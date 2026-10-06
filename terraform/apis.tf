resource "google_project_service" "apis" {
  for_each = toset([
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "logging.googleapis.com",
    # traces arrive over OTLP through the Telemetry API, and are stored by Cloud Trace
    "telemetry.googleapis.com",
    "cloudtrace.googleapis.com",
  ])

  service = each.value

  # destroy removes what the demo built, not the project's APIs
  disable_on_destroy = false
}
