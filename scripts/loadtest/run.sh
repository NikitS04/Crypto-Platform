#!/usr/bin/env bash
# Load test against an isolated backend instance (its own Postgres DB and Redis DB index).
#
# Start the backend first, e.g.:
#   ./mvnw -DskipTests package
#   set -a; . ./.env; set +a
#   java -jar target/Trading-0.0.1-SNAPSHOT.jar --server.port=5455 \
#     --spring.datasource.url=jdbc:postgresql://localhost:5433/trading_load \
#     --spring.data.redis.database=1 --app.rate-limit.enabled=false
#
# Rate limiting is turned off for the run: every request comes from one IP, so it would (correctly) block them.
#
# Usage: scripts/loadtest/run.sh [users=101] [seconds=30] [scenario filter, e.g. "trading"]
set -euo pipefail

BASE=${BASE:-http://localhost:5455}
USERS=${1:-101}
SECONDS_PER_SCENARIO=${2:-30}
PSQL=${PSQL:-psql}
DB_ARGS=(-h "${DB_HOST:-localhost}" -p "${DB_PORT:-5433}" -U "${DB_USERNAME:-trading}" -d "${LOAD_DB:-trading_load}")
REDIS_DB=${REDIS_DB:-1}
DIR=$(cd "$(dirname "$0")" && pwd)
TOKENS=$(mktemp)
trap 'rm -f "$TOKENS"' EXIT

sql() { PGPASSWORD="${DB_PASSWORD:-}" "$PSQL" "${DB_ARGS[@]}" -tAc "$1"; }

echo "Target $BASE, $USERS users, ${SECONDS_PER_SCENARIO}s per scenario"
redis-cli -n "$REDIS_DB" del trading:leaderboard > /dev/null

java "$DIR/LoadTest.java" setup "$BASE" "$USERS" "$TOKENS"

# Give every test user plenty of cash so orders never fail for lack of funds
sql "update wallet set balance = 1000000 where user_id in (select id from users where email like '%@load.test')" > /dev/null

# Orders reference the coin row, which is saved the first time its details are fetched
curl -sf "$BASE/coins/details/bitcoin" > /dev/null

ORDERS_BEFORE=$(sql "select count(*) from orders")
OUT=$(mktemp)
java "$DIR/LoadTest.java" run "$BASE" "$TOKENS" "$SECONDS_PER_SCENARIO" "${3:-}" | tee "$OUT"
ORDERS_OK=$(sed -n 's/^ORDERS_OK=//p' "$OUT")
rm -f "$OUT"

echo
echo "== Consistency checks after the run"
echo "   successful order responses: $ORDERS_OK"
echo "   orders written to Postgres: $(( $(sql "select count(*) from orders") - ORDERS_BEFORE ))"
echo "   wallets with negative cash: $(sql "select count(*) from wallet where balance < 0")"
echo "   assets with negative qty:   $(sql "select count(*) from asset where quantity < -1e-9")"
