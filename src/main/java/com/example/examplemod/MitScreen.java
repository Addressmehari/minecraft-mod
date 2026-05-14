package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MitScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W      = 390;
    private static final int PANEL_H      = 320;
    private static final int PADDING      = 14;
    private static final int HEADER_H     = 32;
    private static final int ROW_H        = 42;
    private static final int VISIBLE_ROWS = 4;
    private static final int INPUT_AREA_H = 34;
    private static final int FOOTER_H     = 30;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xFF080814;
    private static final int COL_HEADER_BG = 0xFF0D1030;
    private static final int COL_ACCENT    = 0xFF4477EE;
    private static final int COL_ACCENT2   = 0xFF2244AA;
    private static final int COL_SHADOW    = 0x88000000;
    private static final int COL_ROW_SEP   = 0xFF141428;
    private static final int COL_HEAD_GLOW = 0x3A4477EE;
    private static final int COL_DIRTY_BG  = 0xFF120900; // dark orange bg for modified state

    // ── State ─────────────────────────────────────────────────────────────────
    private int panelX, panelY;
    private int scrollOffset = 0;
    private EditBox commitInput;

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

    private int listStartY()  { return panelY + HEADER_H; }
    private int inputAreaY()  { return listStartY() + VISIBLE_ROWS * ROW_H; }
    private int footerY()     { return inputAreaY() + INPUT_AREA_H; }

    private void buildWidgets() {
        clearWidgets();
        MitRepository repo = MitRepository.getInstance();

        // ── Commit input (EditBox) ───────────────────────────────────────────
        int inputY = inputAreaY();
        commitInput = new EditBox(font,
            panelX + PADDING, inputY + 10,
            210, 16,
            Component.literal(""));
        commitInput.setHint(Component.literal("Commit message..."));
        commitInput.setMaxLength(64);
        addRenderableWidget(commitInput);

        // ── Commit button ────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
            Component.literal("✅ Commit"), btn -> {
                String msg = commitInput.getValue().trim();
                if (msg.isEmpty()) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null)
                    mc.player.connection.sendCommand("mit commit \"" + msg + "\"");
                onClose();
            })
            .pos(panelX + PADDING + 218, inputY + 7)
            .size(86, 20)
            .build()
        );

        // ── Close button ─────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
            Component.literal("✕ Close"), btn -> onClose())
            .pos(panelX + PANEL_W - 84, footerY() + 6)
            .size(74, 18)
            .build()
        );

        // ── Per-commit Revert buttons ─────────────────────────────────────────
        List<MitCommit> commits = repo.getLog();
        int end = Math.min(scrollOffset + VISIBLE_ROWS, commits.size());
        for (int i = scrollOffset; i < end; i++) {
            final MitCommit commit = commits.get(i);
            int row  = i - scrollOffset;
            int btnY = listStartY() + row * ROW_H + 13;
            addRenderableWidget(Button.builder(
                Component.literal("⏪ Revert"), btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null)
                        mc.player.connection.sendCommand("mit checkout " + commit.id);
                    onClose();
                })
                .pos(panelX + PANEL_W - 88, btnY)
                .size(76, 16)
                .build()
            );
        }
    }

    // ── renderBackground ──────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF050510);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        drawPanel(g);
        drawCommits(g);
        drawInputArea(g);
        drawFooter(g);
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphics g) {
        // Shadow
        g.fill(panelX + 5, panelY + 5, panelX + PANEL_W + 5, panelY + PANEL_H + 5, COL_SHADOW);
        // Background
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        // Borders
        g.fill(panelX,               panelY,               panelX + PANEL_W, panelY + 2,           COL_ACCENT);
        g.fill(panelX,               panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H,     COL_ACCENT);
        g.fill(panelX,               panelY,               panelX + 2,       panelY + PANEL_H,     COL_ACCENT);
        g.fill(panelX + PANEL_W - 2, panelY,               panelX + PANEL_W, panelY + PANEL_H,     COL_ACCENT);
        // Header strip
        g.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + HEADER_H, COL_HEADER_BG);

        MitRepository repo = MitRepository.getInstance();
        boolean dirty = repo.isDirty();

        // Title + status indicator
        if (dirty) {
            g.drawString(font, "§b◈ §fMIT  §8│  §7Build History  §eM", panelX + PADDING, panelY + 11, 0xFFFFFFFF, true);
        } else {
            g.drawString(font, "§b◈ §fMIT  §8│  §7Build History  §a✓", panelX + PADDING, panelY + 11, 0xFFFFFFFF, true);
        }
        g.drawString(font, "§8v1.0", panelX + PANEL_W - 36, panelY + 11, 0xFFFFFFFF, true);

        // Header separator
        int sepColor = dirty ? 0xFF885500 : COL_ACCENT2;
        g.fill(panelX + 2, panelY + HEADER_H, panelX + PANEL_W - 2, panelY + HEADER_H + 1, sepColor);
    }

    private void drawCommits(GuiGraphics g) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int headIndex = MitRepository.getInstance().getHeadIndex();
        int listStart = listStartY();

        if (commits.isEmpty()) {
            g.drawCenteredString(font, "§7No commits yet.", panelX + PANEL_W / 2, listStart + 45, 0xFFEEEEEE);
            g.drawCenteredString(font, "§8Type a message below and click Commit.", panelX + PANEL_W / 2, listStart + 60, 0xFFAAAAAA);
            return;
        }

        int end = Math.min(scrollOffset + VISIBLE_ROWS, commits.size());
        for (int i = scrollOffset; i < end; i++) {
            MitCommit c   = commits.get(i);
            int row       = i - scrollOffset;
            int rowY      = listStart + row * ROW_H;
            boolean isHead = (i == headIndex);

            if (isHead) g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + ROW_H - 1, COL_HEAD_GLOW);
            if (row > 0) g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + 1, COL_ROW_SEP);

            // Pointer
            g.drawString(font, isHead ? "§b►" : "§8·", panelX + PADDING, rowY + 14, 0xFFFFFFFF, true);
            // ID badge
            g.fill(panelX + PADDING + 10, rowY + 9, panelX + PADDING + 52, rowY + 22, 0xFF1A2244);
            g.drawString(font, "§e" + c.id, panelX + PADDING + 12, rowY + 11, 0xFFFFFFFF, true);
            // Message
            g.drawString(font, "§f\"" + c.message + "\"", panelX + PADDING + 57, rowY + 7, 0xFFFFFFFF, true);
            // Timestamp
            g.drawString(font, "§8" + c.timestamp, panelX + PADDING + 57, rowY + 18, 0xFFAAAAAA, true);
            // Stats: +N placed / -N broken
            g.drawString(font, "§a+" + c.blocksPlaced + " placed  §c-" + c.blocksBroken + " broken",
                panelX + PADDING + 57, rowY + 29, 0xFFFFFFFF, true);
            // HEAD badge
            if (isHead) {
                g.fill(panelX + PADDING + 10, rowY + 25, panelX + PADDING + 46, rowY + 37, 0xFF223366);
                g.drawString(font, "§bHEAD", panelX + PADDING + 12, rowY + 27, 0xFFFFFFFF, true);
            }
        }

        // Scroll arrows
        if (scrollOffset > 0)
            g.drawString(font, "§7▲", panelX + 7, listStart + 2, 0xFFFFFFFF, true);
        if (commits.size() > scrollOffset + VISIBLE_ROWS)
            g.drawString(font, "§7▼", panelX + 7, listStart + VISIBLE_ROWS * ROW_H - 10, 0xFFFFFFFF, true);
    }

    private void drawInputArea(GuiGraphics g) {
        int inputY  = inputAreaY();
        MitRepository repo = MitRepository.getInstance();
        boolean dirty = repo.isDirty();

        // Background — orange tint when modified, dark blue otherwise
        int bg = dirty ? COL_DIRTY_BG : 0xFF0A0A1C;
        g.fill(panelX + 2, inputY, panelX + PANEL_W - 2, inputY + INPUT_AREA_H, bg);

        // Top border (orange when dirty)
        int sepColor = dirty ? 0xFF885500 : COL_ACCENT2;
        g.fill(panelX + 2, inputY, panelX + PANEL_W - 2, inputY + 1, sepColor);

        // Pending diff shown to the right of input box when modified
        if (dirty) {
            String pending = "§e⬤  §a+" + repo.getPendingPlaced() + "  §c-" + repo.getPendingBroken();
            g.drawString(font, pending, panelX + PANEL_W - 145, inputY + 11, 0xFFFFFFFF, true);
        }
    }

    private void drawFooter(GuiGraphics g) {
        int fy = footerY();
        g.fill(panelX + 2, fy, panelX + PANEL_W - 2, fy + 1, COL_ACCENT2);
        int n = MitRepository.getInstance().getLog().size();
        g.drawString(font, "§8" + n + " commit" + (n != 1 ? "s" : "") + "  §7·  §8Scroll to navigate",
            panelX + PADDING, fy + 9, 0xFFAAAAAA, true);
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
