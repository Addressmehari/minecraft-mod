package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
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
    private static final int LIST_H       = VISIBLE_ROWS * ROW_H;
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
    private static final int COL_DIRTY_BG  = 0xFF120900;
    private static final int COL_SCROLL_TRACK = 0xFF101020;
    private static final int COL_SCROLL_BAR   = 0xFF303050;

    // ── State ─────────────────────────────────────────────────────────────────
    private int panelX, panelY;
    private double scrollPos = 0;
    private double targetScroll = 0;
    private EditBox commitInput;
    private final List<Button> revertButtons = new ArrayList<>();

    public MitScreen() {
        super(Component.literal("MIT — Build History"));
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        
        // Initial build
        buildStaticWidgets();
        refreshRevertButtons();
    }

    private int listStartY()  { return panelY + HEADER_H; }
    private int inputAreaY()  { return listStartY() + LIST_H; }
    private int footerY()     { return inputAreaY() + INPUT_AREA_H; }

    private void buildStaticWidgets() {
        clearWidgets();
        revertButtons.clear();
        int inputY = inputAreaY();

        // ── Commit input ─────────────────────────────────────────────────────
        commitInput = new EditBox(font, panelX + PADDING, inputY + 10, 170, 16, Component.literal(""));
        commitInput.setHint(Component.literal("Commit message..."));
        commitInput.setMaxLength(64);
        addRenderableWidget(commitInput);

        // ── Commit button ────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("✅ Commit"), btn -> {
            String msg = commitInput.getValue().trim();
            if (msg.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.connection.sendCommand("mit commit \"" + msg + "\"");
            onClose();
        }).pos(panelX + PANEL_W - PADDING - 86, inputY + 7).size(86, 20).build());

        // ── Close button ─────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("✕ Close"), btn -> onClose())
            .pos(panelX + PANEL_W - 84, footerY() + 6).size(74, 18).build());
    }

    private void refreshRevertButtons() {
        // Remove old buttons from the widget list
        for (Button b : revertButtons) removeWidget(b);
        revertButtons.clear();

        List<MitCommit> commits = MitRepository.getInstance().getLog();
        for (int i = 0; i < commits.size(); i++) {
            final MitCommit commit = commits.get(i);
            Button btn = Button.builder(Component.literal("⏪ Revert"), b -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.connection.sendCommand("mit checkout " + commit.id);
                onClose();
            }).pos(panelX + PANEL_W - 96, 0).size(70, 16).build();
            
            revertButtons.add(btn);
            addRenderableWidget(btn);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF050510);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Smooth scroll interpolation
        scrollPos = Mth.lerp(0.2, scrollPos, targetScroll);
        if (Math.abs(scrollPos - targetScroll) < 0.05) scrollPos = targetScroll;

        renderBackground(g, mouseX, mouseY, delta);
        drawPanel(g);
        
        // Draw commits with clipping
        int listY = listStartY();
        g.enableScissor(panelX, listY, panelX + PANEL_W, listY + LIST_H);
        drawCommits(g);
        g.disableScissor();

        drawScrollBar(g);
        drawInputArea(g);
        drawFooter(g);
        
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX + 5, panelY + 5, panelX + PANEL_W + 5, panelY + PANEL_H + 5, COL_SHADOW);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, COL_ACCENT);
        g.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, COL_ACCENT);
        g.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, COL_ACCENT);
        g.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_ACCENT);
        g.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + HEADER_H, COL_HEADER_BG);

        MitRepository repo = MitRepository.getInstance();
        boolean dirty = repo.isDirty();
        g.drawString(font, "§b◈ §fMIT  §8│  §7Build History  " + (dirty ? "§eM" : "§a✓"), panelX + PADDING, panelY + 11, 0xFFFFFFFF, true);
        g.drawString(font, "§8v1.0", panelX + PANEL_W - 36, panelY + 11, 0xFFFFFFFF, true);
        g.fill(panelX + 2, panelY + HEADER_H, panelX + PANEL_W - 2, panelY + HEADER_H + 1, dirty ? 0xFF885500 : COL_ACCENT2);
    }

    private void drawCommits(GuiGraphics g) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int headIndex = MitRepository.getInstance().getHeadIndex();
        int listStart = listStartY();

        if (commits.isEmpty()) {
            g.drawCenteredString(font, "§7No commits yet.", panelX + PANEL_W / 2, listStart + 45, 0xFFEEEEEE);
            return;
        }

        for (int i = 0; i < commits.size(); i++) {
            MitCommit c = commits.get(i);
            int rowY = listStart + (int)(i * ROW_H - scrollPos);
            
            // Only draw if within (or slightly outside) list bounds
            if (rowY + ROW_H < listStart || rowY > listStart + LIST_H) {
                if (i < revertButtons.size()) revertButtons.get(i).visible = false;
                continue;
            }

            boolean isHead = (i == headIndex);
            if (isHead) g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + ROW_H - 1, COL_HEAD_GLOW);
            if (i > 0) g.fill(panelX + 2, rowY, panelX + PANEL_W - 2, rowY + 1, COL_ROW_SEP);

            g.drawString(font, isHead ? "§b►" : "§8·", panelX + PADDING, rowY + 14, 0xFFFFFFFF, true);
            g.fill(panelX + PADDING + 10, rowY + 9, panelX + PADDING + 52, rowY + 22, 0xFF1A2244);
            g.drawString(font, "§e" + c.id, panelX + PADDING + 12, rowY + 11, 0xFFFFFFFF, true);
            g.drawString(font, "§f\"" + c.message + "\"", panelX + PADDING + 57, rowY + 7, 0xFFFFFFFF, true);
            g.drawString(font, "§8" + c.timestamp, panelX + PADDING + 57, rowY + 18, 0xFFAAAAAA, true);
            g.drawString(font, "§a+" + c.blocksPlaced + " placed  §c-" + c.blocksBroken + " broken", panelX + PADDING + 57, rowY + 29, 0xFFFFFFFF, true);
            
            if (isHead) {
                g.fill(panelX + PADDING + 10, rowY + 25, panelX + PADDING + 46, rowY + 37, 0xFF223366);
                g.drawString(font, "§bHEAD", panelX + PADDING + 12, rowY + 27, 0xFFFFFFFF, true);
            }

            // Update button pos and visibility
            if (i < revertButtons.size()) {
                Button btn = revertButtons.get(i);
                int btnY = rowY + 13;
                btn.setY(btnY);
                // Hide button if it's even partially outside the scroll area
                btn.visible = (btnY >= listStart && (btnY + btn.getHeight()) <= listStart + LIST_H);
            }
        }
    }

    private void drawScrollBar(GuiGraphics g) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int totalContentH = commits.size() * ROW_H;
        if (totalContentH <= LIST_H) return;

        int trackX = panelX + PANEL_W - 8;
        int trackY = listStartY();
        int trackW = 4;
        
        // Track
        g.fill(trackX, trackY, trackX + trackW, trackY + LIST_H, COL_SCROLL_TRACK);
        
        // Handle
        int handleH = (int)((double)LIST_H / totalContentH * LIST_H);
        handleH = Math.max(handleH, 10);
        int handleY = trackY + (int)(scrollPos / (totalContentH - LIST_H) * (LIST_H - handleH));
        g.fill(trackX, handleY, trackX + trackW, handleY + handleH, COL_SCROLL_BAR);
    }

    private void drawInputArea(GuiGraphics g) {
        int inputY = inputAreaY();
        MitRepository repo = MitRepository.getInstance();
        boolean dirty = repo.isDirty();
        g.fill(panelX + 2, inputY, panelX + PANEL_W - 2, inputY + INPUT_AREA_H, dirty ? COL_DIRTY_BG : 0xFF0A0A1C);
        g.fill(panelX + 2, inputY, panelX + PANEL_W - 2, inputY + 1, dirty ? 0xFF885500 : COL_ACCENT2);
        if (dirty) {
            String pending = "§e⬤  §a+" + repo.getPendingPlaced() + "  §c-" + repo.getPendingBroken();
            g.drawString(font, pending, panelX + PADDING + 178, inputY + 11, 0xFFFFFFFF, true);
        }
    }

    private void drawFooter(GuiGraphics g) {
        int fy = footerY();
        g.fill(panelX + 2, fy, panelX + PANEL_W - 2, fy + 1, COL_ACCENT2);
        int n = MitRepository.getInstance().getLog().size();
        g.drawString(font, "§8" + n + " commit" + (n != 1 ? "s" : "") + "  §7·  Smooth scroll enabled", panelX + PADDING, fy + 9, 0xFFAAAAAA, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<MitCommit> commits = MitRepository.getInstance().getLog();
        int totalContentH = commits.size() * ROW_H;
        if (totalContentH <= LIST_H) return false;

        targetScroll -= scrollY * 20; // 20 pixels per notch
        targetScroll = Mth.clamp(targetScroll, 0, totalContentH - LIST_H);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
