#!/usr/bin/env bash
#
# One request to orders-api, then where to find it : its log lines in Cloud Logging, its trace in
# Cloud Trace. The script picks the trace id itself, through the W3C traceparent header.

set -euo pipefail
cd "$(dirname "$0")/.."

url=$(terraform -chdir=terraform output -raw service_url)
project=$(terraform -chdir=terraform output -raw project_id)

order_id="demo-$RANDOM"
curl -fsS -X POST "$url/orders/v1/orders" \
  -H 'Content-Type: application/json' -d "{\"orderId\": \"$order_id\", \"amount\": 42}" >/dev/null

# the request : a trace id of our own, so we know what to look for
trace_id=$(openssl rand -hex 16)
echo "GET $url/orders/v1/orders/$order_id"
curl -fsS "$url/orders/v1/orders/$order_id" -H "traceparent: 00-$trace_id-$(openssl rand -hex 8)-01"
echo
echo
echo "🔍 trace $trace_id — waiting for its logs to land in Cloud Logging..."

filter="trace=\"projects/$project/traces/$trace_id\""
for _ in $(seq 1 20); do
  if [ -n "$(gcloud logging read "$filter" --project "$project" --freshness 10m --limit 1 --format 'value(insertId)')" ]; then
    break
  fi
  sleep 3
done

gcloud logging read "$filter" --project "$project" --freshness 10m --order asc \
  --format 'table(timestamp.date("%H:%M:%S"), severity, jsonPayload.message, httpRequest.requestUrl, spanId)'

echo
echo "Logs  : https://console.cloud.google.com/logs/query;query=$(printf %s "$filter" | jq -sRr @uri)?project=$project"
echo "Trace : https://console.cloud.google.com/traces/list?project=$project&tid=$trace_id"
