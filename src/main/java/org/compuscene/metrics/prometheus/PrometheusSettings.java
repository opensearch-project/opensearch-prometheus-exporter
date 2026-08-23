/*
 * Copyright [2019] [Lukáš VLČEK]
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

package org.compuscene.metrics.prometheus;

import org.opensearch.action.support.IndicesOptions;
import org.opensearch.core.common.Strings;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;

/**
 * Dynamically updatable Prometheus exporter settings.
 * <p>
 * These settings are part of the cluster state available via
 * <pre>{@code
 * curl <opensearch>/_cluster/settings?include_defaults=true&filter_path=defaults.prometheus
 * }</pre>
 */
public class PrometheusSettings {
    /**
     * Enum for index name resolution and expansion options.
     */
    public enum INDEX_FILTER_OPTIONS {
        /** See {@link IndicesOptions#strictExpandOpen()}  */
        STRICT_EXPAND_OPEN,
        /** See {@link IndicesOptions#strictExpandOpenHidden()}  */
        STRICT_EXPAND_OPEN_HIDDEN,
        /** See {@link IndicesOptions#strictExpandOpenAndForbidClosed()}  */
        STRICT_EXPAND_OPEN_FORBID_CLOSED,
        /** See {@link IndicesOptions#strictExpandOpenHiddenAndForbidClosed()} */
        STRICT_EXPAND_OPEN_HIDDEN_FORBID_CLOSED,
        /** See {@link IndicesOptions#strictExpandOpenAndForbidClosedIgnoreThrottled()}  */
        STRICT_EXPAND_OPEN_FORBID_CLOSED_IGNORE_THROTTLED,
        /** See {@link IndicesOptions#strictExpand()} */
        STRICT_EXPAND_OPEN_CLOSED,
        /** See {@link IndicesOptions#strictExpandHidden()} */
        STRICT_EXPAND_OPEN_CLOSED_HIDDEN,
        /** See {@link IndicesOptions#strictSingleIndexNoExpandForbidClosed()} */
        STRICT_SINGLE_INDEX_NO_EXPAND_FORBID_CLOSED,
        /** See {@link IndicesOptions#lenientExpandOpen()} */
        LENIENT_EXPAND_OPEN,
        /** See {@link IndicesOptions#lenientExpandOpenHidden()} */
        LENIENT_EXPAND_OPEN_HIDDEN,
        /** See {@link IndicesOptions#lenientExpand()} */
        LENIENT_EXPAND_OPEN_CLOSED,
        /** See {@link IndicesOptions#lenientExpandHidden()} */
        LENIENT_EXPAND_OPEN_CLOSED_HIDDEN
    }

    static String PROMETHEUS_CLUSTER_SETTINGS_KEY = "prometheus.cluster.settings";
    static String PROMETHEUS_INDICES_KEY = "prometheus.indices";
    static String PROMETHEUS_NODES_FILTER_KEY = "prometheus.nodes.filter";
    static String PROMETHEUS_SELECTED_INDICES_KEY = "prometheus.indices_filter.selected_indices";
    static String PROMETHEUS_SELECTED_OPTION_KEY = "prometheus.indices_filter.selected_option";
    static String PROMETHEUS_SCRAPE_STEP_TIMEOUT_KEY = "prometheus.scrape.step_timeout";

    /**
     * This setting is used configure weather to expose cluster settings metrics or not. The default value is true.
     * Can be configured in opensearch.yml file or update dynamically under key {@link #PROMETHEUS_CLUSTER_SETTINGS_KEY}.
     */
    public static final Setting<Boolean> PROMETHEUS_CLUSTER_SETTINGS =
            Setting.boolSetting(PROMETHEUS_CLUSTER_SETTINGS_KEY, true,
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    /**
     * This setting is used configure weather to expose low level index metrics or not. The default value is true.
     * Can be configured in opensearch.yml file or update dynamically under key {@link #PROMETHEUS_INDICES_KEY}.
     */
    public static final Setting<Boolean> PROMETHEUS_INDICES =
            Setting.boolSetting(PROMETHEUS_INDICES_KEY, true,
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    /**
     * This setting is used configure which cluster nodes to gather metrics from. The default value is _local.
     * Can be configured in opensearch.yml file or update dynamically under key {@link #PROMETHEUS_NODES_FILTER_KEY}.
     */
    public static final Setting<String> PROMETHEUS_NODES_FILTER =
            Setting.simpleString(PROMETHEUS_NODES_FILTER_KEY, "_local",
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    /**
     * This setting is used configure to filter indices statistics from selected indices. The default value is "".
     * Can be configured in opensearch.yml file or update dynamically under key {@link #PROMETHEUS_SELECTED_INDICES_KEY}.
     */
    public static final Setting<String> PROMETHEUS_SELECTED_INDICES =
            Setting.simpleString(PROMETHEUS_SELECTED_INDICES_KEY, "",
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    /**
     * This setting determines an index name expansion mechanism for matching indices.
     * The default value is {@link INDEX_FILTER_OPTIONS#STRICT_EXPAND_OPEN_FORBID_CLOSED}.
     * Can be configured in <code>opensearch.yml</code> file or updated dynamically under key {@link #PROMETHEUS_SELECTED_OPTION_KEY}.
     * <p>
     * See {@link IndicesOptions} for more information.
     */
    public static final Setting<INDEX_FILTER_OPTIONS> PROMETHEUS_SELECTED_OPTION =
            new Setting<>(PROMETHEUS_SELECTED_OPTION_KEY,
                    String.valueOf(INDEX_FILTER_OPTIONS.STRICT_EXPAND_OPEN_FORBID_CLOSED),
                    INDEX_FILTER_OPTIONS::valueOf, Setting.Property.Dynamic, Setting.Property.NodeScope);

    /**
     * Per-step timeout applied to each individual request the exporter makes while gathering a
     * scrape (nodes stats, indices stats). Without a bound, a single non-responding node (e.g. one
     * stuck in D-state under disk failure) can stall the whole scrape indefinitely, since these
     * requests fan out to every node in the cluster. The default value is 5 seconds.
     * Can be configured in opensearch.yml file or updated dynamically under key {@link #PROMETHEUS_SCRAPE_STEP_TIMEOUT_KEY}.
     */
    public static final Setting<TimeValue> PROMETHEUS_SCRAPE_STEP_TIMEOUT =
            Setting.positiveTimeSetting(PROMETHEUS_SCRAPE_STEP_TIMEOUT_KEY, TimeValue.timeValueSeconds(5),
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    private volatile boolean clusterSettings;
    private volatile boolean indices;
    private volatile String nodesFilter;
    private volatile String selectedIndices;
    private volatile INDEX_FILTER_OPTIONS selectedOption;
    private volatile TimeValue scrapeStepTimeout;

    /**
     * A constructor.
     * @param settings Settings
     * @param clusterSettings Cluster settings
     */
    public PrometheusSettings(Settings settings, ClusterSettings clusterSettings) {
        setPrometheusClusterSettings(PROMETHEUS_CLUSTER_SETTINGS.get(settings));
        setPrometheusIndices(PROMETHEUS_INDICES.get(settings));
        setPrometheusNodesFilter(PROMETHEUS_NODES_FILTER.get(settings));
        setPrometheusSelectedIndices(PROMETHEUS_SELECTED_INDICES.get(settings));
        setPrometheusSelectedOption(PROMETHEUS_SELECTED_OPTION.get(settings));
        setScrapeStepTimeout(PROMETHEUS_SCRAPE_STEP_TIMEOUT.get(settings));
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_CLUSTER_SETTINGS, this::setPrometheusClusterSettings);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_INDICES, this::setPrometheusIndices);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_NODES_FILTER, this::setPrometheusNodesFilter);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_SELECTED_INDICES, this::setPrometheusSelectedIndices);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_SELECTED_OPTION, this::setPrometheusSelectedOption);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_SCRAPE_STEP_TIMEOUT, this::setScrapeStepTimeout);
    }

    private void setPrometheusClusterSettings(boolean flag) {
        this.clusterSettings = flag;
    }

    private void setPrometheusIndices(boolean flag) {
        this.indices = flag;
    }

    private void setPrometheusNodesFilter(String filter) { this.nodesFilter = filter; }

    private void setPrometheusSelectedIndices(String selectedIndices) {
        this.selectedIndices = selectedIndices;
    }

    private void setPrometheusSelectedOption(INDEX_FILTER_OPTIONS selectedOption) {
        this.selectedOption = selectedOption;
    }

    private void setScrapeStepTimeout(TimeValue timeout) {
        this.scrapeStepTimeout = timeout;
    }

    /**
     * Get value of settings key {@link #PROMETHEUS_CLUSTER_SETTINGS_KEY}.
     * @return boolean value of the key
     */
    public boolean getPrometheusClusterSettings() {
        return this.clusterSettings;
    }

    /**
     * Get value of settings key {@link #PROMETHEUS_INDICES_KEY}.
     * @return boolean value of the key
     */
    public boolean getPrometheusIndices() {
        return this.indices;
    }

    /**
     * Get value of settings key {@link #PROMETHEUS_NODES_FILTER_KEY}.
     * @return boolean value of the key
     */
    public String getNodesFilter() { return this.nodesFilter; }

    /**
     * Get value of settings key {@link #PROMETHEUS_SELECTED_INDICES_KEY}.
     * @return string value of the key
     */
    public String[] getPrometheusSelectedIndices() {
        return Strings.splitStringByCommaToArray(this.selectedIndices);
    }

    /**
     * Get value of settings key {@link #PROMETHEUS_SCRAPE_STEP_TIMEOUT_KEY}.
     * @return TimeValue of the key
     */
    public TimeValue getScrapeStepTimeout() {
        return this.scrapeStepTimeout;
    }

    /**
     * Get IndicesOptions of settings key {@link #PROMETHEUS_SELECTED_OPTION_KEY}.
     * @return IndicesOptions of the key
     */
    public IndicesOptions getIndicesOptions() {
        IndicesOptions indicesOptions = null;
        switch (this.selectedOption) {
            case STRICT_EXPAND_OPEN:
                indicesOptions = IndicesOptions.strictExpandOpen();
                break;
            case STRICT_EXPAND_OPEN_HIDDEN:
                indicesOptions = IndicesOptions.strictExpandOpenHidden();
                break;
            case STRICT_EXPAND_OPEN_FORBID_CLOSED:
                indicesOptions = IndicesOptions.strictExpandOpenAndForbidClosed();
                break;
            case STRICT_EXPAND_OPEN_HIDDEN_FORBID_CLOSED:
                indicesOptions = IndicesOptions.strictExpandOpenHiddenAndForbidClosed();
                break;
            case STRICT_EXPAND_OPEN_FORBID_CLOSED_IGNORE_THROTTLED:
                indicesOptions = IndicesOptions.strictExpandOpenAndForbidClosedIgnoreThrottled();
                break;
            case STRICT_EXPAND_OPEN_CLOSED:
                indicesOptions = IndicesOptions.strictExpand();
                break;
            case STRICT_EXPAND_OPEN_CLOSED_HIDDEN:
                indicesOptions = IndicesOptions.strictExpandHidden();
                break;
            case STRICT_SINGLE_INDEX_NO_EXPAND_FORBID_CLOSED:
                indicesOptions = IndicesOptions.strictSingleIndexNoExpandForbidClosed();
                break;
            case LENIENT_EXPAND_OPEN:
                indicesOptions = IndicesOptions.lenientExpandOpen();
                break;
            case LENIENT_EXPAND_OPEN_HIDDEN:
                indicesOptions = IndicesOptions.lenientExpandOpenHidden();
                break;
            case LENIENT_EXPAND_OPEN_CLOSED:
                indicesOptions = IndicesOptions.lenientExpand();
                break;
            case LENIENT_EXPAND_OPEN_CLOSED_HIDDEN:
                indicesOptions = IndicesOptions.lenientExpandHidden();
                break;
        }
        return indicesOptions;
    }
}
