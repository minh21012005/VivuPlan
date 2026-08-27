#!/usr/bin/env bash
set -euo pipefail

envfile="${1:-/home/ubuntu/VivuPlan/.env}"
backup_dir="${2:-/home/ubuntu}"

if [[ ! -f "$envfile" ]]; then
  echo "Missing env file: $envfile" >&2
  exit 1
fi

read_env_value() {
  local key="$1"
  local line value
  line="$(grep -m 1 -E "^${key}=" "$envfile" || true)"
  if [[ -z "$line" ]]; then
    return 1
  fi
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

DB_URL="$(read_env_value DB_URL)" || {
  echo "DB_URL is required" >&2
  exit 1
}
DB_USERNAME="$(read_env_value DB_USERNAME)" || {
  echo "DB_USERNAME is required" >&2
  exit 1
}
DB_PASSWORD="$(read_env_value DB_PASSWORD)" || {
  echo "DB_PASSWORD is required" >&2
  exit 1
}

url="${DB_URL#jdbc:postgresql://}"
hostport="${url%%/*}"
dbpart="${url#*/}"
dbname="${dbpart%%\?*}"
host="${hostport%%:*}"
port="${hostport#*:}"

if [[ "$port" == "$hostport" ]]; then
  port="5432"
fi

timestamp="$(date -u +%Y%m%d_%H%M%S)"
output="${backup_dir%/}/vivuplan-rds-${timestamp}.dump"

export PGPASSWORD="$DB_PASSWORD"
pg_dump \
  --host="$host" \
  --port="$port" \
  --username="$DB_USERNAME" \
  --dbname="$dbname" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="$output"
unset PGPASSWORD

sha256sum "$output"
ls -lh "$output"
