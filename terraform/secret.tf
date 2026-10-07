# the MongoDB password reaches the container through Secret Manager, never through a plain environment
# variable (the Terraform state still holds it — keep it local)
resource "google_secret_manager_secret" "mongodb_password" {
  secret_id = "orders-api-mongodb-password"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "mongodb_password" {
  secret      = google_secret_manager_secret.mongodb_password.id
  secret_data = random_password.orders_api.result
}

resource "google_secret_manager_secret_iam_member" "orders_api" {
  secret_id = google_secret_manager_secret.mongodb_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.service["orders-api"].member
}
