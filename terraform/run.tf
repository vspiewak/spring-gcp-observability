resource "google_cloud_run_v2_service" "orders_api" {
  name     = "orders-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  # a demo : anyone may call it, and terraform destroy may delete it
  invoker_iam_disabled = true
  deletion_protection  = false

  template {
    service_account = google_service_account.service["orders-api"].email

    scaling {
      max_instance_count = 1
    }

    containers {
      image = var.orders_api_image

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

      # the only Google Cloud knowledge the service gets : which project it runs in — Spring Cloud
      # GCP's own property ; the observability starter turns Cloud Trace and Cloud Logging on from there
      env {
        name  = "SPRING_CLOUD_GCP_PROJECT_ID"
        value = var.project_id
      }
      # the gcp profile : application-gcp.yaml, which reads the three variables after it
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "gcp"
      }
      env {
        name  = "MONGODB_HOST"
        value = local.mongodb_host
      }
      env {
        name = "MONGODB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.mongodb_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "PRICING_URL"
        value = google_cloud_run_v2_service.pricing_api.uri
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_version.mongodb_password,
    google_secret_manager_secret_iam_member.orders_api,
    google_project_iam_member.traces,
  ]
}

resource "google_cloud_run_v2_service" "pricing_api" {
  name     = "pricing-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  # a demo : public like orders-api — calling it with an identity token would be Cloud Run
  # plumbing, not observability
  invoker_iam_disabled = true
  deletion_protection  = false

  template {
    service_account = google_service_account.service["pricing-api"].email

    scaling {
      max_instance_count = 1
    }

    containers {
      image = var.pricing_api_image

      ports {
        container_port = 8081 # pricing-api's own server.port
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = false
        startup_cpu_boost = true
      }

      env {
        name  = "SPRING_CLOUD_GCP_PROJECT_ID"
        value = var.project_id
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_project_iam_member.traces,
  ]
}
