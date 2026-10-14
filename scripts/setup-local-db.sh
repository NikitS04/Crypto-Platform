#!/bin/sh
# One-time setup for running the backend against a locally installed PostgreSQL (not needed with Docker).
# Creates the database user from DB_USERNAME / DB_PASSWORD in .env and a "trading" database it owns.
# Connects as a PostgreSQL superuser (default "postgres") and asks for that user's password.
#
#   ./scripts/setup-local-db.sh
#   PSQL=/Library/PostgreSQL/16/bin/psql ./scripts/setup-local-db.sh   # if psql isn't on your PATH
set -e
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Create .env first: cp .env.example .env"; exit 1; }
set -a; . ./.env; set +a

PSQL=${PSQL:-psql}
"$PSQL" -h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" -U "${PG_SUPERUSER:-postgres}" -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USERNAME}') THEN
    CREATE ROLE "${DB_USERNAME}" LOGIN PASSWORD '${DB_PASSWORD}';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE trading OWNER "${DB_USERNAME}"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'trading')\gexec
SQL
echo "Database 'trading' is ready for user '${DB_USERNAME}'."
