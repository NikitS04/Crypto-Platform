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
- **Cache**: Redis (Spring Cache)
- **ORM**: JPA (Java Persistence API)
- **Authentication**: JWT (JSON Web Token)

### Frontend
- **Framework**: React
- **Styling**: Tailwind CSS

---

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
Start PostgreSQL (with a `trading` database) and Redis, then:
```bash
cp .env.example .env
set -a; source .env; set +a
./mvnw spring-boot:run
```

### Running Tests
The tests run against an in-memory H2 database (using the PostgreSQL dialect) with an in-memory cache and stubbed market data, so no PostgreSQL, Redis, or network access is needed:
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

