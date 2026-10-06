locals {
  services = toset(["orders-api", "pricing-api"])
}

# one identity per service : each may write traces, only orders-api may read the MongoDB secret
resource "google_service_account" "service" {
  for_each = local.services

  account_id   = each.key
  display_name = "${each.key} (Cloud Run runtime)"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "traces" {
  for_each = local.services

  project = var.project_id
  role    = "roles/telemetry.tracesWriter"
  member  = google_service_account.service[each.key].member
}
