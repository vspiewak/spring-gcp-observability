#!/usr/bin/env bash
#
# Provision, build and deploy orders-api — the one entry point, first time and every time after.
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

# a brand-new project can reject the very first Cloud Run deployment with an "internal error" while
# the APIs and IAM bindings created seconds before propagate : one retry gets past it
apply() {
  tf apply "$@" && return
  echo "↻ retrying once : Cloud Run sometimes refuses the first deployment into a fresh project"
  tf apply "$@"
}

tf init -input=false >/dev/null

# 1. the infrastructure : APIs, registry, identity, Atlas, the secret — and Cloud Run, on the image
#    it already runs (the public placeholder, the very first time)
current_image=$(tf output -raw image 2>/dev/null || true)
apply ${current_image:+-var "image=$current_image"}

# 2. the image : built and pushed by Jib, no Docker daemon needed
repository=$(tf output -raw image_repository)
./mvnw -q -ntp -pl orders-api -am package jib:build -DskipTests \
  -Djib.to.image="$repository:$(git rev-parse --short HEAD 2>/dev/null || echo latest)" \
  -Djib.to.auth.username=oauth2accesstoken \
  -Djib.to.auth.password="$(gcloud auth print-access-token)"

# 3. Cloud Run, switched to that exact image
apply -auto-approve -var "image=$repository@$(cat orders-api/target/jib-image.digest)"

echo
echo "🚀 orders-api is up : $(tf output -raw service_url)"
echo "   next : scripts/demo.sh"
