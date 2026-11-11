/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Duration and outcome of each request the exporter sends to OpenSearch while gathering one scrape.
 * Steps that are disabled by settings are not recorded.
 */
public class ScrapeStats implements Writeable {

    /** Cluster health request (local node). */
    public static final String CLUSTER_HEALTH = "cluster_health";
    /** Nodes info request for the local node. */
    public static final String NODES_INFO = "nodes_info";
    /** Nodes stats request (nodes selected by the nodes filter). */
    public static final String NODES_STATS = "nodes_stats";
    /** Indices stats request (all nodes). */
    public static final String INDICES_STATS = "indices_stats";
    /** Cluster state request used for cluster settings metrics (cluster manager node). */
    public static final String CLUSTER_SETTINGS = "cluster_settings";

    /**
     * Outcome of one step.
     * @param durationNanos time from sending the request until its response or failure, in nanoseconds
     * @param success whether the request succeeded
     */
    public record Step(long durationNanos, boolean success) {}

    private final Map<String, Step> steps = new LinkedHashMap<>();

    /** Creates empty stats. */
    public ScrapeStats() {}

    /**
     * Materializes the instance from {@link StreamInput}.
     * @param in input stream
     * @throws IOException if there is an exception reading from {@link StreamInput}
     */
    public ScrapeStats(StreamInput in) throws IOException {
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            steps.put(in.readString(), new Step(in.readVLong(), in.readBoolean()));
        }
    }

    /**
     * Record the outcome of a step.
     * @param step step name, one of the constants of this class
     * @param durationNanos duration in nanoseconds
     * @param success whether the step succeeded
     */
    public void record(String step, long durationNanos, boolean success) {
        steps.put(step, new Step(Math.max(0, durationNanos), success));
    }

    /**
     * Get the recorded steps, in the order they were executed.
     * @return unmodifiable map of step name to its outcome
     */
    public Map<String, Step> getSteps() {
        return Collections.unmodifiableMap(steps);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(steps.size());
        for (Map.Entry<String, Step> entry : steps.entrySet()) {
            out.writeString(entry.getKey());
            out.writeVLong(entry.getValue().durationNanos());
            out.writeBoolean(entry.getValue().success());
        }
    }

    @Override
    public String toString() {
        return "ScrapeStats" + steps;
    }
}
