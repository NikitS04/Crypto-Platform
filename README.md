# Trading-Platform
A feature-rich trading platform designed to handle real-time cryptocurrency transactions and provide users with an intuitive interface to track, analyze, and manage their investments. This project integrates a secure backend with a dynamic frontend to deliver seamless trading experiences.

---

## Features

### Backend
- **Market Data Caching**: Redis cache-aside layer in front of the CoinGecko API with per-symbol keys and per-endpoint TTLs, so repeated reads never touch the rate-limited upstream API.
- **Secure RESTful APIs**:
  - Buy and sell cryptocurrency.
  - Wallet-to-wallet transfers.
  - Deposit and withdraw funds.
- **Authentication**: JWT-based authentication ensures secure access control and data protection.

### Frontend
- **Market Data Tracking**: Browse, search, and chart thousands of cryptocurrencies.
- **Personalized Watchlists**: Users can create and manage custom watchlists for their favorite coins.
- **Interactive Visualizations**: Responsive charts and graphs enable better decision-making.
- **Modern UI**: Built with React and Tailwind CSS for a clean, user-friendly experience.

---

## Tech Stack

### Backend
- **Framework**: Spring Boot
- **Database**: PostgreSQL
- **Cache / real-time state**: Redis (Spring Cache, hashes, sorted sets, Lua scripts)
- **Real-time**: WebSockets (Binance feed in, browser push out)
- **ORM**: JPA (Java Persistence API)
- **Authentication**: JWT (JSON Web Token)

### Frontend
- **Framework**: React
- **Styling**: Tailwind CSS

---

## Live Prices over WebSockets

Prices stream in real time from **Binance's free public market data feed** (no account or API key needed):

- The backend holds **one** upstream WebSocket to Binance's all-market ticker stream and fans updates out to
  every browser over its own WebSocket (`/ws/prices`), so the number of users never affects the upstream connection.
- New clients get a snapshot of every price, then only the coins that changed (~1 update/second).
- Latest prices live in a **Redis hash**, so any backend instance (and the `/coins/live` REST endpoint) serves the same
  data; the hash expires if the feed stops, so stale prices never look live.
- Market orders fill at the live price when it is under 15s old, falling back to the cached CoinGecko price.
- The upstream connection reconnects with exponential backoff; slow browser clients are dropped rather than
  blocking everyone else (`ConcurrentWebSocketSessionDecorator`).
- In the UI, prices tick in place and flash green/red on each move.

> binance.com is unavailable to US users; set `LIVE_PRICES_URL=wss://stream.binance.us:9443/ws/!miniTicker@arr` there.

## Portfolio Analytics

The Analytics page shows equity, unrealised and realised P&L, an equity curve, allocation and per-holding P&L.

- **Time-weighted return (TWR)**: each period's return strips out deposits, withdrawals and transfers, so adding
  money never shows up as performance (`PerformanceMetrics.twrIndex`).
- **Max drawdown** from the TWR index, plus **annualised volatility and Sharpe ratio** from daily returns
  (365-day annualisation since crypto never closes; shown once there are 5+ days of history).
- Equity is snapshotted after every committed trade or money movement (an `AFTER_COMMIT` event listener, so a
  rolled-back trade never produces a snapshot) and every 15 minutes so the curve follows the market. Snapshots run on
  a background pool: taking a second DB connection on the request thread exhausted the pool under concurrent load,
  which the concurrency tests caught.

## Leaderboard

Traders are ranked by time-weighted return in a **Redis sorted set**: updating a score, reading the top N and finding
a user's rank are all O(log n). Names are shown as "First L." only.

## Rate Limiting

A **token bucket** per client in Redis, updated by a **Lua script** so each read-refill-take-write is atomic: 200
concurrent requests against a 20-token bucket let exactly 20 through (`RedisBackedFeaturesTest`). The script uses
Redis's clock so all instances agree, and idle buckets expire on their own.

| Requests | Keyed by | Burst | Sustained |
|----------|----------|-------|-----------|
| `/auth/**` (sign in, sign up, reset) | IP | 10 | 1 per 5s, slows password guessing |
| Everything else | signed-in user, else IP | 60 | 2 per second |

Over the limit returns `429` with `Retry-After`. If Redis is down the limiter **fails open**: a limiter outage
degrades protection instead of taking the API down.

So Redis now plays four roles: a **cache** protecting the outbound CoinGecko dependency, a **rate limiter**
protecting the inbound API, the **shared store** for live prices, and the **ranking engine** for the leaderboard.

## Market Data Caching

CoinGecko's free tier allows only a few dozen requests per minute, so every market data call goes through
`MarketDataClient`, a cache-aside layer backed by Redis:

- **Per-symbol keys** such as `trading:coinDetails::bitcoin` and `trading:marketChart::bitcoin:7`
- **Per-endpoint TTLs** matched to how fast the data changes: prices 60s, charts 5 min, trending 10 min, search 1 h
- **Stampede protection**: concurrent misses on the same key wait for a single upstream call (`sync = true`)
- **Failures are never cached**: a rate-limited call returns `503` and is retried on the next request

Measured locally against the live API: a cold `/coins/top50` request took ~1.1 s, while 300 cached requests
averaged **6.3 ms** (p50 5.1 ms, p99 30 ms). Hit/miss counts are exposed as the `cache.gets` metric on `/actuator/metrics`.

---

## Concurrency: Preventing Double-Spends

Balance checks used to be a plain read-check-write, so simultaneous requests could all read the same balance and
all pass the "enough funds?" check. `ConcurrencyTest` reproduces this by firing up to 200 requests at once:
before the fix, **100 of 100** $10 withdrawals succeeded against a $300 balance and 40 sells of 0.1 BTC succeeded
against a 1 BTC holding.

The fix:
- Every balance-changing operation takes a **row lock on the wallet** (`SELECT ... FOR UPDATE`) and does its
  check and update inside the same transaction, so requests for the same wallet run one after another.
  Trades lock the wallet first, which also serialises updates to that user's holdings.
- Transfers lock both wallets in **ascending id order**, so opposite-direction transfers can't deadlock.
- After acquiring a lock the row is **re-read**, so a copy cached earlier in the transaction can't be acted on.
- Open-session-in-view is disabled, so each transaction works on its own fresh persistence context.

`ConcurrencyTest` releases every request at once (one thread per request, all held at a start gate): exactly 30 of
100 simultaneous $10 withdrawals succeed against $300, exactly 50 of 100 $10 buys against $500, and 200
simultaneous two-way transfers conserve the total balance. The same tests pass against PostgreSQL.

---

## Load Testing

`scripts/loadtest/` contains a dependency-free load generator (plain Java 21, virtual threads) and a runner that
reconciles the results against Postgres afterwards. It runs against a separate backend instance with its own
database and Redis index, with rate limiting disabled since every request comes from one IP.

```bash
createdb -O trading trading_load
./mvnw -DskipTests package
set -a; . ./.env; set +a
java -jar target/Trading-0.0.1-SNAPSHOT.jar --server.port=5455 \
  --spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:-5432}/trading_load \
  --spring.data.redis.database=1 --app.rate-limit.enabled=false
scripts/loadtest/run.sh 101 30      # users, seconds per scenario
```

Results on an Apple M1 laptop (8 cores, 8 GB), with the load generator, backend, PostgreSQL 16 and Redis all on
the same machine (CPU-bound at 100%), 30 s per scenario after a 3 s warm-up:

| Scenario | Workers | Throughput | p50 | p95 | p99 | Errors |
|---|---|---|---|---|---|---|
| Market data reads (Redis cache + live price hash) | 64 | 2,858 req/s | 22 ms | 35 ms | 44 ms | 0 |
| Market orders, 100 independent users | 100 | 555 orders/s | 167 ms | 293 ms | 367 ms | 0 |
| Market orders, 32 workers on one wallet (lock contention) | 32 | 586 orders/s | 52 ms | 85 ms | 109 ms | 0 |
| Portfolio analytics + leaderboard | 64 | 479 req/s | 125 ms | 223 ms | 273 ms | 0 |

- **Cache:** a market data miss (CoinGecko) took 190-290 ms; a Redis hit took ~5 ms at p50.
- **Consistency:** 36,245 successful order responses, exactly 36,245 orders written to Postgres, and no wallet or
  holding went negative.

### What the first run found

The first run sustained 435 orders/s but also logged 16,415 dropped portfolio snapshots. Every trade queued a
snapshot, and each snapshot recomputed the user's return by replaying their entire history to update the
leaderboard, so snapshots got slower with every trade and the queue overflowed. Fixes:

- **Coalescing:** a burst of trades by one user queues one snapshot, which reads the latest state.
- **Incremental return:** each snapshot stores its running time-weighted return index, so updating the
  leaderboard reads one row instead of the whole history.
- **No per-order market data refresh** when a fresh live price exists, and realised P&L summed in SQL instead of
  loading every order (which also loaded each order's item in a separate query).

| | Before | After |
|---|---|---|
| Orders/s (100 users / one wallet) | 435 / 443 | 555 / 586 |
| Analytics + leaderboard | 222 req/s, p99 526 ms, max 5.0 s | 479 req/s, p99 273 ms, max 0.4 s |
| Dropped snapshots | 16,415 | 0 |

## Installation & Setup

### Prerequisites
- Docker (for PostgreSQL and Redis), or local installs of both
- Java 17 or later
- Node.js and npm

### Quick Start with Docker
```bash
git clone https://github.com/NikitS04/Crypto-Platform.git
cd Crypto-Platform
cp .env.example .env        # set JWT_SECRET at minimum
docker compose up --build   # PostgreSQL + Redis + backend on http://localhost:5454
```

### Running the Backend Without Docker
Needs PostgreSQL and Redis running locally (e.g. `brew install postgresql@16 redis`, or the PostgreSQL installer).

```bash
cp .env.example .env              # set JWT_SECRET to a random string of 32+ characters
./scripts/setup-local-db.sh       # one-time: creates the "trading" database and user (asks for your postgres password)
set -a; source .env; set +a
./mvnw spring-boot:run            # backend on http://localhost:5454
```

Setup script options:
- **Homebrew PostgreSQL** has no `postgres` user; its admin is your macOS user:
  `PG_SUPERUSER=$(whoami) ./scripts/setup-local-db.sh`
- **`psql` not on your PATH** (e.g. the EDB installer on macOS):
  `PSQL=/Library/PostgreSQL/16/bin/psql ./scripts/setup-local-db.sh`
- **PostgreSQL on another port** (e.g. two installs side by side): set `DB_PORT` in `.env`.

### Running Tests
Most tests run against an in-memory H2 database (using the PostgreSQL dialect) with in-memory versions of the Redis features and stubbed market data, so no PostgreSQL, Redis or network access is needed. `RedisBackedFeaturesTest` exercises the real Redis implementations (Lua rate limiter, sorted-set leaderboard, price hash) and runs automatically when Redis is available on `localhost:6379`:
```bash
./mvnw test
```

### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd Trading-react
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```

---

## Usage
1. Access the application at `http://localhost:5173`.
2. Sign in or create an account.
3. Use the dashboard to:
   - Track cryptocurrency prices and trends.
   - Manage your personalized watchlist.
   - Buy, sell, deposit, or withdraw funds securely.

---

## Acknowledgments
- Built using **Spring Boot** and **React**.
- Styled with **Tailwind CSS** for a modern and responsive design.
- Data persistence powered by **PostgreSQL**, market data cached in **Redis**.

---

Feel free to reach out with questions, suggestions, or issues through the repository's [Issues](https://github.com/NikitS04/Crypto-Platform/issues) section!

