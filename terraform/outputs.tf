output "project_id" {
  value = var.project_id
}

output "service_url" {
  value = google_cloud_run_v2_service.orders_api.uri
}

output "pricing_url" {
  value = google_cloud_run_v2_service.pricing_api.uri
}

output "image_repository" {
  value = local.image_repository
}

# the images Cloud Run currently runs — scripts/deploy.sh keeps them on its next first apply
output "orders_api_image" {
  value = var.orders_api_image
}

output "pricing_api_image" {
  value = var.pricing_api_image
}
