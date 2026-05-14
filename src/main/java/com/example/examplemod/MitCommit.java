package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class MitCommit {
    public final String id;
    public final String message;
    public final String timestamp;
    public final Map<BlockPos, BlockState> blocks;

    /** Used when creating a new commit (timestamp = now). */
    public MitCommit(String id, String message, Map<BlockPos, BlockState> blocks) {
        this.id = id;
        this.message = message;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.blocks = new HashMap<>(blocks);
    }

    /** Used when loading a commit from disk (timestamp already known). */
    public MitCommit(String id, String message, String timestamp, Map<BlockPos, BlockState> blocks) {
        this.id = id;
        this.message = message;
        this.timestamp = timestamp;
        this.blocks = new HashMap<>(blocks);
    }
}
