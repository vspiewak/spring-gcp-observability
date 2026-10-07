#!/usr/bin/env bash
#
# One request to orders-api — which calls pricing-api — then where to find it : the log lines of both
# services in Cloud Logging, one trace in Cloud Trace. The script picks the trace id itself, through
# the W3C traceparent header.

set -euo pipefail
cd "$(dirname "$0")/.."

url=$(terraform -chdir=terraform output -raw service_url)
project=$(terraform -chdir=terraform output -raw project_id)

order_id="demo-$(openssl rand -hex 4)"
curl -fsS -X POST "$url/orders/v1/orders" \
  -H 'Content-Type: application/json' -d "{\"orderId\": \"$order_id\", \"amount\": 42}" >/dev/null

# the request : a trace id of our own, so we know what to look for
trace_id=$(openssl rand -hex 16)
echo "GET $url/orders/v1/orders/$order_id"
curl -fsS "$url/orders/v1/orders/$order_id" -H "traceparent: 00-$trace_id-$(openssl rand -hex 8)-01"
echo
echo
echo "🔍 trace $trace_id — waiting for its logs to land in Cloud Logging..."

# four entries : each service's Cloud Run request log, and each service's own line — ingested apart
filter="trace=\"projects/$project/traces/$trace_id\""
for _ in $(seq 1 20); do
  entries=$(gcloud logging read "$filter" --project "$project" --freshness 10m --limit 10 --format 'value(insertId)' | wc -l)
  if [ "$entries" -ge 4 ]; then
    break
  fi
  sleep 3
done

gcloud logging read "$filter" --project "$project" --freshness 10m --order asc \
  --format 'table(timestamp.date("%H:%M:%S"), resource.labels.service_name, severity, jsonPayload.message, httpRequest.requestUrl, spanId)'

echo
echo "Logs  : https://console.cloud.google.com/logs/query;query=$(printf %s "$filter" | jq -sRr @uri)?project=$project"
echo "Trace : https://console.cloud.google.com/traces/explorer;traceId=$trace_id;duration=P1D?project=$project"
