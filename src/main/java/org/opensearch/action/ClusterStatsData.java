/*
 * Copyright [2018] [Vincent VAN HOLLEBEKE]
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
package org.opensearch.action;

import static org.opensearch.cluster.routing.allocation.DiskThresholdSettings.*;

import org.opensearch.OpenSearchParseException;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.common.Nullable;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsException;
import org.opensearch.common.unit.RatioValue;

import java.io.IOException;


/**
 * Selected settings from OpenSearch cluster settings.
 * <p>
 * Disk-based shard allocation [1] settings play an important role in how OpenSearch decides where to allocate
 * new shards or if existing shards are relocated to different nodes. The tricky part about these settings is
 * that they can be expressed either in percent or bytes value (they cannot be mixed), and they can be updated
 * on the fly [2].
 * <ul>
 *   <li> [1] <a href="https://docs.opensearch.org/latest/install-and-configure/configuring-opensearch/cluster-settings/">Cluster Settings ("cluster.routing.allocation.disk.watermark.*")</a></li>
 *   <li> [2] <a href="https://docs.opensearch.org/latest/api-reference/popular-api/#change-disk-watermarks-or-other-cluster-settings">Change disk watermark</a></li>
 * </ul>
 * <p>
 * To make it easy for Prometheus to consume the data, we expose these settings in both formats (pct and bytes)
 * and we do our best in determining if they are currently set as pct or bytes filling appropriate variables
 * with data or null value.
 */
public class ClusterStatsData extends ActionResponse {

    @Nullable private final Boolean thresholdEnabled;

    @Nullable private final Long diskLowInBytes;
    @Nullable private final Long diskHighInBytes;
    @Nullable private final Long floodStageInBytes;

    @Nullable private final Double diskLowInPct;
    @Nullable private final Double diskHighInPct;
    @Nullable private final Double floodStageInPct;

    /**
     * A constructor.
     * @param in A {@link StreamInput} to materialize the instance from
     * @throws IOException if reading from {@link StreamInput} is not successful
     */
    public ClusterStatsData(StreamInput in) throws IOException {
        super(in);
        thresholdEnabled = in.readOptionalBoolean();
        //
        diskLowInBytes = in.readOptionalLong();
        diskHighInBytes = in.readOptionalLong();
        floodStageInBytes = in.readOptionalLong();
        //
        diskLowInPct = in.readOptionalDouble();
        diskHighInPct = in.readOptionalDouble();
        floodStageInPct = in.readOptionalDouble();
    }

    @SuppressWarnings({"checkstyle:LineLength"})
    ClusterStatsData(ClusterStateResponse clusterStateResponse, Settings settings, ClusterSettings clusterSettings) {

        Metadata metadata = clusterStateResponse.getState().getMetadata();

        Boolean resolvedThresholdEnabled = null;
        Long resolvedDiskLowInBytes = null;
        Long resolvedDiskHighInBytes = null;
        Long resolvedFloodStageInBytes = null;
        Double resolvedDiskLowInPct = null;
        Double resolvedDiskHighInPct = null;
        Double resolvedFloodStageInPct = null;

        // There are several layers of cluster settings in OpenSearch each having different priority.
        // We need to traverse them from the top priority down to find relevant value of each setting.
        // See https://docs.opensearch.org/latest/install-and-configure/configuring-opensearch/index/#updating-cluster-settings-using-the-api
        for (Settings currentSettings : new Settings[]{
                metadata.transientSettings(),
                metadata.persistentSettings(),
                clusterSettings.diff(metadata.settings(), settings)
        }) {
            if (resolvedThresholdEnabled == null) {
                resolvedThresholdEnabled = currentSettings.getAsBoolean(
                        CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), null);
            }

            LongValue parsedLow = parseWatermarkValue(
                    currentSettings,
                    CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey());
            resolvedDiskLowInBytes = firstNonNull(resolvedDiskLowInBytes, parsedLow.bytes());
            resolvedDiskLowInPct = firstNonNull(resolvedDiskLowInPct, parsedLow.pct());

            LongValue parsedHigh = parseWatermarkValue(
                    currentSettings,
                    CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey());
            resolvedDiskHighInBytes = firstNonNull(resolvedDiskHighInBytes, parsedHigh.bytes());
            resolvedDiskHighInPct = firstNonNull(resolvedDiskHighInPct, parsedHigh.pct());

            LongValue parsedFlood = parseWatermarkValue(
                    currentSettings,
                    CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey());
            resolvedFloodStageInBytes = firstNonNull(resolvedFloodStageInBytes, parsedFlood.bytes());
            resolvedFloodStageInPct = firstNonNull(resolvedFloodStageInPct, parsedFlood.pct());
        }

        thresholdEnabled = resolvedThresholdEnabled;
        diskLowInBytes = resolvedDiskLowInBytes;
        diskHighInBytes = resolvedDiskHighInBytes;
        floodStageInBytes = resolvedFloodStageInBytes;
        diskLowInPct = resolvedDiskLowInPct;
        diskHighInPct = resolvedDiskHighInPct;
        floodStageInPct = resolvedFloodStageInPct;
    }

    private record LongValue(@Nullable Long bytes, @Nullable Double pct) {

        private static LongValue empty() {
            return new LongValue(null, null);
        }
    }

    private LongValue parseWatermarkValue(Settings settings, String key) {
        String value = settings.get(key);
        if (value == null) {
            return LongValue.empty();
        }

        try {
            Double pct = RatioValue.parseRatioValue(value).getAsPercent();
            return new LongValue(null, pct);
        } catch (SettingsException | OpenSearchParseException | NullPointerException ignored) {
            try {
                Long bytes = settings.getAsBytesSize(key, null).getBytes();
                return new LongValue(bytes, null);
            } catch (SettingsException | OpenSearchParseException | NullPointerException ignoredAgain) {
                return LongValue.empty();
            }
        }
    }

    private static <T> T firstNonNull(@Nullable T current, @Nullable T candidate) {
        return current != null ? current : candidate;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(thresholdEnabled);
        //
        out.writeOptionalLong(diskLowInBytes);
        out.writeOptionalLong(diskHighInBytes);
        out.writeOptionalLong(floodStageInBytes);
        //
        out.writeOptionalDouble(diskLowInPct);
        out.writeOptionalDouble(diskHighInPct);
        out.writeOptionalDouble(floodStageInPct);
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING}.
     * @return A Boolean value of the setting.
     */
    @Nullable
    public Boolean getThresholdEnabled() {
        return thresholdEnabled;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING}.
     * @return A Long value of the setting.
     */
    @Nullable
    public Long getDiskLowInBytes() {
        return diskLowInBytes;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING}.
     * @return A Long value of the setting.
     */
    @Nullable
    public Long getDiskHighInBytes() {
        return diskHighInBytes;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING}.
     * @return A Long value of the setting.
     */
    @Nullable
    public Long getFloodStageInBytes() {
        return floodStageInBytes;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING}.
     * @return A Double value of the setting.
     */
    @Nullable
    public Double getDiskLowInPct() {
        return diskLowInPct;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING}.
     * @return A Double value of the setting.
     */
    @Nullable
    public Double getDiskHighInPct() {
        return diskHighInPct;
    }

    /**
     * Get value of setting controlled by {@link org.opensearch.cluster.routing.allocation.DiskThresholdSettings#CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING}.
     * @return A Double value of the setting.
     */
    @Nullable
    public Double getFloodStageInPct() {
        return floodStageInPct;
    }
}
