resource "google_cloud_run_v2_service" "orders_api" {
  name     = "orders-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  # a demo : anyone may call it, and terraform destroy may delete it
  invoker_iam_disabled = true
  deletion_protection  = false

  template {
    service_account = google_service_account.orders_api.email

    scaling {
      max_instance_count = 1
    }

    containers {
      image = var.image

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        # spans leave in batches, after the response : with the CPU throttled between requests,
        # the export call fails ("Failed to export spans") and the batch is lost — measured
        cpu_idle          = false
        startup_cpu_boost = true
      }

      # the only Google Cloud knowledge the service gets : which project it belongs to
      env {
        name  = "SPRING_CLOUD_GCP_PROJECT_ID"
        value = var.project_id
      }
      # one JSON object per line on stdout — the observability starter names its fields for Google
      env {
        name  = "LOGGING_STRUCTURED_FORMAT_CONSOLE"
        value = "logstash"
      }
      env {
        name = "SPRING_MONGODB_URI"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.mongodb_uri.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_version.mongodb_uri,
    google_secret_manager_secret_iam_member.orders_api,
    google_project_iam_member.orders_api_traces,
  ]
}
