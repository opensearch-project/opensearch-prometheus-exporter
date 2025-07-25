// Define recording rules that are used in alerts
{
  prometheusRules+:: {
    groups+: [
      {
        name: 'opensearch.rules',
        rules: [
          {
            record: 'bulk:rejected_requests:rate2m',
            expr: |||
              rate(opensearch_threadpool_threads_count{name="bulk", type="rejected"}[2m])
            |||,
          },
          {
            record: 'bulk:completed_requests:rate2m',
            expr: |||
              rate(opensearch_threadpool_threads_count{name="bulk", type="completed"}[2m])
            |||,
          },
          {
            // If there are no bulk rejections then we get 0/0 which is NaN. Although this might seem counterintuitive
            // it is in fact valid result wrt to Prometheus and a good practice also, see:
            // https://stackoverflow.com/questions/47056557/how-to-gracefully-avoid-divide-by-zero-in-prometheus
            record: 'bulk:reject_ratio:rate2m',
            expr: |||
              sum by (cluster, instance, node) (bulk:rejected_requests:rate2m) / on (cluster, instance, node) (bulk:completed_requests:rate2m)
            |||,
          },
        ],
      },
    ],
  },
}
