# the identity orders-api runs as : allowed to write traces and to read its own secret, nothing else
resource "google_service_account" "orders_api" {
  account_id   = "orders-api"
  display_name = "orders-api (Cloud Run runtime)"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "orders_api_traces" {
  project = var.project_id
  role    = "roles/telemetry.tracesWriter"
  member  = google_service_account.orders_api.member
}
