#!/usr/bin/env bash
#
# Provision, build and deploy orders-api and pricing-api — the one entry point, first time and every
# time after.
#
#   export TF_VAR_project_id=<a fresh Google Cloud project, billing linked>
#   export TF_VAR_atlas_org_id=<your MongoDB Atlas organization>
#   export MONGODB_ATLAS_CLIENT_ID=... MONGODB_ATLAS_CLIENT_SECRET=...   # an Atlas service account
#   gcloud auth application-default login                               # Terraform's Google credentials
#
# A bare `terraform apply` would put Cloud Run back on the placeholder image : use this script.

set -euo pipefail
cd "$(dirname "$0")/.."

: "${TF_VAR_project_id:?export TF_VAR_project_id=<your Google Cloud project>}"
: "${TF_VAR_atlas_org_id:?export TF_VAR_atlas_org_id=<your MongoDB Atlas organization id>}"
: "${MONGODB_ATLAS_CLIENT_ID:?export MONGODB_ATLAS_CLIENT_ID (an Atlas service account)}"
: "${MONGODB_ATLAS_CLIENT_SECRET:?export MONGODB_ATLAS_CLIENT_SECRET}"

tf() { terraform -chdir=terraform "$@"; }

# an output's value, or nothing — `terraform output -raw` prints its "no outputs" warning on stdout
output() { tf output -json | jq -r --arg name "$1" '.[$name].value // empty'; }

apply() {
  tf apply "$@" || {
    echo "✋ terraform apply failed. A brand-new project sometimes refuses its very first Cloud Run"
    echo "   deployment with an \"internal error\" while its APIs and IAM settle : run this script again."
    exit 1
  }
}

tf init -input=false >/dev/null

# 1. the infrastructure : APIs, registry, identities, Atlas, the secret — and both Cloud Run
#    services, on the images they already run (the public placeholder, the very first time)
orders_image=$(output orders_api_image)
pricing_image=$(output pricing_api_image)
apply ${orders_image:+-var "orders_api_image=$orders_image"} \
  ${pricing_image:+-var "pricing_api_image=$pricing_image"}

# 2. the images : built and pushed by Jib, no Docker daemon needed
repository=$(output image_repository)
./mvnw -q -ntp -pl orders-api,pricing-api -am package jib:build -DskipTests \
  -Dimage.repository="$repository" \
  -Dimage.tag="$(git rev-parse --short HEAD 2>/dev/null || echo latest)" \
  -Djib.to.auth.username=oauth2accesstoken \
  -Djib.to.auth.password="$(gcloud auth print-access-token)"

# 3. Cloud Run, switched to those exact images
apply -auto-approve \
  -var "orders_api_image=$repository/orders-api@$(cat orders-api/target/jib-image.digest)" \
  -var "pricing_api_image=$repository/pricing-api@$(cat pricing-api/target/jib-image.digest)"

echo
echo "🚀 orders-api is up : $(output service_url)"
echo "   calling pricing-api : $(output pricing_url)"
echo "   next : scripts/demo.sh"
