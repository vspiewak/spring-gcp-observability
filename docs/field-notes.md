# 📓 Cloud Run field notes

Measured while building [spring-gcp-observability](../README.md), on Spring Boot 4.1, Cloud Run and the Telemetry API, October 2026 :

* **Throttled CPU loses spans.** Spans leave in batches, after the response. With Cloud Run's default
  request-based CPU, the export call failed (`Failed to export spans. The request could not be
  executed.`) and the batch was gone ; with `cpu_idle = false`, the same idle request's spans arrived
  within 20 seconds.
* **Every trace starts with a "Missing span ID".** For a request Cloud Run chose not to sample, it
  still hands the service a `traceparent` naming its own span as the parent — but never records that
  span : the service's spans hang under a placeholder. The alternatives are worse : follow Cloud Run's
  decision and most requests leave no trace, start a fresh trace and the service's logs no longer share
  Cloud Run's request log trace id. When Cloud Run does sample, its own span is there — and the
  placeholder moves up one level : Cloud Run's span has a parent of its own, never recorded in the
  project (presumably Google's front end — not documented that we found).
* **A sampled `traceparent` does not force Cloud Run's span.** `demo.sh` asks for sampling, and Cloud
  Run mostly honours it — not always : a GET sent right after the POST that seeds it came back without
  Cloud Run's span, the same GET sent 15 seconds later with it. Cloud Run's 0.1 request per second
  per instance cap looks like the reason. The service's own spans are there either way.
* **The trace shows the cold start.** About fifteen minutes after their last request, both services had
  scaled to zero (Cloud Monitoring's instance count). The next GET found pricing-api cold — `demo.sh`'s
  POST had just woken orders-api : the call to it took 9.8 seconds, of which pricing-api itself spent
  254 ms — the rest is Cloud Run's front end waiting for an instance to start.

  ![A cold-start trace : the call to pricing-api lasts 9.8 s, pricing-api's own server span 254 ms](./images/trace-cold-start.png)
* **Boot's OpenTelemetry starter exports metrics too.** It ships an OTLP metrics registry, on by
  default, aimed at `http://localhost:4318/v1/metrics` : left on, every service logs `Failed to publish
  metrics to OTLP receiver` once a minute. The defaults switch it off — this demo is about traces and
  logs.
* **Trace storage provisions itself, in `us`.** Spans are stored in an observability bucket named
  `_Trace`, created about two minutes after the project's first span — in the `us` location by default.
  Until it exists, reading a trace answers `404 _Trace bucket not found`. Create it yourself first if the
  location matters to you.
* **The very first Cloud Run deployment into a fresh project failed** with an *internal error*, and
  succeeded when run again — `deploy.sh` says so when an apply fails.
* **Spans land about a minute after the request.** Batched in the service for up to five seconds, then
  ingested : a trace read right away can show pricing-api's spans and not yet orders-api's.
* **Trace flags are `03`, not `01`.** OpenTelemetry Java sets W3C Trace Context Level 2's *random trace
  id* bit next to *sampled* ; Cloud Run's front end takes it as sampled.
* **Spring Cloud GCP for credentials, not for traces.** Its core starter gives the token the standard
  Spring way, shared with Pub/Sub, Secret Manager and the rest — about 15 jars a service using any of
  them carries anyway. Its trace starter is another story : built on Brave and Zipkin over gRPC, not on
  the OpenTelemetry bridge Boot exports from, it follows Cloud Run's sampling flag.
* **The free M0 tier** has no Workload Identity Federation (M10 and up), no peering, no private
  endpoint : the service authenticates with a password.
