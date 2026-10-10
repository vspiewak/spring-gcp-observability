locals {
  # Google's build of the OpenTelemetry Collector, the sidecar beside each service in run.tf
  collector_image = "us-docker.pkg.dev/cloud-ops-agents-artifacts/google-cloud-opentelemetry-collector/otelcol-google:0.160.0"
}

# collector.yaml reaches the sidecar as a file, and Cloud Run mounts files from Secret Manager —
# nothing in it is secret
resource "google_secret_manager_secret" "collector_config" {
  secret_id = "otel-collector-config"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "collector_config" {
  secret      = google_secret_manager_secret.collector_config.id
  secret_data = file("${path.module}/collector.yaml")

  # run.tf mounts this exact version : the new one must exist before the services move to it
  lifecycle {
    create_before_destroy = true
  }
}

resource "google_secret_manager_secret_iam_member" "collector_config" {
  for_each = local.services

  secret_id = google_secret_manager_secret.collector_config.id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.service[each.key].member
}
