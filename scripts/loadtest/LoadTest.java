import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free load generator for the trading API (Java 21, run with `java LoadTest.java`).
 *
 *   java LoadTest.java setup <baseUrl> <users> <tokenFile>   sign up users and write their JWTs
 *   java LoadTest.java run   <baseUrl> <tokenFile> <seconds> [filter]  run scenarios (optionally only those whose
 *                                                                     name contains filter) and print a report
 *
 * Each scenario is closed-loop: N workers (virtual threads) send a request, wait for the response,
 * and immediately send the next one, for a fixed duration. Latency is measured per request.
 */
public class LoadTest {

    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    static String base;
    static String only = "";

    /** Every successful order response, warm-ups included, to reconcile against the orders table afterwards. */
    static final AtomicLong ORDERS_OK = new AtomicLong();

    public static void main(String[] args) throws Exception {
        base = args[1];
        switch (args[0]) {
            case "setup" -> setup(Integer.parseInt(args[2]), Path.of(args[3]));
            case "run" -> {
                only = args.length > 4 ? args[4].toLowerCase() : "";
                run(Files.readAllLines(Path.of(args[2])), Integer.parseInt(args[3]));
            }
            default -> throw new IllegalArgumentException("usage: setup|run");
        }
    }

    // ---------- setup ----------

    static void setup(int users, Path tokenFile) throws Exception {
        String runId = Long.toString(System.currentTimeMillis(), 36);
        Map<Integer, String> tokens = new ConcurrentHashMap<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < users; i++) {
                int n = i;
                pool.submit(() -> {
                    String body = "{\"email\":\"load-" + runId + "-" + n + "@load.test\",\"password\":\"load-test-pw\","
                            + "\"fullName\":\"Load User" + n + "\"}";
                    HttpResponse<String> res = send(post("/auth/signup", body, null));
                    Matcher m = Pattern.compile("\"jwt\"\\s*:\\s*\"([^\"]+)\"").matcher(res.body());
                    if (!m.find()) throw new IllegalStateException("signup failed: " + res.statusCode() + " " + res.body());
                    String jwt = "Bearer " + m.group(1);
                    send(get("/api/wallet", jwt)); // wallets are created on first access
                    tokens.put(n, jwt);
                    return null;
                });
            }
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < users; i++) lines.add(tokens.get(i));
        Files.write(tokenFile, lines);
        System.out.println("Created " + users + " users");
    }

    // ---------- scenarios ----------

    static void run(List<String> tokens, int seconds) throws Exception {
        String hot = tokens.get(0);
        List<String> traders = tokens.subList(1, tokens.size());

        if (only.isEmpty() || "cache".contains(only)) {
            cacheColdVsWarm(List.of("ethereum", "solana", "cardano"));
        }

        report(scenario("Market data reads (Redis cache + live price hash)", 64, seconds,
                (w, n) -> n % 2 == 0 ? get("/coins/live", null) : get("/coins/top50", null)));

        report(scenario("Trading: independent users buying and selling", traders.size(), seconds,
                (w, n) -> order(traders.get(w % traders.size()), n)));

        report(scenario("Trading: 32 workers on ONE wallet (row-lock contention)", 32, seconds,
                (w, n) -> order(hot, n)));

        report(scenario("Portfolio analytics + leaderboard (Redis sorted set)", 64, seconds,
                (w, n) -> n % 2 == 0 ? get("/api/portfolio/analytics", tokens.get(w % tokens.size()))
                                     : get("/api/leaderboard?limit=10", tokens.get(w % tokens.size()))));

        System.out.printf("%nORDERS_OK=%d%n", ORDERS_OK.get());
    }

    /** Each worker alternates buy/sell of a tiny amount so balances and holdings never run out. */
    static HttpRequest order(String jwt, int iteration) {
        String type = iteration % 2 == 0 ? "BUY" : "SELL";
        return post("/api/orders/pay", "{\"coinId\":\"bitcoin\",\"quantity\":0.0001,\"orderType\":\"" + type + "\"}", jwt);
    }

    static void cacheColdVsWarm(List<String> coins) throws Exception {
        System.out.println("\n== Market data cache: first request (miss, calls CoinGecko) vs cached (hit, Redis)");
        for (String coin : coins) {
            long t0 = System.nanoTime();
            HttpResponse<String> cold = send(get("/coins/details/" + coin, null));
            double coldMs = (System.nanoTime() - t0) / 1e6;
            long[] warm = new long[200];
            for (int i = 0; i < warm.length; i++) {
                long s = System.nanoTime();
                send(get("/coins/details/" + coin, null));
                warm[i] = System.nanoTime() - s;
            }
            Arrays.sort(warm);
            System.out.printf("   %-9s miss %7.1f ms (HTTP %d)   hit p50 %5.2f ms  p99 %5.2f ms%n",
                    coin, coldMs, cold.statusCode(), warm[warm.length / 2] / 1e6, warm[warm.length * 99 / 100] / 1e6);
        }
    }

    // ---------- engine ----------

    record Result(String name, int workers, double seconds, long[] latencies, Map<Integer, AtomicLong> statuses, long failures) {
    }

    /** request.apply(worker, iteration) builds a worker's next request, so each worker can keep its own user. */
    static Result scenario(String name, int workers, int seconds, BiFunction<Integer, Integer, HttpRequest> request) throws Exception {
        if (!name.toLowerCase().contains(only)) return null;
        // Short warm-up so JIT compilation and connection setup don't skew the numbers
        long warmEnd = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        drive(workers, warmEnd, request, null, new AtomicLong());

        Map<Integer, AtomicLong> statuses = new ConcurrentHashMap<>();
        AtomicLong failures = new AtomicLong();
        long start = System.nanoTime();
        long end = start + Duration.ofSeconds(seconds).toNanos();
        List<List<Long>> samples = drive(workers, end, request, statuses, failures);
        double elapsed = (System.nanoTime() - start) / 1e9;

        long[] all = samples.stream().flatMap(List::stream).mapToLong(Long::longValue).toArray();
        Arrays.sort(all);
        return new Result(name, workers, elapsed, all, statuses, failures.get());
    }

    static List<List<Long>> drive(int workers, long endNanos, BiFunction<Integer, Integer, HttpRequest> request,
                                  Map<Integer, AtomicLong> statuses, AtomicLong failures) throws Exception {
        List<List<Long>> samples = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int w = 0; w < workers; w++) {
                List<Long> mine = new ArrayList<>();
                samples.add(mine);
                int worker = w;
                pool.submit(() -> {
                    int n = 0;
                    while (System.nanoTime() < endNanos) {
                        HttpRequest req = request.apply(worker, n);
                        long s = System.nanoTime();
                        try {
                            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                            mine.add(System.nanoTime() - s);
                            if (res.statusCode() / 100 == 2 && req.uri().getPath().equals("/api/orders/pay")) ORDERS_OK.incrementAndGet();
                            if (statuses != null) statuses.computeIfAbsent(res.statusCode(), k -> new AtomicLong()).incrementAndGet();
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                        n++;
                    }
                    return null;
                });
            }
        }
        return samples;
    }

    static void report(Result r) {
        if (r == null) return;
        long ok = r.statuses().entrySet().stream().filter(e -> e.getKey() / 100 == 2).mapToLong(e -> e.getValue().get()).sum();
        long total = r.latencies().length + r.failures();
        long[] l = r.latencies();
        System.out.printf("%n== %s%n", r.name());
        System.out.printf("   workers %d, %.0fs, %,d requests -> %,.0f req/s (%,.0f successful/s)%n",
                r.workers(), r.seconds(), total, total / r.seconds(), ok / r.seconds());
        if (l.length > 0) {
            System.out.printf("   latency p50 %.1f ms   p95 %.1f ms   p99 %.1f ms   max %.1f ms%n",
                    pct(l, 50), pct(l, 95), pct(l, 99), l[l.length - 1] / 1e6);
        }
        System.out.printf("   status codes %s, connection errors %d%n", r.statuses(), r.failures());
    }

    static double pct(long[] sorted, int p) {
        int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(i, sorted.length - 1))] / 1e6;
    }

    // ---------- http ----------

    static HttpRequest get(String path, String jwt) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).GET();
        if (jwt != null) b.header("Authorization", jwt);
        return b.build();
    }

    static HttpRequest post(String path, String json, String jwt) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (jwt != null) b.header("Authorization", jwt);
        return b.build();
    }

    static HttpResponse<String> send(HttpRequest req) throws Exception {
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
