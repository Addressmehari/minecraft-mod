package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MitCommand {

    // How far around the player's feet the init region extends
    private static final int RADIUS = 30;

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

            // /mit checkout <id>
            .then(Commands.literal("checkout")
                .then(Commands.argument("id", StringArgumentType.word())
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

        ServerLevel level = source.getLevel();
        Map<BlockPos, BlockState> snapshot = captureSnapshot(level, repo.getCorner1(), repo.getCorner2());
        MitCommit commit = repo.commit(message, snapshot, overwrite);

        if (overwrite) {
            sendSuccess(source, "§a[MIT] ⚡ Overwrote timeline! New commit: §e[" + commit.id + "]§a \"" + commit.message + "\"");
        } else {
            sendSuccess(source, "§a[MIT] ✅ Committed §e[" + commit.id + "]§a \"" + commit.message + "\" — " + snapshot.size() + " blocks saved.");
        }
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

        // Restore the blocks
        ServerLevel level = source.getLevel();
        restoreSnapshot(level, commit.blocks);

        sendSuccess(source, "§a[MIT] ⏪ Checked out §e[" + commit.id + "]§a \"" + commit.message + "\"");
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
        if (log.isEmpty()) {
            sendInfo(source, "§7[MIT] No commits yet. Use §e/mit commit \"message\"§7 to save a snapshot.");
            return 1;
        }

        sendInfo(source, "§a[MIT] Commit History:");
        int headIndex = repo.getHeadIndex();
        for (int i = log.size() - 1; i >= 0; i--) {
            MitCommit c = log.get(i);
            String pointer = (i == headIndex) ? "§b► " : "§7  ";
            source.sendSystemMessage(Component.literal(
                pointer + "§e[" + c.id + "] §f\"" + c.message + "\" §8(" + c.timestamp + ")"
            ));
        }
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
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
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
