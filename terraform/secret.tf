# the connection string carries the password : it reaches the container through Secret Manager,
# never through a plain environment variable (the Terraform state still holds it — keep it local)
resource "google_secret_manager_secret" "mongodb_uri" {
  secret_id = "orders-api-mongodb-uri"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "mongodb_uri" {
  secret      = google_secret_manager_secret.mongodb_uri.id
  secret_data = local.mongodb_uri
}

resource "google_secret_manager_secret_iam_member" "orders_api" {
  secret_id = google_secret_manager_secret.mongodb_uri.id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.orders_api.member
}
