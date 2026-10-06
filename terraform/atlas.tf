resource "mongodbatlas_project" "demo" {
  name   = "spring-gcp-observability"
  org_id = var.atlas_org_id
}

# the free tier : one M0 per project, shared, on Google Cloud
resource "mongodbatlas_advanced_cluster" "demo" {
  project_id   = mongodbatlas_project.demo.id
  name         = "orders"
  cluster_type = "REPLICASET"

  replication_specs = [{
    region_configs = [{
      provider_name         = "TENANT"
      backing_provider_name = "GCP"
      region_name           = var.atlas_region
      priority              = 7
      electable_specs = {
        instance_size = "M0"
      }
    }]
  }]

  termination_protection_enabled = false
}

resource "random_password" "orders_api" {
  length  = 32
  special = false # keeps the connection string free of escaping
}

resource "mongodbatlas_database_user" "orders_api" {
  project_id         = mongodbatlas_project.demo.id
  username           = "orders-api"
  password           = random_password.orders_api.result
  auth_database_name = "admin"

  roles {
    role_name     = "readWrite"
    database_name = "orders"
  }

  scopes {
    name = mongodbatlas_advanced_cluster.demo.name
    type = "CLUSTER"
  }
}

# DEMO ONLY : Cloud Run has no fixed outbound IP, and the free M0 tier has no private networking.
# The cluster is open to the internet behind a 32-character random password. A real deployment
# uses a dedicated cluster with a private endpoint, or a static egress IP through Cloud NAT.
resource "mongodbatlas_project_ip_access_list" "anywhere" {
  project_id = mongodbatlas_project.demo.id
  cidr_block = "0.0.0.0/0"
  comment    = "demo only : Cloud Run has no static egress IP"
}

locals {
  mongodb_uri = format(
    "mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority",
    mongodbatlas_database_user.orders_api.username,
    random_password.orders_api.result,
    trimprefix(mongodbatlas_advanced_cluster.demo.connection_strings.standard_srv, "mongodb+srv://"),
  )
}
