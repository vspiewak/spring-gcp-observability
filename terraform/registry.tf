resource "google_artifact_registry_repository" "images" {
  repository_id = "spring-gcp-observability"
  location      = var.region
  format        = "DOCKER"

  depends_on = [google_project_service.apis]
}

locals {
  image_repository = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/orders-api"
}
