package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles saving and loading the MitRepository to/from
 * <world_folder>/mit_repo.dat using Minecraft's NBT format.
 */
public class MitStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LevelResource SAVE_PATH = new LevelResource("mit_repo.dat");

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    public static void save(MinecraftServer server) {
        MitRepository repo = MitRepository.getInstance();
        if (!repo.isInitialized()) return;

        CompoundTag root = new CompoundTag();

        // Region corners
        BlockPos c1 = repo.getCorner1(), c2 = repo.getCorner2();
        root.putIntArray("corner1", new int[]{c1.getX(), c1.getY(), c1.getZ()});
        root.putIntArray("corner2", new int[]{c2.getX(), c2.getY(), c2.getZ()});
        root.putInt("headIndex", repo.getHeadIndex());

        // Commits
        ListTag commitList = new ListTag();
        for (MitCommit commit : repo.getLog()) {
            CompoundTag ct = new CompoundTag();
            ct.putString("id",        commit.id);
            ct.putString("message",   commit.message);
            ct.putString("timestamp", commit.timestamp);

            // Blocks
            ListTag blockList = new ListTag();
            for (Map.Entry<BlockPos, BlockState> e : commit.blocks.entrySet()) {
                CompoundTag bt = new CompoundTag();
                bt.putInt("x", e.getKey().getX());
                bt.putInt("y", e.getKey().getY());
                bt.putInt("z", e.getKey().getZ());
                bt.put("state", NbtUtils.writeBlockState(e.getValue()));
                blockList.add(bt);
            }
            ct.put("blocks", blockList);
            commitList.add(ct);
        }
        root.put("commits", commitList);

        // Write to disk
        Path path = server.getWorldPath(SAVE_PATH);
        try {
            NbtIo.writeCompressed(root, path);
            LOGGER.info("[MIT] Saved {} commit(s) to {}", repo.getLog().size(), path);
        } catch (IOException e) {
            LOGGER.error("[MIT] Failed to save repository: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public static void load(MinecraftServer server) {
        MitRepository.reset();
        MitRepository repo = MitRepository.getInstance();

        Path path = server.getWorldPath(SAVE_PATH);
        if (!Files.exists(path)) {
            LOGGER.info("[MIT] No saved repository found. Start with /mit init.");
            return;
        }

        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            LOGGER.error("[MIT] Failed to load repository: {}", e.getMessage());
            return;
        }

        // Restore region
        if (!root.contains("corner1") || !root.contains("corner2")) return;
        int[] c1 = root.getIntArray("corner1");
        int[] c2 = root.getIntArray("corner2");
        repo.initFromStorage(new BlockPos(c1[0], c1[1], c1[2]), new BlockPos(c2[0], c2[1], c2[2]));

        // Block registry for reading BlockStates
        HolderGetter<Block> blockGetter = server.overworld().holderLookup(Registries.BLOCK);

        // Restore commits
        ListTag commitList = root.getList("commits", Tag.TAG_COMPOUND);
        List<MitCommit> loaded = new ArrayList<>();
        for (int i = 0; i < commitList.size(); i++) {
            CompoundTag ct    = commitList.getCompound(i);
            String id         = ct.getString("id");
            String message    = ct.getString("message");
            String timestamp  = ct.getString("timestamp");

            ListTag blockList = ct.getList("blocks", Tag.TAG_COMPOUND);
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            for (int j = 0; j < blockList.size(); j++) {
                CompoundTag bt = blockList.getCompound(j);
                BlockPos pos   = new BlockPos(bt.getInt("x"), bt.getInt("y"), bt.getInt("z"));
                BlockState state = NbtUtils.readBlockState(blockGetter, bt.getCompound("state"));
                blocks.put(pos, state);
            }
            loaded.add(new MitCommit(id, message, timestamp, blocks));
        }

        repo.loadCommits(loaded, root.getInt("headIndex"));
        LOGGER.info("[MIT] Loaded {} commit(s) from disk.", loaded.size());
    }
}
