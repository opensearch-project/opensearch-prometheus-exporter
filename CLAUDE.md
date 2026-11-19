# CLAUDE.md

Guidance for AI coding agents working in this repository. For user-facing documentation
(plugin settings, installation, build and test commands) see [README.md](README.md);
for contribution rules see [CONTRIBUTING.md](CONTRIBUTING.md).

## Project Overview

The **Prometheus Exporter Plugin for OpenSearch** is a Java plugin that exposes OpenSearch
cluster and node metrics in Prometheus text format via a `GET /_prometheus/metrics` endpoint.

## Build & Test

```bash
./gradlew clean build   # build
./gradlew clean check   # all tests: unit, integTest, yamlRestTest
./bwctest.sh            # backward compatibility tests (not part of check)
./gradlew run           # run a local cluster with the plugin installed (http://localhost:9200)
```

`./gradlew run` starts a 2-node cluster (node count comes from `testClusters.all` in `build.gradle`;
`-PnumNodes` is not supported). Try it with `curl localhost:9200/_prometheus/metrics`. Useful options:
`--debug-jvm`, `--preserve-data`, `--data-dir <path>`.

See the README for running a single YAML REST test. YAML REST specs live under `src/yamlRestTest`.

## Architecture

### Request Flow

```
GET /_prometheus/metrics
  → RestPrometheusMetricsAction          (REST layer)
  → NodePrometheusMetricsAction          (action definition; privilege: cluster:monitor/prometheus/metrics)
  → TransportNodePrometheusMetricsAction (orchestrates async data collection)
      ├── ClusterHealthRequest
      ├── NodesInfoRequest (local)
      ├── NodesStatsRequest (configurable node filter)
      ├── IndicesStatsRequest (configurable index filter)
      └── ClusterStateRequest (for cluster settings, optional)
  → PrometheusMetricsCollector           (maps OpenSearch stats → Prometheus metric updates)
  → PrometheusMetricsCatalog             (registry wrapper; creates gauges/summaries, renders text format)
  → Prometheus text format HTTP response
```

### Packages

All code lives under `org.opensearch.plugin.prometheus`:

| Package | Contents |
|---|---|
| (root) | `PrometheusExporterPlugin` — entry point; registers actions, REST handler, settings |
| `.rest` | `RestPrometheusMetricsAction` — the HTTP endpoint |
| `.action` | Action definition, transport action, request/response, `ClusterStatsData` |
| `.collector` | `PrometheusMetricsCollector`, `PrometheusMetricsCatalog`, `PrometheusSettings` |
| `.filter` | `PrometheusActionFilter` |

`PrometheusMetricsCatalog` owns the Prometheus `CollectorRegistry` and provides typed helpers
(`registerClusterGauge`, `setClusterGauge`, `registerNodeGauge`, `setNodeGauge`, ...).
`PrometheusMetricsCollector` calls into it with the data fetched by the transport action.
Plugin settings are defined in `PrometheusSettings`.

## Code Style

Checkstyle is enforced (`config/checkstyle/checkstyle.xml`). Notable rules:
- No `System.out.println`
- Use "cluster manager" terminology, not "master"
- Sun conventions: Javadoc, naming, imports, whitespace

Every source file needs the Apache 2.0 license header, and every commit needs a DCO sign-off (`git commit -s`).

## Compatibility

Plugin versions track OpenSearch versions (plugin `X.Y.Z.0` → OpenSearch `X.Y.Z`). Current versions,
including the BWC test target (`BWCversion` / `BWCPluginVersion`), are in `gradle.properties`;
see [COMPATIBILITY.md](COMPATIBILITY.md) for the full matrix.
