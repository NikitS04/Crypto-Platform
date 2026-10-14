package com.nikit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.live.LivePrice;
import com.nikit.live.LivePriceStore;
import com.nikit.modal.Coin;
import com.nikit.modal.Wallet;
import com.nikit.repository.CoinRepository;
import com.nikit.repository.PortfolioSnapshotRepository;
import com.nikit.repository.UserRepository;
import com.nikit.repository.WalletRepository;
import com.nikit.service.CoinService;
import com.nikit.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Live prices in orders, portfolio analytics and the leaderboard, end to end through the API. */
@SpringBootTest
@AutoConfigureMockMvc
class FeaturesIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CoinRepository coinRepository;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private WalletService walletService;
    @Autowired private LivePriceStore livePriceStore;

    @MockBean private CoinService coinService;

    private String coinId;
    private String symbol;

    @BeforeEach
    void setUp() throws Exception {
        // A unique coin per test so live prices set in one test can't leak into another
        symbol = "t" + UUID.randomUUID().toString().substring(0, 6);
        coinId = "coin-" + symbol;
        Coin coin = new Coin();
        coin.setId(coinId);
        coin.setSymbol(symbol);
        coin.setName("Test Coin");
        coin.setCurrentPrice(100.0);
        coinRepository.save(coin);
        when(coinService.findById(coinId)).thenAnswer(i -> coinRepository.findById(coinId).orElseThrow());
        when(coinService.getCoinDetails(anyString())).thenReturn("{}");
    }

    // ---------- helpers ----------

    private String signup(String email, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", name));
        String res = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(res).get("jwt").asText();
    }

    private void fund(String email, long amount) {
        Wallet wallet = walletService.getUserWallet(userRepository.findByEmail(email));
        wallet.setBalance(BigDecimal.valueOf(amount));
        walletRepository.save(wallet);
    }

    private void trade(String jwt, String type, double quantity) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("coinId", coinId, "quantity", quantity, "orderType", type));
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void setLivePrice(double price) {
        livePriceStore.putAll(List.of(new LivePrice(symbol, price, 0, System.currentTimeMillis())));
    }

    private JsonNode getJson(String url, String jwt) throws Exception {
        return objectMapper.readTree(mvc.perform(get(url).header("Authorization", jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("condition within 5s").isTrue();
    }

    // ---------- live prices ----------

    @Test
    void marketOrdersFillAtTheFreshLivePrice() throws Exception {
        String email = "live-" + UUID.randomUUID() + "@test.com";
        String jwt = signup(email, "Live Trader");
        fund(email, 1000);
        setLivePrice(200.0);

        trade(jwt, "BUY", 1);

        JsonNode orders = getJson("/api/orders", jwt);
        assertThat(orders.get(0).get("price").asDouble()).isEqualTo(200.0);
    }

    @Test
    void staleLivePricesAreIgnored() throws Exception {
        String email = "stale-" + UUID.randomUUID() + "@test.com";
        String jwt = signup(email, "Stale Trader");
        fund(email, 1000);
        livePriceStore.putAll(List.of(new LivePrice(symbol, 200.0, 0, System.currentTimeMillis() - 60_000)));

        trade(jwt, "BUY", 1);

        assertThat(getJson("/api/orders", jwt).get(0).get("price").asDouble()).isEqualTo(100.0);
    }

    @Test
    void livePricesEndpointServesTheLatestPrices() throws Exception {
        setLivePrice(123.45);
        JsonNode live = objectMapper.readTree(mvc.perform(get("/coins/live")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(live.get(symbol).get("price").asDouble()).isEqualTo(123.45);
    }

    // ---------- analytics ----------

    @Test
    void analyticsReportsUnrealisedAndRealisedPnlAndReturns() throws Exception {
        String email = "pnl-" + UUID.randomUUID() + "@test.com";
        String jwt = signup(email, "Pnl Trader");
        fund(email, 1000);

        trade(jwt, "BUY", 2);           // 2 @ 100 = 200 cost, cash 800
        setLivePrice(150.0);            // holdings now worth 300

        JsonNode a = getJson("/api/portfolio/analytics", jwt);
        assertThat(a.get("cash").asDouble()).isEqualTo(800.0);
        assertThat(a.get("holdingsValue").asDouble()).isEqualTo(300.0);
        assertThat(a.get("equity").asDouble()).isEqualTo(1100.0);
        assertThat(a.get("unrealizedPnl").asDouble()).isEqualTo(100.0);
        assertThat(a.get("holdings").get(0).get("unrealizedPnlPct").asDouble()).isCloseTo(50.0, within(1e-9));
        assertThat(a.get("holdings").get(0).get("allocationPct").asDouble()).isCloseTo(300.0 / 1100 * 100, within(1e-9));

        // Sell one at 150: realised (150 - 100) x 1 = 50
        trade(jwt, "SELL", 1);
        a = getJson("/api/portfolio/analytics", jwt);
        assertThat(a.get("realizedPnl").asDouble()).isEqualTo(50.0);
        assertThat(a.get("unrealizedPnl").asDouble()).isEqualTo(50.0);
        assertThat(a.get("equityCurve").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void tradesProduceSnapshotsAfterCommit() throws Exception {
        String email = "snap-" + UUID.randomUUID() + "@test.com";
        String jwt = signup(email, "Snap Trader");
        fund(email, 1000);
        Long userId = userRepository.findByEmail(email).getId();

        trade(jwt, "BUY", 1);
        await(() -> !snapshotRepository.findByUserIdOrderByTimestampAsc(userId).isEmpty());

        // A rejected trade rolls back and must not produce another snapshot
        int before = snapshotRepository.findByUserIdOrderByTimestampAsc(userId).size();
        String tooBig = objectMapper.writeValueAsString(Map.of("coinId", coinId, "quantity", 1000, "orderType", "BUY"));
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt).contentType(MediaType.APPLICATION_JSON).content(tooBig))
                .andExpect(status().isBadRequest());
        Thread.sleep(300);
        assertThat(snapshotRepository.findByUserIdOrderByTimestampAsc(userId)).hasSize(before);
    }

    // ---------- leaderboard ----------

    @Test
    void leaderboardRanksTradersByReturn() throws Exception {
        String winnerEmail = "win-" + UUID.randomUUID() + "@test.com";
        String loserEmail = "lose-" + UUID.randomUUID() + "@test.com";
        String winnerJwt = signup(winnerEmail, "Ada Lovelace");
        String loserJwt = signup(loserEmail, "Grace Hopper");
        fund(winnerEmail, 1000);
        fund(loserEmail, 1000);
        Long winnerId = userRepository.findByEmail(winnerEmail).getId();
        Long loserId = userRepository.findByEmail(loserEmail).getId();

        // Both buy 5 @ 100 (half their cash)
        trade(winnerJwt, "BUY", 5);
        trade(loserJwt, "BUY", 5);
        await(() -> snapshotRepository.findByUserIdOrderByTimestampAsc(winnerId).size() >= 1
                && snapshotRepository.findByUserIdOrderByTimestampAsc(loserId).size() >= 1);

        // Price doubles; the winner sells at the top, the loser holds and it falls back below cost
        setLivePrice(200.0);
        trade(winnerJwt, "SELL", 5);   // winner: 500 + 1000 = 1500 (+50%)
        setLivePrice(50.0);
        trade(loserJwt, "BUY", 1);     // loser: 450 cash + 6 x 50 = 750 (-25%)
        await(() -> snapshotRepository.findByUserIdOrderByTimestampAsc(loserId).size() >= 2);

        JsonNode board = getJson("/api/leaderboard?limit=100", winnerJwt);
        JsonNode entries = board.get("entries");
        int winnerPos = -1;
        int loserPos = -1;
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i).get("name").asText();
            if (name.equals("Ada L.")) winnerPos = i;
            if (name.equals("Grace H.")) loserPos = i;
        }
        assertThat(winnerPos).isGreaterThanOrEqualTo(0);
        assertThat(loserPos).isGreaterThan(winnerPos);
        assertThat(entries.get(winnerPos).get("returnPct").asDouble()).isCloseTo(50.0, within(0.01));
        assertThat(entries.get(winnerPos).get("you").asBoolean()).isTrue();
        assertThat(entries.get(loserPos).get("returnPct").asDouble()).isCloseTo(-25.0, within(0.01));
        assertThat(board.get("yourRank").asLong()).isEqualTo(winnerPos + 1);
    }
}
