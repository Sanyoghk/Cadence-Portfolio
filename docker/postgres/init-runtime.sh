#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=runtime_password="$CADENCE_RUNTIME_DB_PASSWORD" --set=ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE cadence_runtime LOGIN PASSWORD :'runtime_password';
GRANT CONNECT ON DATABASE cadence TO cadence_runtime;
GRANT USAGE ON SCHEMA public TO cadence_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE cadence_migration IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO cadence_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE cadence_migration IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO cadence_runtime;
SQL
