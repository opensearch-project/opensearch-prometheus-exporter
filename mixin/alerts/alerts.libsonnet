{
  prometheusAlerts+:: {
    groups+: [
      {
        name: 'opensearch.alerts',
        rules: [

          // ==========================================
          // Cluster health alerts
          // ==========================================
          {
            alert: 'OpenSearchClusterNotHealthy',
            expr: |||
              sum by (cluster) (opensearch_cluster_status == 2)
            |||,
            'for': '%(oseClusterHealthStatusRED)s' % $._config,
            labels: {
              severity: 'critical',
            },
            annotations: {
              summary: 'Cluster health status is RED',
              message: "Cluster {{ $labels.cluster }} health status has been RED for at least %(oseClusterHealthStatusRED)s. Cluster does not accept writes, shards may be missing or master node hasn't been elected yet." % $._config,
            },
          },
          {
            alert: 'OpenSearchClusterNotHealthy',
            expr: |||
              sum by (cluster) (opensearch_cluster_status == 1)
            |||,
            'for': '%(oseClusterHealthStatusYELLOW)s' % $._config,
            labels: {
              severity: 'warning',
            },
            annotations: {
              summary: 'Cluster health status is YELLOW',
              message: 'Cluster {{ $labels.cluster }} health status has been YELLOW for at least %(oseClusterHealthStatusYELLOW)s. Some shard replicas are not allocated.' % $._config,
            },
          },

          // ==========================================
          // Bulk Requests Rejection
          // ==========================================
          {
            alert: 'OpenSearchBulkRequestsRejectionJumps',
            expr: |||
              round( bulk:reject_ratio:rate2m * 100, 0.001 ) > %(oseBulkPctIncrease)s
            ||| % $._config,
            'for': '10m',
            labels: {
              severity: 'warning',
            },
            annotations: {
              summary: 'High Bulk Rejection Ratio - {{ $value }}%',
              message: 'High Bulk Rejection Ratio at {{ $labels.node }} node in {{ $labels.cluster }} cluster. This node may not be keeping up with the indexing speed.',
            },
          },

          // ==========================================
          // Disk Usage
          // ==========================================

          {
            alert: 'OpenSearchNodeDiskWatermarkReached',
            expr: |||
              sum by (cluster, instance, node) (
                round(
                  (1 - (
                    opensearch_fs_path_available_bytes /
                    opensearch_fs_path_total_bytes
                  )
                ) * 100, 0.001)
              ) > %(oseDiskLowWaterMark)s
            ||| % $._config,
            'for': '5m',
            labels: {
              severity: 'alert',
            },
            annotations: {
              summary: 'Disk Low Watermark Reached - disk saturation is {{ $value }}%',
              message: 'Disk Low Watermark Reached at {{ $labels.node }} node in {{ $labels.cluster }} cluster. Shards can not be allocated to this node anymore. You should consider adding more disk to the node.',
            },
          },
          {
            alert: 'OpenSearchNodeDiskWatermarkReached',
            expr: |||
              sum by (cluster, instance, node) (
                round(
                  (1 - (
                    opensearch_fs_path_available_bytes /
                    opensearch_fs_path_total_bytes
                  )
                ) * 100, 0.001)
              ) > %(oseDiskHighWaterMark)s
            ||| % $._config,
            'for': '5m',
            labels: {
              severity: 'high',
            },
            annotations: {
              summary: 'Disk High Watermark Reached - disk saturation is {{ $value }}%',
              message: 'Disk High Watermark Reached at {{ $labels.node }} node in {{ $labels.cluster }} cluster. Some shards will be re-allocated to different nodes if possible. Make sure more disk space is added to the node or drop old indices allocated to this node.',
            },
          },

          // Remaining space on the node:
          //   sum by (cluster, instance, node) (opensearch_fs_path_free_bytes)
          // Though this ^^ might not be the best metric, because node can have multiple paths where the
          // data can be stored but pure sum by paths is not is fully utilized.
          // There can be issues ingeritted from: https://github.com/elastic/elasticsearch/issues/27174
          //
          // Total index size by node:
          //   sum by (cluster, instance, node) (opensearch_index_store_size_bytes{context="total"}) // <- this does not seem to work correctly!?
          //   sum by (cluster, instance, node) (opensearch_indices_store_size_bytes)

          /* Commenting out, this alert is not yet matur enough.
          {
            alert: 'OpenSearchNodeDiskLowForSegmentMerges',
            expr: |||
              sum by (cluster, instance, node) (opensearch_fs_path_free_bytes) /
              sum by (cluster, instance, node) (opensearch_indices_store_size_bytes)
              < %(oseDiskSpaceRatioForMerges)s
            ||| % $._config,
            'for': '15m',
            labels: {
              severity: 'warning',
            },
            annotations: {
              summary: 'Free disk may be low for optimal segment merges',
              message: 'Free disk at {{ $labels.node }} node in {{ $labels.cluster }} cluster may be low for optimal segment merges',
            },
          },
          */

          // ==========================================
          // JVM Heap Usage
          // ==========================================
          {
            alert: 'OpenSearchJVMHeapUseHigh',
            expr: |||
              sum by (cluster, instance, node) (opensearch_jvm_mem_heap_used_percent) > %(oseJvmHeapUseThreshold)s
            ||| % $._config,
            'for': '10m',
            labels: {
              severity: 'alert',
            },
            annotations: {
              summary: 'JVM Heap usage on the node is high',
              message: 'JVM Heap usage on the node {{ $labels.node }} in {{ $labels.cluster }} cluster is {{ $value }}%.',
            },
          },

          // ==========================================
          // CPU Usage
          // ==========================================
          // TODO: Check how number of CPU cores is reflected in calculation of this metric.
          {
            alert: 'OpenSearchHostSystemCPUHigh',
            expr: |||
              sum by (cluster, instance, node) (opensearch_os_cpu_percent) > %(oseSystemCPUHigh)s
            ||| % $._config,
            'for': '1m',
            labels: {
              severity: 'alert',
            },
            annotations: {
              summary: 'System CPU usage is high',
              message: 'System CPU usage on the node {{ $labels.node }} in {{ $labels.cluster }} cluster is {{ $value }}%',
            },
          },
          {
            alert: 'OpenSearchProcessCPUHigh',
            expr: |||
              sum by (cluster, instance, node) (opensearch_process_cpu_percent) > %(oseProcessCPUHigh)s
            ||| % $._config,
            'for': '1m',
            labels: {
              severity: 'alert',
            },
            annotations: {
              summary: 'OSE process CPU usage is high',
              message: 'OSE process CPU usage on the node {{ $labels.node }} in {{ $labels.cluster }} cluster is {{ $value }}%',
            },
          },

        ],
      },
    ],
  },
}
