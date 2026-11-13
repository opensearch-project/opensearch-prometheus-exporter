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
 * Index level blocks (such as the {@code read_only_allow_delete} block set when a node exceeds the flood stage
 * disk watermark) extracted from the cluster state. Only the blocks listed in {@link #TRACKED_BLOCKS} are kept.
 */
public class IndexBlocksData implements Writeable {

    /**
     * Index blocks exposed as metrics, keyed by the block name used as the metric label value.
     */
    public static final Map<String, ClusterBlock> TRACKED_BLOCKS;

    static {
        Map<String, ClusterBlock> blocks = new LinkedHashMap<>();
        blocks.put("read_only_allow_delete", IndexMetadata.INDEX_READ_ONLY_ALLOW_DELETE_BLOCK);
        blocks.put("read_only", IndexMetadata.INDEX_READ_ONLY_BLOCK);
        blocks.put("write", IndexMetadata.INDEX_WRITE_BLOCK);
        TRACKED_BLOCKS = Collections.unmodifiableMap(blocks);
    }

    // index name -> names of tracked blocks set on the index; indices without any tracked block are omitted
    private final Map<String, Set<String>> blocksByIndex;

    /**
     * Extracts the tracked index blocks from the cluster state blocks.
     * @param clusterBlocks {@link ClusterBlocks} from the cluster state
     */
    public IndexBlocksData(ClusterBlocks clusterBlocks) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String index : clusterBlocks.indices().keySet()) {
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, ClusterBlock> tracked : TRACKED_BLOCKS.entrySet()) {
                if (clusterBlocks.hasIndexBlock(index, tracked.getValue())) {
                    names.add(tracked.getKey());
                }
            }
            if (names.isEmpty() == false) {
                result.put(index, names);
            }
        }
        this.blocksByIndex = result;
    }

    /**
     * A constructor that materializes the instance from {@link StreamInput}.
     * @param in A {@link StreamInput} to materialize the instance from
     * @throws IOException if reading from {@link StreamInput} is not successful
     */
    public IndexBlocksData(StreamInput in) throws IOException {
        this.blocksByIndex = in.readMap(StreamInput::readString, i -> new HashSet<>(i.readStringList()));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(blocksByIndex, StreamOutput::writeString, StreamOutput::writeStringCollection);
    }

    /**
     * Whether the given tracked block is set on the index.
     * @param index index name
     * @param block block name, one of the keys of {@link #TRACKED_BLOCKS}
     * @return true if the block is set
     */
    public boolean hasBlock(String index, String block) {
        Set<String> names = blocksByIndex.get(index);
        return names != null && names.contains(block);
    }
}
