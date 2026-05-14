package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class MitRepository {

    // Singleton — one repo per game session
    private static MitRepository instance;

    // The tracked region: from corner1 to corner2
    private BlockPos corner1;
    private BlockPos corner2;
    private boolean initialized = false;

    // All commits in order
    private final List<MitCommit> commits = new ArrayList<>();

    // Index of the currently checked out commit (-1 = tip/latest)
    private int headIndex = -1;

    // Dirty tracking — incremented by block events
    private boolean dirty         = false;
    private int     pendingPlaced = 0;
    private int     pendingBroken = 0;

    private MitRepository() {}

    public static MitRepository getInstance() {
        if (instance == null) instance = new MitRepository();
        return instance;
    }

    /** Reset for a new world/session */
    public static void reset() {
        instance = new MitRepository();
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    public void init(BlockPos playerPos, int radius) {
        corner1 = playerPos.offset(-radius, -radius, -radius);
        corner2 = playerPos.offset(radius, radius, radius);
        initialized = true;
        commits.clear();
        headIndex = -1;
    }

    public boolean isInitialized()  { return initialized; }
    public BlockPos getCorner1()    { return corner1; }
    public BlockPos getCorner2()    { return corner2; }
    public boolean isDirty()        { return dirty; }
    public int getPendingPlaced()   { return pendingPlaced; }
    public int getPendingBroken()   { return pendingBroken; }

    /** Called when a block inside the tracked region is placed. */
    public void trackBlockPlaced(BlockPos pos) {
        if (!initialized || !isInRegion(pos)) return;
        dirty = true;
        pendingPlaced++;
    }

    /** Called when a block inside the tracked region is broken. */
    public void trackBlockBroken(BlockPos pos) {
        if (!initialized || !isInRegion(pos)) return;
        dirty = true;
        pendingBroken++;
    }

    /** Reset dirty state after a commit or checkout. */
    public void resetPendingChanges() {
        dirty = false;
        pendingPlaced = 0;
        pendingBroken = 0;
    }

    private boolean isInRegion(BlockPos pos) {
        int x1 = Math.min(corner1.getX(), corner2.getX()), x2 = Math.max(corner1.getX(), corner2.getX());
        int y1 = Math.min(corner1.getY(), corner2.getY()), y2 = Math.max(corner1.getY(), corner2.getY());
        int z1 = Math.min(corner1.getZ(), corner2.getZ()), z2 = Math.max(corner1.getZ(), corner2.getZ());
        return pos.getX() >= x1 && pos.getX() <= x2
            && pos.getY() >= y1 && pos.getY() <= y2
            && pos.getZ() >= z1 && pos.getZ() <= z2;
    }

    /** Called by MitStorage to restore region from disk (no clearing of commits). */
    public void initFromStorage(BlockPos c1, BlockPos c2) {
        this.corner1 = c1;
        this.corner2 = c2;
        this.initialized = true;
    }

    /** Called by MitStorage to bulk-load commits and restore HEAD pointer. */
    public void loadCommits(List<MitCommit> loaded, int head) {
        commits.clear();
        commits.addAll(loaded);
        this.headIndex = head;
    }

    // -------------------------------------------------------------------------
    // Commit
    // -------------------------------------------------------------------------

    /**
     * Creates a commit from a snapshot and appends it to the timeline.
     * If overwrite is true and HEAD is not at the tip, the commits after HEAD are erased first.
     */
    public MitCommit commit(String message, Map<BlockPos, BlockState> snapshot, boolean overwrite,
                            int placed, int broken,
                            double px, double py, double pz, float yaw, float pitch,
                            net.minecraft.nbt.ListTag inventoryNbt) {
        if (overwrite && headIndex >= 0 && headIndex < commits.size() - 1) {
            commits.subList(headIndex + 1, commits.size()).clear();
        }

        String id = generateId();
        MitCommit commit = new MitCommit(id, message, snapshot,
            placed, broken, px, py, pz, yaw, pitch, inventoryNbt);
        commits.add(commit);
        headIndex = commits.size() - 1;
        return commit;
    }

    // -------------------------------------------------------------------------
    // Checkout
    // -------------------------------------------------------------------------

    /** Returns the commit matching the given id, or null if not found. */
    public MitCommit checkout(String id) {
        for (int i = 0; i < commits.size(); i++) {
            if (commits.get(i).id.equalsIgnoreCase(id)) {
                headIndex = i;
                return commits.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Log
    // -------------------------------------------------------------------------

    public List<MitCommit> getLog() {
        return Collections.unmodifiableList(commits);
    }

    public int getHeadIndex() { return headIndex; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Generates a unique 5-character alphanumeric ID (e.g. "a3f9k") */
    private String generateId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random rng = new Random();
        Set<String> existing = new HashSet<>();
        for (MitCommit c : commits) existing.add(c.id);

        String id;
        do {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
            id = sb.toString();
        } while (existing.contains(id));

        return id;
    }
}
