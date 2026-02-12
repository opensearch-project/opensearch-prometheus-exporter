local g = import 'github.com/grafana/grafonnet/gen/grafonnet-latest/main.libsonnet';

local var = g.dashboard.variable;
local panel = g.panel;
local query = g.query;

// Helper to create Prometheus query target
local promQuery(expr, legendFormat='') =
  query.prometheus.new('$datasource', expr)
  + query.prometheus.withLegendFormat(legendFormat)
  + query.prometheus.withIntervalFactor(2);

// Base timeSeries panel with default styling (matching original dashboard)
local timeSeriesBase =
  panel.timeSeries.fieldConfig.defaults.custom.withShowPoints('never')
  + panel.timeSeries.fieldConfig.defaults.custom.withFillOpacity(10);

// ==========================================
// Variables
// ==========================================
local variables = [
  var.datasource.new('datasource', 'prometheus'),

  var.custom.new('interval', ['15s', '30s', '1m', '5m', '1h', '6h', '1d'])
  + var.custom.generalOptions.withLabel('Interval')
  + var.custom.generalOptions.withCurrent('1m'),

  var.query.new('cluster', 'label_values(opensearch_cluster_status, cluster)')
  + var.query.withDatasource('prometheus', '$datasource')
  + var.query.generalOptions.withLabel('Cluster')
  + var.query.withSort(1)
  + var.query.withRefresh(1),

  var.query.new('node', 'label_values(opensearch_jvm_uptime_seconds{cluster="$cluster"}, node)')
  + var.query.withDatasource('prometheus', '$datasource')
  + var.query.generalOptions.withLabel('Node')
  + var.query.withSort(1)
  + var.query.withRefresh(1)
  + var.query.selectionOptions.withIncludeAll(true),

  var.query.new('shard_type', 'label_values(opensearch_cluster_shards_number, type)')
  + var.query.withDatasource('prometheus', '$datasource')
  + var.query.generalOptions.withLabel('Shard')
  + var.query.generalOptions.showOnDashboard.withNothing()
  + var.query.withSort(1)
  + var.query.withRefresh(1)
  + var.query.selectionOptions.withIncludeAll(true),

  var.query.new('pool_name', 'label_values(opensearch_threadpool_tasks_number, name)')
  + var.query.withDatasource('prometheus', '$datasource')
  + var.query.generalOptions.withLabel('Threadpool Type name')
  + var.query.generalOptions.showOnDashboard.withNothing()
  + var.query.withSort(1)
  + var.query.withRefresh(1)
  + var.query.selectionOptions.withIncludeAll(true),
];

// ==========================================
// Cluster Row Panels
// ==========================================
local clusterStatusPanel =
  panel.stat.new('Cluster status')
  + panel.stat.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.stat.queryOptions.withTargets([
    promQuery('max(opensearch_cluster_status{cluster="$cluster"})'),
  ])
  + panel.stat.options.withColorMode('background')
  + panel.stat.options.withGraphMode('none')
  + panel.stat.standardOptions.withMappings([
    { type: 'value', options: { '0': { text: 'GREEN', color: 'green' } } },
    { type: 'value', options: { '1': { text: 'YELLOW', color: 'yellow' } } },
    { type: 'value', options: { '2': { text: 'RED', color: 'red' } } },
  ])
  + { gridPos: { w: 4, h: 4 } };

local clusterHealthHistoryPanel =
  panel.timeSeries.new('Cluster Health History')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('(opensearch_cluster_status{cluster="$cluster"} == 0) + 1', 'GREEN')
    + query.prometheus.withIntervalFactor(10),
    promQuery('(opensearch_cluster_status{cluster="$cluster"} == 1)', 'YELLOW')
    + query.prometheus.withIntervalFactor(10),
    promQuery('(opensearch_cluster_status{cluster="$cluster"} == 2) - 1', 'RED')
    + query.prometheus.withIntervalFactor(10),
  ])
  + panel.timeSeries.options.legend.withShowLegend(false)
  + panel.timeSeries.fieldConfig.defaults.custom.withDrawStyle('bars')
  + panel.timeSeries.fieldConfig.defaults.custom.withFillOpacity(100)
  + panel.timeSeries.fieldConfig.defaults.custom.stacking.withMode('percent')
  + panel.timeSeries.standardOptions.withOverrides([
    { matcher: { id: 'byName', options: 'GREEN' }, properties: [{ id: 'color', value: { mode: 'fixed', fixedColor: 'green' } }] },
    { matcher: { id: 'byName', options: 'YELLOW' }, properties: [{ id: 'color', value: { mode: 'fixed', fixedColor: 'yellow' } }] },
    { matcher: { id: 'byName', options: 'RED' }, properties: [{ id: 'color', value: { mode: 'fixed', fixedColor: 'red' } }] },
  ])
  + { gridPos: { w: 8, h: 4 } };

local clusterNodesPanel =
  panel.stat.new('Nodes')
  + panel.stat.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.stat.queryOptions.withTargets([
    promQuery('max(opensearch_cluster_nodes_number{cluster="$cluster"})'),
  ])
  + { gridPos: { w: 4, h: 4 } };

local clusterDataNodesPanel =
  panel.stat.new('Data nodes')
  + panel.stat.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.stat.queryOptions.withTargets([
    promQuery('max(opensearch_cluster_datanodes_number{cluster="$cluster"})'),
  ])
  + { gridPos: { w: 4, h: 4 } };

local clusterPendingTasksPanel =
  panel.stat.new('Pending tasks')
  + panel.stat.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.stat.queryOptions.withTargets([
    promQuery('max(opensearch_cluster_pending_tasks_number{cluster="$cluster"})'),
  ])
  + { gridPos: { w: 4, h: 4 } };

// ==========================================
// Shards Row Panels
// ==========================================
local shardsTypePanel =
  panel.timeSeries.new('$shard_type shards')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('max(opensearch_cluster_shards_number{cluster="$cluster",type="$shard_type"})'),
  ])
  + panel.timeSeries.panelOptions.withRepeat('shard_type')
  + panel.timeSeries.panelOptions.withRepeatDirection('h')
  + { gridPos: { w: 4, h: 6 } };

// ==========================================
// Threadpools Row Panels
// ==========================================
local threadpoolTypePanel =
  panel.timeSeries.new('$pool_name tasks')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('max(opensearch_threadpool_tasks_number{cluster="$cluster",name="$pool_name"})'),
  ])
  + panel.timeSeries.panelOptions.withRepeat('pool_name')
  + panel.timeSeries.panelOptions.withRepeatDirection('h')
  + { gridPos: { w: 4, h: 6 } };

// ==========================================
// System Row Panels
// ==========================================
local systemCpuUsagePanel =
  panel.timeSeries.new('CPU usage')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_os_cpu_percent{cluster="$cluster", node=~"$node"}', '{{node}}'),
  ])
  + panel.timeSeries.standardOptions.withUnit('percent')
  + panel.timeSeries.standardOptions.withMin(0)
  + panel.timeSeries.standardOptions.withMax(100)
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

local systemMemoryUsagePanel =
  panel.timeSeries.new('Memory usage')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_os_mem_used_bytes{cluster="$cluster", node=~"$node"}', '{{node}}'),
  ])
  + panel.timeSeries.standardOptions.withUnit('bytes')
  + panel.timeSeries.standardOptions.withMin(0)
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

local systemDiskUsagePanel =
  panel.timeSeries.new('Disk usage')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('1 - opensearch_fs_path_available_bytes{cluster="$cluster",node=~"$node"} / opensearch_fs_path_total_bytes{cluster="$cluster",node=~"$node"}', '{{node}} - {{path}}'),
  ])
  + panel.timeSeries.standardOptions.withUnit('percentunit')
  + panel.timeSeries.standardOptions.withMin(0)
  + panel.timeSeries.standardOptions.withMax(1)
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + panel.timeSeries.fieldConfig.defaults.custom.withThresholdsStyle({ mode: 'area' })
  + panel.timeSeries.standardOptions.thresholds.withMode('absolute')
  + panel.timeSeries.standardOptions.thresholds.withSteps([
      { color: 'green', value: null },
      { color: 'yellow', value: 0.8 },
      { color: 'red', value: 0.9 },
  ])
  + { gridPos: { w: 8, h: 8 } };

// ==========================================
// Documents and Latencies Row Panels
// ==========================================
local documentsIndexingRatePanel =
  panel.timeSeries.new('Documents indexing rate')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_indexing_index_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local indexingLatencyPanel =
  panel.timeSeries.new('Indexing latency')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_indexing_index_time_seconds{cluster="$cluster", node=~"$node"}[$interval]) / rate(opensearch_indices_indexing_index_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local searchRatePanel =
  panel.timeSeries.new('Search rate')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_search_query_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local searchLatencyPanel =
  panel.timeSeries.new('Search latency')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_search_query_time_seconds{cluster="$cluster", node=~"$node"}[$interval]) / rate(opensearch_indices_search_query_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local documentsCountPanel =
  panel.timeSeries.new('Documents count (with replicas)')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_indices_doc_number{cluster="$cluster", node=~"$node"}', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + panel.timeSeries.fieldConfig.defaults.custom.withFillOpacity(30)
  + { gridPos: { w: 8, h: 8 } };

local documentsDeletingRatePanel =
  panel.timeSeries.new('Documents deleting rate')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_doc_deleted_number{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

local documentsMergingRatePanel =
  panel.timeSeries.new('Documents merging rate')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_merges_total_docs_count{cluster="$cluster",node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

// ==========================================
// Caches Row Panels
// ==========================================
local cacheFieldDataMemSizePanel =
  panel.timeSeries.new('Field data memory size')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_indices_fielddata_memory_size_bytes{cluster="$cluster", node=~"$node"}', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 12, h: 8 } };

local cacheFieldDataEvictionsPanel =
  panel.timeSeries.new('Field data evictions')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_fielddata_evictions_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 12, h: 8 } };

local cacheQuerySizePanel =
  panel.timeSeries.new('Query cache size')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_indices_querycache_cache_size_bytes{cluster="$cluster", node=~"$node"}', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local cacheQueryEvictionsPanel =
  panel.timeSeries.new('Query cache evictions')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_querycache_evictions_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local cacheQueryHitsPanel =
  panel.timeSeries.new('Query cache hits')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_querycache_hit_count{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

local cacheQueryMissesPanel =
  panel.timeSeries.new('Query cache misses')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_querycache_miss_number{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 6, h: 8 } };

// ==========================================
// Throttling Row Panels
// ==========================================
local throttlingIndexingPanel =
  panel.timeSeries.new('Indexing throttling')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_indexing_throttle_time_seconds{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 12, h: 8 } };

local throttlingMergingPanel =
  panel.timeSeries.new('Merging throttling')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_indices_merges_total_throttled_time_seconds{cluster="$cluster", node=~"$node"}[$interval])', '{{node}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 12, h: 8 } };

// ==========================================
// JVM Row Panels
// ==========================================
local jvmHeapUsedPanel =
  panel.timeSeries.new('Heap used')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('opensearch_jvm_mem_heap_used_bytes{cluster="$cluster", node=~"$node"}', '{{node}} - heap used'),
  ])
  + panel.timeSeries.standardOptions.withUnit('bytes')
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

local jvmGCCountPanel =
  panel.timeSeries.new('GC count')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_jvm_gc_collection_count{cluster="$cluster",node=~"$node"}[$interval])', '{{node}} - {{gc}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

local jvmGCTimePanel =
  panel.timeSeries.new('GC time')
  + timeSeriesBase
  + panel.timeSeries.queryOptions.withDatasource('prometheus', '$datasource')
  + panel.timeSeries.queryOptions.withTargets([
    promQuery('rate(opensearch_jvm_gc_collection_time_seconds{cluster="$cluster", node=~"$node"}[$interval])', '{{node}} - {{gc}}'),
  ])
  + panel.timeSeries.options.legend.withShowLegend(true)
  + panel.timeSeries.options.legend.withDisplayMode('table')
  + panel.timeSeries.options.legend.withCalcs(['mean', 'lastNotNull', 'max', 'min'])
  + { gridPos: { w: 8, h: 8 } };

// ==========================================
// Rows
// ==========================================
local clusterRow = panel.row.new('Cluster');
local shardsRow = panel.row.new('Shards');
local threadpoolsRow = panel.row.new('Threadpools');
local systemRow = panel.row.new('System');
local docsAndLatenciesRow = panel.row.new('Documents and Latencies');
local cachesRow = panel.row.new('Caches');
local throttlingRow = panel.row.new('Throttling');
local jvmRow = panel.row.new('JVM');

// ==========================================
// Dashboard
// ==========================================
{
  grafanaDashboards+:: {
    'opensearch.json':
      g.dashboard.new('OpenSearch')
      + g.dashboard.withUid('opensearch-mixin')
      + g.dashboard.withEditable(true)
      + g.dashboard.withVariables(variables)
      + g.dashboard.time.withFrom('now-3h')
      + g.dashboard.graphTooltip.withSharedCrosshair()
      + g.dashboard.withPanels(
        g.util.grid.wrapPanels([
          // Cluster row
          clusterRow,
          clusterStatusPanel,
          clusterHealthHistoryPanel,
          clusterNodesPanel,
          clusterDataNodesPanel,
          clusterPendingTasksPanel,

          // Shards row
          shardsRow,
          shardsTypePanel,

          // Threadpools row
          threadpoolsRow,
          threadpoolTypePanel,

          // System row
          systemRow,
          systemCpuUsagePanel,
          systemMemoryUsagePanel,
          systemDiskUsagePanel,

          // Documents and Latencies row
          docsAndLatenciesRow,
          documentsIndexingRatePanel,
          indexingLatencyPanel,
          searchRatePanel,
          searchLatencyPanel,
          documentsCountPanel,
          documentsDeletingRatePanel,
          documentsMergingRatePanel,

          // Caches row
          cachesRow,
          cacheFieldDataMemSizePanel,
          cacheFieldDataEvictionsPanel,
          cacheQuerySizePanel,
          cacheQueryEvictionsPanel,
          cacheQueryHitsPanel,
          cacheQueryMissesPanel,

          // Throttling row
          throttlingRow,
          throttlingIndexingPanel,
          throttlingMergingPanel,

          // JVM row
          jvmRow,
          jvmHeapUsedPanel,
          jvmGCCountPanel,
          jvmGCTimePanel,
        ], panelWidth=8, panelHeight=8)
      ),
  },
}
