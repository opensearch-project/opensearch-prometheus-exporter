{
  _config+:: {

    // Cluster health alerts
    // =====================
    //
    // Cluster health can become RED by natural cause or by critical cause.
    //
    // The natural causes do not last long and they include:
    //   - a new index is created and all its primary shards haven’t been allocated yet
    //   - a master node has not been elected yet (for example master node dies and a new master node hasn’t been
    //     elected yet, and this can take some time if the cluster load is high...)
    //
    // If the natural cause takes long or the cause is different than those listed above then it is considered
    // to be a critical cause.
    //
    // Cluster health becomes YELLOW if not all index replica shards are allocated. If the goal is to have GREEN cluster
    // health (ie number of replica shards is configured accordingly) but the status stays YELLOW for too long then this
    // is considered serious.
    oseClusterHealthStatusRED: '2m',
    oseClusterHealthStatusYELLOW: '20m',

    // Bulk Requests Rejections
    // ========================
    //
    // Sudden spikes (increases) in number of rejected bulk requests is considered serious. It means the node can not keep
    // up with incoming bulk indexing requests pace.
    //
    // Check https://docs.google.com/presentation/d/1X1rKozAUuF2MVc1YXElFWq9wkcWv3Axdldl8LOH9Vik/edit#slide=id.gb41e27854_0_27
    //
    // Increase (in percent) of bulk requests rejected:
    oseBulkPctIncrease: 5,

    // Disk Usage
    // ==========
    //
    // There are two disk capacity saturation thresholds that impact how index shards are allocated on each OSE node.
    //   - Low watermark  - 85% used capacity (default)
    //   - High watermark - 90% used capacity (default)
    //
    // Disk allocation thresholds (low, high watermarks) can be changed using REST API:
    // https://opensearch.org/docs/latest/opensearch/popular-api/#change-disk-watermarks-or-other-cluster-settings
    oseDiskLowWaterMark: 85,
    oseDiskHighWaterMark: 90,

    // There needs to be enough free disk space for optimal background segments merges.
    //
    // Ratio of free disk space and total sum of segments size per node.
    // Examples:
    //     1 = Remaining free space is the same as total sum of segments size
    //   0.5 = Remaining free space is half of the total sum of segments size
    oseDiskSpaceRatioForMerges: 0.5,

    // JVM Heap Usage
    // ==============
    //
    // OSE is by default configured to start heavy GC when JVM heap usage crosses 75%. Thus, if OSE is using more
    // that 75% JVM heap for a longer period of time we should check why it is not able to free the memory.
    //
    // This is ensured by use of -XX:CMSInitiatingOccupancyFraction=75 and -XX:+UseCMSInitiatingOccupancyOnly
    // in `distribution/src/config/jvm.options` in OSE source code. Notice that this config is relevant as long as JVM
    // is using CMS. Once G1 is used this may change.
    oseJvmHeapUseThreshold: 75,

    // CPU Usage
    // ==============
    //
    // High CPU usage for longer period may signal capacity problem.
    // These alerts might be already monitored at different level (i.e. system monitoring).
    oseSystemCPUHigh: 90,
    oseProcessCPUHigh: 90,

  },
}
