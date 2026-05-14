package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MitScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_W     = 370;
    private static final int PANEL_H     = 280;
    private static final int ROW_H       = 42;
    private static final int PADDING     = 14;
    private static final int HEADER_H    = 32;
    private static final int FOOTER_H    = 32;
    private static final int VISIBLE_ROWS = 5;

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xFF080814;  // fully opaque
    private static final int COL_HEADER_BG = 0xFF0D1030;
    private static final int COL_ACCENT    = 0xFF4477EE;
    private static final int COL_ACCENT2   = 0xFF2244AA;
    private static final int COL_SHADOW    = 0x88000000;
    private static final int COL_ROW_SEP   = 0xFF141428;
    private static final int COL_HEAD_GLOW = 0x3A4477EE;  // stronger glow

    // ── State ─────────────────────────────────────────────────────────────────
    private int panelX, panelY;
    private int scrollOffset = 0;

    public MitScreen() {
        super(Component.literal("MIT — Build History"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        buildWidgets();
    }

    /** Re-adds all buttons based on current scrollOffset. */
    private void buildWidgets() {
        clearWidgets();

        // Close button
        addRenderableWidget(
            Button.builder(Component.literal("✕  Close"), btn -> onClose())
                .pos(panelX + PANEL_W - 88, panelY + PANEL_H - 26)
                .size(78, 18)
                .build()
        );

        // Per-commit "Revert" buttons
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int end = Math.min(scrollOffset + VISIBLE_ROWS, commits.size());
        for (int i = scrollOffset; i < end; i++) {
            final MitCommit commit = commits.get(i);
            int row  = i - scrollOffset;
            int btnY = panelY + HEADER_H + 2 + row * ROW_H + 13;

            addRenderableWidget(
                Button.builder(Component.literal("⏪ Revert"), btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null)
                        mc.player.connection.sendCommand("mit checkout " + commit.id);
                    onClose();
                })
                .pos(panelX + PANEL_W - 86, btnY)
                .size(74, 16)
                .build()
            );
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fully opaque dark overlay — hides the blurred game world completely
        g.fill(0, 0, this.width, this.height, 0xFF050510);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        drawPanel(g);
        drawCommits(g);
        drawFooter(g);
        super.render(g, mouseX, mouseY, delta);  // draws buttons last
    }

    private void drawPanel(GuiGraphics g) {
        // Drop shadow
        g.fill(panelX + 5, panelY + 5, panelX + PANEL_W + 5, panelY + PANEL_H + 5, COL_SHADOW);

        // Main background
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);

        // Accent borders (2px)
        g.fill(panelX,              panelY,              panelX + PANEL_W, panelY + 2,          COL_ACCENT);
        g.fill(panelX,              panelY + PANEL_H - 2,panelX + PANEL_W, panelY + PANEL_H,    COL_ACCENT);
        g.fill(panelX,              panelY,              panelX + 2,        panelY + PANEL_H,    COL_ACCENT);
        g.fill(panelX + PANEL_W - 2,panelY,              panelX + PANEL_W, panelY + PANEL_H,    COL_ACCENT);

        // Header strip
        g.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + HEADER_H, COL_HEADER_BG);

        // Title
        g.drawString(font, "§b◈ §fMIT  §8│  §7Build History", panelX + PADDING, panelY + 11, 0xFFFFFFFF, true);

        // Version badge on right
        g.drawString(font, "§8v1.0", panelX + PANEL_W - 38, panelY + 11, 0xFFFFFFFF, true);

        // Header separator line
        g.fill(panelX + 2, panelY + HEADER_H, panelX + PANEL_W - 2, panelY + HEADER_H + 1, COL_ACCENT2);
    }

    private void drawCommits(GuiGraphics g) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int headIndex = MitRepository.getInstance().getHeadIndex();

        if (commits.isEmpty()) {
            g.drawCenteredString(font, "§7No commits yet.", panelX + PANEL_W / 2, panelY + PANEL_H / 2 - 10, 0xFFEEEEEE);
            g.drawCenteredString(font, "§8Use /mit commit \"message\" to start", panelX + PANEL_W / 2, panelY + PANEL_H / 2 + 4, 0xFFAAAAAA);
            return;
        }

        int end = Math.min(scrollOffset + VISIBLE_ROWS, commits.size());
        for (int i = scrollOffset; i < end; i++) {
            MitCommit c = commits.get(i);
            int row  = i - scrollOffset;
            int rowY = panelY + HEADER_H + 2 + row * ROW_H;
            boolean isHead = (i == headIndex);

            // HEAD highlight glow
            if (isHead)
                g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + ROW_H - 1, COL_HEAD_GLOW);

            // Row divider
            if (row > 0)
                g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + 1, COL_ROW_SEP);

            // ► or  ·  pointer
            g.drawString(font, isHead ? "§b►" : "§8·", panelX + PADDING, rowY + 14, 0xFFFFFFFF, true);

            // Commit ID badge
            g.fill(panelX + PADDING + 10, rowY + 9, panelX + PADDING + 52, rowY + 22, 0xFF1A2244);
            g.drawString(font, "§e" + c.id, panelX + PADDING + 12, rowY + 11, 0xFFFFFFFF, true);

            // Message
            g.drawString(font, "§f\"" + c.message + "\"", panelX + PADDING + 57, rowY + 7, 0xFFFFFFFF, true);

            // Timestamp
            g.drawString(font, "§8" + c.timestamp, panelX + PADDING + 57, rowY + 18, 0xFFAAAAAA, true);

            // Block diff stats
            String statsText = "§a+" + c.blocksPlaced + " placed  §c-" + c.blocksBroken + " broken";
            g.drawString(font, statsText, panelX + PADDING + 57, rowY + 29, 0xFFFFFFFF, true);

            // HEAD badge
            if (isHead) {
                g.fill(panelX + PADDING + 10, rowY + 25, panelX + PADDING + 46, rowY + 36, 0xFF223366);
                g.drawString(font, "§bHEAD", panelX + PADDING + 12, rowY + 27, 0xFFFFFFFF, true);
            }
        }

        // Scroll hint arrows
        if (scrollOffset > 0)
            g.drawString(font, "§7▲", panelX + PANEL_W / 2 - 3, panelY + HEADER_H + 2, 0xFFFFFFFF, true);
        if (commits.size() > scrollOffset + VISIBLE_ROWS)
            g.drawString(font, "§7▼", panelX + PANEL_W / 2 - 3, panelY + HEADER_H + VISIBLE_ROWS * ROW_H - 2, 0xFFFFFFFF, true);
    }

    private void drawFooter(GuiGraphics g) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int footerY = panelY + PANEL_H - FOOTER_H;

        // Footer separator
        g.fill(panelX + 2, footerY, panelX + PANEL_W - 2, footerY + 1, COL_ACCENT2);

        // Commit count
        int n = commits.size();
        g.drawString(font, "§8" + n + " commit" + (n != 1 ? "s" : "") + "  §7·  §8Scroll to navigate",
            panelX + PADDING, footerY + 9, 0xFFAAAAAA, true);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        if (scrollY < 0 && scrollOffset + VISIBLE_ROWS < commits.size()) {
            scrollOffset++;
            buildWidgets();
        } else if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            buildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
