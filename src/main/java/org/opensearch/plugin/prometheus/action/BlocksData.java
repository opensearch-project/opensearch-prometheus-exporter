/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.prometheus.action;

import org.opensearch.cluster.block.ClusterBlock;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cluster level and index level blocks extracted from the cluster state, such as the {@code read_only_allow_delete}
 * index block set when a node exceeds the flood stage disk watermark. Only the blocks listed in
 * {@link #TRACKED_CLUSTER_BLOCKS} and {@link #TRACKED_INDEX_BLOCKS} are kept.
 */
public class BlocksData implements Writeable {

    /**
     * Cluster level blocks exposed as metrics, keyed by the block name used as the metric label value.
     * These are the blocks controlled by the {@code cluster.blocks.*} settings.
     */
    public static final Map<String, ClusterBlock> TRACKED_CLUSTER_BLOCKS;

    /**
     * Index level blocks exposed as metrics, keyed by the block name used as the metric label value.
     * These are the blocks controlled by the {@code index.blocks.*} settings.
     */
    public static final Map<String, ClusterBlock> TRACKED_INDEX_BLOCKS;

    static {
        Map<String, ClusterBlock> cluster = new LinkedHashMap<>();
        cluster.put("read_only", Metadata.CLUSTER_READ_ONLY_BLOCK);
        cluster.put("read_only_allow_delete", Metadata.CLUSTER_READ_ONLY_ALLOW_DELETE_BLOCK);
        cluster.put("create_index", Metadata.CLUSTER_CREATE_INDEX_BLOCK);
        TRACKED_CLUSTER_BLOCKS = Collections.unmodifiableMap(cluster);

        Map<String, ClusterBlock> index = new LinkedHashMap<>();
        index.put("read_only_allow_delete", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK);
        index.put("read_only", IndexMetadata.INDEX_READ_ONLY_BLOCK);
        index.put("write", IndexMetadata.INDEX_WRITE_BLOCK);
        index.put("read", IndexMetadata.INDEX_READ_BLOCK);
        index.put("metadata", IndexMetadata.INDEX_METADATA_BLOCK);
        TRACKED_INDEX_BLOCKS = Collections.unmodifiableMap(index);
    }

    // names of tracked cluster blocks that are set
    private final Set<String> clusterBlocks;
    // index name -> names of tracked blocks set on the index; indices without any tracked block are omitted
    private final Map<String, Set<String>> indexBlocks;

    /**
     * Extracts the tracked blocks from the cluster state blocks.
     * @param blocks {@link ClusterBlocks} from the cluster state
     */
    public BlocksData(ClusterBlocks blocks) {
        Set<String> cluster = new HashSet<>();
        for (Map.Entry<String, ClusterBlock> tracked : TRACKED_CLUSTER_BLOCKS.entrySet()) {
            if (blocks.hasGlobalBlock(tracked.getValue())) {
                cluster.add(tracked.getKey());
            }
        }
        Map<String, Set<String>> indices = new HashMap<>();
        for (String index : blocks.indices().keySet()) {
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, ClusterBlock> tracked : TRACKED_INDEX_BLOCKS.entrySet()) {
                if (blocks.hasIndexBlock(index, tracked.getValue())) {
                    names.add(tracked.getKey());
                }
            }
            if (names.isEmpty() == false) {
                indices.put(index, names);
            }
        }
        this.clusterBlocks = cluster;
        this.indexBlocks = indices;
    }

    /**
     * A constructor that materializes the instance from {@link StreamInput}.
     * @param in A {@link StreamInput} to materialize the instance from
     * @throws IOException if reading from {@link StreamInput} is not successful
     */
    public BlocksData(StreamInput in) throws IOException {
        this.clusterBlocks = new HashSet<>(in.readStringList());
        this.indexBlocks = in.readMap(StreamInput::readString, i -> new HashSet<>(i.readStringList()));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeStringCollection(clusterBlocks);
        out.writeMap(indexBlocks, StreamOutput::writeString, StreamOutput::writeStringCollection);
    }

    /**
     * Whether the given tracked cluster block is set.
     * @param block block name, one of the keys of {@link #TRACKED_CLUSTER_BLOCKS}
     * @return true if the block is set
     */
    public boolean hasClusterBlock(String block) {
        return clusterBlocks.contains(block);
    }

    /**
     * Whether the given tracked block is set on the index.
     * @param index index name
     * @param block block name, one of the keys of {@link #TRACKED_INDEX_BLOCKS}
     * @return true if the block is set
     */
    public boolean hasIndexBlock(String index, String block) {
        Set<String> names = indexBlocks.get(index);
        return names != null && names.contains(block);
    }
}
