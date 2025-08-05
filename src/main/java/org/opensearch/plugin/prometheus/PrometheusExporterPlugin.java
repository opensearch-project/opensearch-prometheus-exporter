/*
 * Copyright [2016] [Vincent VAN HOLLEBEKE]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.opensearch.plugin.prometheus;

import static java.util.Collections.singletonList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.compuscene.metrics.prometheus.PrometheusSettings;
import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.action.NodePrometheusMetricsAction;
import org.opensearch.action.TransportNodePrometheusMetricsAction;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.*;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.prometheus.RestPrometheusMetricsAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Prometheus Exporter plugin main class.
 */
public class PrometheusExporterPlugin extends Plugin implements ActionPlugin {
    private static final Logger logger = LogManager.getLogger(PrometheusExporterPlugin.class);

    /**
     * A constructor.
     */
    public PrometheusExporterPlugin() {
        logger.info("starting Prometheus exporter plugin");
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return singletonList(
                new ActionHandler<>(NodePrometheusMetricsAction.INSTANCE, TransportNodePrometheusMetricsAction.class)
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController, ClusterSettings clusterSettings,
                                             IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        return singletonList(
                new RestPrometheusMetricsAction(settings, clusterSettings)
        );
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = Arrays.asList(
                PrometheusSettings.PROMETHEUS_CLUSTER_SETTINGS,
                PrometheusSettings.PROMETHEUS_INDICES,
                PrometheusSettings.PROMETHEUS_SNAPSHOTS,
                PrometheusSettings.PROMETHEUS_NODES_FILTER,
                PrometheusSettings.PROMETHEUS_SELECTED_INDICES,
                PrometheusSettings.PROMETHEUS_SELECTED_OPTION,
                RestPrometheusMetricsAction.METRIC_PREFIX
        );
        return Collections.unmodifiableList(settings);
    }
}
