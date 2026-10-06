output "project_id" {
  value = var.project_id
}

output "service_url" {
  value = google_cloud_run_v2_service.orders_api.uri
}

output "image_repository" {
  value = local.image_repository
}

output "image" {
  description = "The image Cloud Run currently runs — scripts/deploy.sh keeps it on the next apply."
  value       = var.image
}
