package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
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

    // Block change stats vs the previous commit
    public final int blocksPlaced;
    public final int blocksBroken;

    // Player state at commit time
    public final double playerX, playerY, playerZ;
    public final float  playerYaw, playerPitch;
    public final ListTag inventoryNbt;  // serialized player inventory

    /** Used when creating a brand-new commit (timestamp = now). */
    public MitCommit(String id, String message, Map<BlockPos, BlockState> blocks,
                     int placed, int broken,
                     double px, double py, double pz, float yaw, float pitch,
                     ListTag inventoryNbt) {
        this.id            = id;
        this.message       = message;
        this.timestamp     = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.blocks        = new HashMap<>(blocks);
        this.blocksPlaced  = placed;
        this.blocksBroken  = broken;
        this.playerX       = px;
        this.playerY       = py;
        this.playerZ       = pz;
        this.playerYaw     = yaw;
        this.playerPitch   = pitch;
        this.inventoryNbt  = inventoryNbt;
    }

    /** Used when loading from disk (timestamp already known). */
    public MitCommit(String id, String message, String timestamp, Map<BlockPos, BlockState> blocks,
                     int placed, int broken,
                     double px, double py, double pz, float yaw, float pitch,
                     ListTag inventoryNbt) {
        this.id            = id;
        this.message       = message;
        this.timestamp     = timestamp;
        this.blocks        = new HashMap<>(blocks);
        this.blocksPlaced  = placed;
        this.blocksBroken  = broken;
        this.playerX       = px;
        this.playerY       = py;
        this.playerZ       = pz;
        this.playerYaw     = yaw;
        this.playerPitch   = pitch;
        this.inventoryNbt  = inventoryNbt;
    }
}
