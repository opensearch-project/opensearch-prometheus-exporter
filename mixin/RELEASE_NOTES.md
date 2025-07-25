# Mixin-1.0.0-rc1

Release date: 2022-12-06

## New Features:

- Initial **Grafana Dashboard** that included sections for:
  - **Cluster high-level status**: Cluster health charts and basic node counts
  - **Cluster shards**: Number of cluster shards in individual states (active, initializing, relocating and unassigned)
  - **Thread Pools**: Internal Thread Pools stats
  - **System**: Basic Host system metrics provided as by OpenSearch
  - **Document and Latencies**: Document indexing, deleting and searching stats 
  - **Caches**: Field data and Query caches stats
  - **Throttling**: Indexing and merging throttling
  - **JVM**: Basic metrics of cluster nodes JVM (Heap and GC)

- Initial **Prometheus Alerts**:
  - **OpenSearchClusterNotHealthy**: Cluster health status alert
  - **OpenSearchBulkRequestsRejectionJumps**: High increase in the node bulk request rejections count
  - **OpenSearchNodeDiskWatermarkReached**: High/Low disk watermark threshold has been reached on the node
  - **OpenSearchJVMHeapUseHigh**: High JVM Heap use on the node
  - **OpenSearchHostSystemCPUHigh**: Host system CPU load is high
  - **OpenSearchProcessCPUHigh**: OpenSearch process CPU load is high

## Known issues:

- Cluster status chart does not correctly display the cluster health (ie: Green, Yellow, Red).
- Node DiskWatermark alerts use static thresholds value while they should be using node settings value exposed in metric (see [relevant issue](https://github.com/lukas-vlcek/elasticsearch-mixin/issues/5)).
