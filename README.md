# Prometheus Exporter Plugin for OpenSearch

The [Prometheus® exporter](https://prometheus.io/docs/instrumenting/writing_exporters/) plugin for OpenSearch® exposes many OpenSearch metrics in [Prometheus format](https://prometheus.io/docs/instrumenting/exposition_formats/).

- [Preface](#preface)
- [Introduction](#introduction)
- [Compatibility Matrix](#compatibility-matrix)
- [Install or Remove Plugin](#install-or-remove-plugin)
- [Plugin Configuration](#plugin-configuration)
  - [Static settings](#static-settings) 
  - [Dynamic settings](#dynamic-settings) 
- [Usage](#usage)
- [Build from Source](#build-from-source)
- [Testing](#testing)
  - [BWC Testing](#bwc-testing)
- [License](#license)
- [Trademarks & Attributions](#trademarks--attributions)

---

## Preface

This repository contains two projects that are related, but each can be used independently:

1. The first project is a Java plugin – Prometheus Exporter Plugin for OpenSearch® – this code lives in the `main` branch.
2. The second project is a Jsonnet bundle – OpenSearch® Mixin – this code lives in the `mixin` branch.

## Introduction

This plugin was started as a fork of the Prometheus exporter for Elasticsearch® by @vvanholl (it was forked in version 7.10.2.0; commit hash: 8dc7f85) utilizing [OpenSearch plugin template](https://github.com/opensearch-project/opensearch-plugin-template-java). It uses the official [Prometheus Java Simpleclient](https://github.com/prometheus/client_java/tree/simpleclient).

**Currently, the available metrics are:**

- Cluster status
- Nodes status:
    - JVM
    - Indices (global)
    - Transport
    - HTTP
    - Scripts
    - Process
    - Operating System
    - File System
    - Circuit Breaker
- Indices status
- Cluster settings (notably [disk watermarks](https://docs.opensearch.org/latest/install-and-configure/configuring-opensearch/cluster-settings/#cluster-level-routing-and-allocation-settings) that can be updated dynamically)

## Compatibility Matrix

For the complete compatibility matrix including all supported OpenSearch versions and corresponding plugin versions, see [COMPATIBILITY.md](COMPATIBILITY.md).

## Install or Remove Plugin

You need to install the plugin on every OpenSearch node that will be scraped by Prometheus.

To **install** the plugin:

`./bin/opensearch-plugin install https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/3.3.2.0/prometheus-exporter-3.3.2.0.zip`

To **remove** the plugin.

`./bin/opensearch-plugin remove prometheus-exporter`

For more info about plugin CLI, visit: <https://docs.opensearch.org/latest/install-and-configure/plugins/>

## Plugin Configuration

### Static settings

Static settings are configured in `config/opensearch.yml`.

#### Metric name prefix

All metric names share common prefix, by default set to `opensearch_`.

The value of this prefix can be customized using setting:
```
prometheus.metric_name.prefix: "opensearch_"
```

### Dynamic settings

Dynamic settings are configured in `config/opensearch.yml` but they can also be [updated](https://docs.opensearch.org/latest/api-reference/cluster-api/cluster-settings/#update-cluster-setting) at any time via REST API.

#### Index level metrics

Whether to export detailed index level metrics or not. Default value: `true`.

Detailed index level metrics can represent a lot of data and can lead to high-cardinality labels in Prometheus.
If you do not need detailed index level metrics **it is recommended to disable it** via setting:

```
prometheus.indices: false
```

#### Cluster settings

Whether to export cluster settings metrics or not. Default value: `true`.

To disable exporting cluster settings use:
```
prometheus.cluster.settings: false
```

#### Nodes filter

Metrics include statistics about individual OpenSearch nodes.
By default, only statistics from the node that received the request are included.

Prometheus exporter can be configured to include statistics from other nodes as well.
This filter is directly utilizing OpenSearch [Node filters](https://docs.opensearch.org/latest/api-reference/nodes-apis/index/#node-filters) feature.
Default value: `"_local"`.

For example to get stats for all cluster nodes from any node use settings:
```
prometheus.nodes.filter: "_all"
```

#### Indices filter

Prometheus exporter can be configured to filter indices statistics from selected indices.
To target all indices, use "" or * or _all or null.
Default value: `""`.

For example to filter indices statistics:
```
prometheus.indices_filter.selected_indices: "log-*,*log,*log*,log*-test"
```

Users can select which option for filtering indices statistics.
Default value: `"STRICT_EXPAND_OPEN_FORBID_CLOSED"`.

For example to select an option:
```
prometheus.indices_filter.selected_option: "LENIENT_EXPAND_OPEN"
```
Here is a list of indices options available for selection
```
STRICT_EXPAND_OPEN: indices options that requires every specified index to exist, expands wildcards only to open indices and allows that no indices are resolved from wildcard expressions (not returning an error).
STRICT_EXPAND_OPEN_HIDDEN: indices options that requires every specified index to exist, expands wildcards only to open indices, includes hidden indices, and allows that no indices are resolved from wildcard expressions (not returning an error).
STRICT_EXPAND_OPEN_FORBID_CLOSED: indices options that requires every specified index to exist, expands wildcards only to open indices, allows that no indices are resolved from wildcard expressions (not returning an error) and forbids the use of closed indices by throwing an error.
STRICT_EXPAND_OPEN_HIDDEN_FORBID_CLOSED: indices options that requires every specified index to exist, expands wildcards only to open indices, includes hidden indices, and allows that no indices are resolved from wildcard expressions (not returning an error) and forbids the use of closed indices by throwing an error.
STRICT_EXPAND_OPEN_FORBID_CLOSED_IGNORE_THROTTLED: indices options that requires every specified index to exist, expands wildcards only to open indices, allows that no indices are resolved from wildcard expressions (not returning an error), forbids the use of closed indices by throwing an error and ignores indices that are throttled.
STRICT_EXPAND_OPEN_CLOSED: indices option that requires every specified index to exist, expands wildcards to both open and closed indices and allows that no indices are resolved from wildcard expressions (not returning an error).
STRICT_EXPAND_OPEN_CLOSED_HIDDEN: indices option that requires every specified index to exist, expands wildcards to both open and closed indices, includes hidden indices, and allows that no indices are resolved from wildcard expressions (not returning an error).
STRICT_SINGLE_INDEX_NO_EXPAND_FORBID_CLOSED: indices option that requires each specified index or alias to exist, doesn't expand wildcards and throws error if any of the aliases resolves to multiple indices.
LENIENT_EXPAND_OPEN: indices options that ignores unavailable indices, expands wildcards only to open indices and allows that no indices are resolved from wildcard expressions (not returning an error).
LENIENT_EXPAND_OPEN_HIDDEN: indices options that ignores unavailable indices, expands wildcards to open and hidden indices, and allows that no indices are resolved from wildcard expressions (not returning an error).
LENIENT_EXPAND_OPEN_CLOSED: indices options that ignores unavailable indices, expands wildcards to both open and closed indices and allows that no indices are resolved from wildcard expressions (not returning an error).
LENIENT_EXPAND_OPEN_CLOSED_HIDDEN: indices options that ignores unavailable indices, expands wildcards to all open and closed indices and allows that no indices are resolved from wildcard expressions (not returning an error).
```

## Usage

Metrics are directly available at:

    http(s)://<opensearch-host>:9200/_prometheus/metrics

As a sample result, you get:

```
# HELP opensearch_process_mem_total_virtual_bytes Memory used by ES process
# TYPE opensearch_process_mem_total_virtual_bytes gauge
opensearch_process_mem_total_virtual_bytes{cluster="develop",node="develop01",} 3.626733568E9
# HELP opensearch_indices_indexing_is_throttled_bool Is indexing throttling ?
# TYPE opensearch_indices_indexing_is_throttled_bool gauge
opensearch_indices_indexing_is_throttled_bool{cluster="develop",node="develop01",} 0.0
# HELP opensearch_jvm_gc_collection_time_seconds Time spent for GC collections
# TYPE opensearch_jvm_gc_collection_time_seconds counter
opensearch_jvm_gc_collection_time_seconds{cluster="develop",node="develop01",gc="old",} 0.0
opensearch_jvm_gc_collection_time_seconds{cluster="develop",node="develop01",gc="young",} 0.0
# HELP opensearch_indices_requestcache_memory_size_bytes Memory used for request cache
# TYPE opensearch_indices_requestcache_memory_size_bytes gauge
opensearch_indices_requestcache_memory_size_bytes{cluster="develop",node="develop01",} 0.0
# HELP opensearch_indices_search_open_contexts_number Number of search open contexts
# TYPE opensearch_indices_search_open_contexts_number gauge
opensearch_indices_search_open_contexts_number{cluster="develop",node="develop01",} 0.0
# HELP opensearch_jvm_mem_nonheap_used_bytes Memory used apart from heap
# TYPE opensearch_jvm_mem_nonheap_used_bytes gauge
opensearch_jvm_mem_nonheap_used_bytes{cluster="develop",node="develop01",} 5.5302736E7
...
```

### Configure the Prometheus target

On your Prometheus servers, configure a new job as usual.

For example, if you have a cluster of 3 nodes:

```YAML
- job_name: opensearch
  scrape_interval: 10s
  metrics_path: "/_prometheus/metrics"
  static_configs:
  - targets:
    - node1:9200
    - node2:9200
    - node3:9200
```

Of course, you could use the service discovery service instead of a static config.

Just keep in mind that `metrics_path` must be `/_prometheus/metrics`, otherwise Prometheus will find no metric.

## Build from Source

To build the plugin you need JDK 17:

```
./gradlew clean build
```
If you have doubts about the system requirements, please check the [CI.yml](.github/workflows/CI.yml) file for more information.

## Testing

Project contains:
- [integration tests](src/yamlRestTest/resources/rest-api-spec) implemented using
[rest layer](https://github.com/opensearch-project/OpenSearch/blob/main/TESTING.md#testing-the-rest-layer)
framework
- [internal cluster tests](src/test)

Complete test suite is run using:
```
./gradlew clean check
```

To run individual integration rest test file use:
```
./gradlew :yamlRestTest \
  -Dtests.method="test {yaml=/20_11_index_level_metrics_disabled/Dynamically disable index level metrics}"
```

### BWC Testing

Backward Compatibility (BWC) Testing is run manually using provided shell script:

```
./bwctest.sh
```

It is not part of `./gradlew [build|check]` task(s), but it is included in the CI workflow.

OpenSearch versions used during BWC tests use determined by properties located in `gradle.properties` file. Specifically `project.version` and `project.BWCversion`. Version of plugin deployed into `project.BWCversion` cluster is specified by `project.BWCPluginVersion` property.

In the beginning of BWC tests the actual version of plugin (`project.version`) is build using `bundlePlugin` gradle task and the `project.BWCPluginVersion` plugin is downloaded from GitHub releases. Both ZIP files are placed into `src/test/resource/org/opensearch/prometheus-exrpoter/bwc/prometheus-exporter` folder (this folder is ignored by git). 

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Trademarks & Attributions

Prometheus is a registered trademark of The Linux Foundation. OpenSearch is a registered trademark of Amazon Web Services. Elasticsearch is a registered trademark of Elasticsearch BV.

We gratefully thank all the contributors from the [Aiven organization](https://github.com/Aiven-Open) for their work on this project prior to its migration to the OpenSearch organization.


