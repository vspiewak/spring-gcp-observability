variable "project_id" {
  description = "The Google Cloud project the demo deploys into : a fresh one, billing linked."
  type        = string
}

variable "region" {
  description = "Cloud Run and Artifact Registry region."
  type        = string
  default     = "europe-west1"
}

variable "atlas_org_id" {
  description = "The MongoDB Atlas organization the demo creates its project in."
  type        = string
}

variable "atlas_region" {
  description = "Atlas name of the Google Cloud region hosting the free M0 cluster (europe-west1 is WESTERN_EUROPE)."
  type        = string
  default     = "WESTERN_EUROPE"
}

variable "image" {
  description = "The orders-api image. scripts/deploy.sh sets it to the digest it just pushed ; the default is Google's public placeholder, so the very first apply has something to run."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}
