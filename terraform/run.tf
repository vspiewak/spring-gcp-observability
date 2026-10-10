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
      name  = "orders-api"
      image = var.orders_api_image

      # spans go to the collector, over localhost : it has to be up first
      depends_on = ["collector"]

      # with a sidecar, Cloud Run no longer assumes which container takes the requests, nor its port
      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        # spans leave in batches, after the response — and the collector batches them again : with
        # the CPU throttled between requests, the export call fails ("Failed to export spans") and
        # the batch is lost — measured when the service still exported to Google itself
        cpu_idle          = false
        startup_cpu_boost = true
      }

      # the only Google Cloud knowledge the service gets : which project it runs in — Spring Cloud
      # GCP's own property ; the observability starter turns the collector and Cloud Logging on from there
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

    # Google's OpenTelemetry Collector : OTLP in on localhost:4318, Cloud Trace out — collector.yaml
    containers {
      name  = "collector"
      image = local.collector_image
      args  = ["--config=/etc/otelcol-google/config.yaml"]

      # Cloud Run's defaults for a container, written out : the instance now runs two
      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = false
        startup_cpu_boost = true
      }

      # collector.yaml files every span under it
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }

      # the health_check extension : orders-api starts once it answers
      startup_probe {
        http_get {
          path = "/"
          port = 13133
        }
        period_seconds    = 1
        failure_threshold = 30
      }

      volume_mounts {
        name       = "collector-config"
        mount_path = "/etc/otelcol-google"
      }
    }

    volumes {
      name = "collector-config"
      secret {
        secret = google_secret_manager_secret.collector_config.secret_id
        items {
          # this exact version, not latest : a change to collector.yaml rolls out a new revision
          version = google_secret_manager_secret_version.collector_config.version
          path    = "config.yaml"
        }
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_version.mongodb_password,
    google_secret_manager_secret_iam_member.orders_api,
    google_secret_manager_secret_iam_member.collector_config,
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
      name  = "pricing-api"
      image = var.pricing_api_image

      depends_on = ["collector"]

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

    containers {
      name  = "collector"
      image = local.collector_image
      args  = ["--config=/etc/otelcol-google/config.yaml"]

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = false
        startup_cpu_boost = true
      }

      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }

      startup_probe {
        http_get {
          path = "/"
          port = 13133
        }
        period_seconds    = 1
        failure_threshold = 30
      }

      volume_mounts {
        name       = "collector-config"
        mount_path = "/etc/otelcol-google"
      }
    }

    volumes {
      name = "collector-config"
      secret {
        secret = google_secret_manager_secret.collector_config.secret_id
        items {
          version = google_secret_manager_secret_version.collector_config.version
          path    = "config.yaml"
        }
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_iam_member.collector_config,
    google_project_iam_member.traces,
  ]
}
