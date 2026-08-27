#!/usr/bin/env bash
set -euo pipefail

root="${1:-/home/ubuntu/VivuPlan}"

cd "$root"

redact_value() {
  local key="$1"
  local value="$2"
  if [[ "$key" =~ (PASSWORD|SECRET|KEY|TOKEN|CLIENT_SECRET|API_KEY|JWT_SECRET|MAIL_PASSWORD|DB_PASSWORD) ]]; then
    printf '<redacted>'
  else
    printf '%s' "$value"
  fi
}

echo "--- env ---"
while IFS= read -r line; do
  line="${line%$'\r'}"
  [[ "$line" =~ ^[A-Z0-9_]+= ]] || continue
  key="${line%%=*}"
  value="${line#*=}"
  printf '%s=' "$key"
  redact_value "$key" "$value"
  printf '\n'
done < .env | sort

echo "--- compose ---"
sed -E 's/((PASSWORD|SECRET|KEY|TOKEN|CLIENT_SECRET|API_KEY|JWT_SECRET|MAIL_PASSWORD|DB_PASSWORD)[A-Z0-9_]*[[:space:]]*[:=][[:space:]]*).*/\1<redacted>/g' docker-compose.yml
