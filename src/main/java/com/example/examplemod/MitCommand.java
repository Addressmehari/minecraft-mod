package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MitCommand {

    // How far around the player's feet the init region extends
    private static final int RADIUS = 30;

    // Suggestion provider: shows all commit IDs + messages in tab-complete
    private static final SuggestionProvider<CommandSourceStack> COMMIT_SUGGESTIONS =
        (context, builder) -> {
            List<MitCommit> commits = MitRepository.getInstance().getLog();
            for (MitCommit commit : commits) {
                // Each suggestion = the ID, tooltip = the message
                builder.suggest(commit.id, Component.literal("\"" + commit.message + "\"  " + commit.timestamp));
            }
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("mit")

            // /mit
            .executes(ctx -> {
                sendInfo(ctx.getSource(), "§a[MIT] Git for Minecraft! Commands:");
                sendInfo(ctx.getSource(), "§7  /mit init");
                sendInfo(ctx.getSource(), "§7  /mit commit \"message\"");
                sendInfo(ctx.getSource(), "§7  /mit commit \"message\" --overwrite");
                sendInfo(ctx.getSource(), "§7  /mit checkout <id>");
                sendInfo(ctx.getSource(), "§7  /mit log");
                return 1;
            })

            // /mit init
            .then(Commands.literal("init")
                .executes(ctx -> {
                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                    MitRepository.getInstance().init(pos, RADIUS);
                    MitStorage.save(ctx.getSource().getServer());  // persist immediately
                    sendSuccess(ctx.getSource(),
                        "§a[MIT] Initialized! Tracking §e" + (RADIUS * 2 + 1) + "x" + (RADIUS * 2 + 1) + "x" + (RADIUS * 2 + 1)
                        + "§a region centered on your position.");
                    return 1;
                })
            )

            // /mit commit "message"
            .then(Commands.literal("commit")
                .then(Commands.argument("message", StringArgumentType.string())
                    .executes(ctx -> doCommit(ctx.getSource(), StringArgumentType.getString(ctx, "message"), false))

                    // /mit commit "message" --overwrite
                    .then(Commands.literal("--overwrite")
                        .executes(ctx -> doCommit(ctx.getSource(), StringArgumentType.getString(ctx, "message"), true))
                    )
                )
            )

            // /mit checkout <id>  — with dynamic tab-complete showing id + message
            .then(Commands.literal("checkout")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(COMMIT_SUGGESTIONS)
                    .executes(ctx -> doCheckout(ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                )
            )

            // /mit log
            .then(Commands.literal("log")
                .executes(ctx -> doLog(ctx.getSource()))
            )
        );
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    private static int doCommit(CommandSourceStack source, String message, boolean overwrite) {
        MitRepository repo = MitRepository.getInstance();

        if (!repo.isInitialized()) {
            sendError(source, "No MIT repository found. Run §e/mit init§c first.");
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        ServerLevel level   = source.getLevel();

        // 1. Capture world snapshot
        Map<BlockPos, BlockState> snapshot = captureSnapshot(level, repo.getCorner1(), repo.getCorner2());

        // 2. Compute diff vs the previous commit
        List<MitCommit> log = repo.getLog();
        int[] diff = {0, 0}; // [placed, broken]
        if (!log.isEmpty()) {
            Map<BlockPos, BlockState> prev = log.get(log.size() - 1).blocks;
            diff = computeDiff(prev, snapshot);
        } else {
            // First commit — count non-air blocks as "placed"
            for (BlockState s : snapshot.values()) if (!s.isAir()) diff[0]++;
        }

        // 3. Capture player state
        double px = 0, py = 0, pz = 0;
        float yaw = 0, pitch = 0;
        ListTag invNbt = new ListTag();
        if (player != null) {
            px    = player.getX();
            py    = player.getY();
            pz    = player.getZ();
            yaw   = player.getYRot();
            pitch = player.getXRot();
            player.getInventory().save(invNbt);
        }

        // 4. Save commit
        MitCommit commit = repo.commit(message, snapshot, overwrite,
            diff[0], diff[1], px, py, pz, yaw, pitch, invNbt);

        if (overwrite) {
            sendSuccess(source, "§a[MIT] ⚡ Overwrote timeline! §e[" + commit.id + "]§a \"" + commit.message + "\"");
        } else {
            sendSuccess(source, "§a[MIT] ✅ Committed §e[" + commit.id + "]§a \"" + commit.message + "\"  §a+" + diff[0] + " §c-" + diff[1]);
        }
        MitStorage.save(source.getServer());
        return 1;
    }

    private static int doCheckout(CommandSourceStack source, String id) {
        MitRepository repo = MitRepository.getInstance();

        if (!repo.isInitialized()) {
            sendError(source, "No MIT repository found. Run §e/mit init§c first.");
            return 0;
        }

        MitCommit commit = repo.checkout(id);
        if (commit == null) {
            sendError(source, "No commit with id §e\"" + id + "\"§c found. Use §e/mit log§c to see all commits.");
            return 0;
        }

        // Restore world blocks
        ServerLevel level = source.getLevel();
        restoreSnapshot(level, commit.blocks);

        // Restore player position and inventory
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.teleportTo(commit.playerX, commit.playerY, commit.playerZ);
            player.setYRot(commit.playerYaw);
            player.setXRot(commit.playerPitch);
            player.getInventory().clearContent();
            if (commit.inventoryNbt != null && !commit.inventoryNbt.isEmpty()) {
                player.getInventory().load(commit.inventoryNbt);
            }
        }

        MitStorage.save(source.getServer());
        sendSuccess(source, "§a[MIT] ⏪ Checked out §e[" + commit.id + "]§a \"" + commit.message + "\" — world, position & inventory restored!");
        sendInfo(source, "§7  Tip: Use /mit commit \"msg\" --overwrite to start a new timeline from here.");
        return 1;
    }

    private static int doLog(CommandSourceStack source) {
        MitRepository repo = MitRepository.getInstance();

        if (!repo.isInitialized()) {
            sendError(source, "No MIT repository found. Run §e/mit init§c first.");
            return 0;
        }

        List<MitCommit> log = repo.getLog();
        String sep = "§8§m──────────────────────────────────────────§r";

        // Header
        source.sendSystemMessage(Component.literal(sep));
        source.sendSystemMessage(Component.literal(
            "§b◈ §fMIT  §8│  §7Commit History  §8(" + log.size() + " total)"
        ));
        source.sendSystemMessage(Component.literal(sep));

        if (log.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7  No commits yet."));
            source.sendSystemMessage(Component.literal("§8  Use /mit commit \"message\" to start."));
        } else {
            int headIndex = repo.getHeadIndex();
            for (int i = log.size() - 1; i >= 0; i--) {
                MitCommit c = log.get(i);
                boolean isHead = (i == headIndex);
                String pointer = isHead ? "§b►" : "§8 ";
                String headTag = isHead ? "  §b§l[HEAD]§r" : "";
                String num     = "§8#" + (i + 1) + " ";

                source.sendSystemMessage(Component.literal(
                    " " + pointer + " " + num + "§e[" + c.id + "]  §f\"" + c.message + "\"" + headTag
                ));
                source.sendSystemMessage(Component.literal(
                    "       §8" + c.timestamp
                ));
            }
        }

        // Footer tip
        source.sendSystemMessage(Component.literal(sep));
        source.sendSystemMessage(Component.literal(
            "§7  Press §aG §7for visual UI  §8·  §7/mit checkout <id> §8to time travel"
        ));
        source.sendSystemMessage(Component.literal(sep));
        return 1;
    }

    // -------------------------------------------------------------------------
    // Block snapshot helpers
    // -------------------------------------------------------------------------

    private static Map<BlockPos, BlockState> captureSnapshot(ServerLevel level, BlockPos c1, BlockPos c2) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        int x1 = Math.min(c1.getX(), c2.getX()), x2 = Math.max(c1.getX(), c2.getX());
        int y1 = Math.min(c1.getY(), c2.getY()), y2 = Math.max(c1.getY(), c2.getY());
        int z1 = Math.min(c1.getZ(), c2.getZ()), z2 = Math.max(c1.getZ(), c2.getZ());

        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    map.put(pos, level.getBlockState(pos));
                }
        return map;
    }

    private static void restoreSnapshot(ServerLevel level, Map<BlockPos, BlockState> snapshot) {
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet())
            level.setBlock(entry.getKey(), entry.getValue(), 3);
    }

    /** Computes how many blocks were placed / broken between two snapshots. */
    private static int[] computeDiff(Map<BlockPos, BlockState> prev, Map<BlockPos, BlockState> next) {
        int placed = 0, broken = 0;
        for (Map.Entry<BlockPos, BlockState> e : next.entrySet()) {
            BlockState prevState = prev.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState());
            BlockState nextState = e.getValue();
            boolean wasAir = prevState.isAir();
            boolean isAir  = nextState.isAir();
            if (!wasAir && isAir)  broken++;
            else if (wasAir && !isAir) placed++;
        }
        return new int[]{placed, broken};
    }

    // -------------------------------------------------------------------------
    // Message helpers
    // -------------------------------------------------------------------------

    private static void sendSuccess(CommandSourceStack src, String msg) {
        src.sendSystemMessage(Component.literal(msg));
    }

    private static void sendError(CommandSourceStack src, String msg) {
        src.sendSystemMessage(Component.literal("§c[MIT] " + msg));
    }

    private static void sendInfo(CommandSourceStack src, String msg) {
        src.sendSystemMessage(Component.literal(msg));
    }
}
