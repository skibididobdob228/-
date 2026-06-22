package com.minicraft.ui;

import com.minicraft.world.Container;
import com.minicraft.world.Inventory;

/** Chest GUI: 27 chest slots above the player's storage and hotbar. */
public class ChestScreen {
    private static final float CELL = 42, SLOT = 38;
    private int sw, sh;
    private float px, py, pw, ph;

    private void layout(int sw, int sh) {
        this.sw = sw; this.sh = sh;
        pw = 9 * CELL + 24;
        ph = 7 * CELL + 76;
        px = (sw - pw) / 2f;
        py = (sh - ph) / 2f;
    }

    public void render(HUD hud, int sw, int sh, Inventory inv, Container chest, double mx, double my) {
        layout(sw, sh);
        hud.begin(sw, sh);
        hud.rect(0, 0, sw, sh, 0, 0, 0, 0.55f);
        hud.panel(px, py, pw, ph);
        hud.text("Chest", px + 12, py + 10, 1.6f, 0.15f, 0.15f, 0.15f, 1);

        float cx = px + 12, cy = py + 40;
        for (int i = 0; i < 27; i++) {
            float x = cx + (i % 9) * CELL, y = cy + (i / 9) * CELL;
            hud.slot(x, y, SLOT); hud.item(chest.slots[i], x + 3, y + 3, SLOT - 6);
        }
        float sty = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++) {
            float x = cx + (i % 9) * CELL, y = sty + (i / 9) * CELL;
            hud.slot(x, y, SLOT); hud.item(inv.slots[9 + i], x + 3, y + 3, SLOT - 6);
        }
        float hy = sty + 3 * CELL + 8;
        for (int i = 0; i < 9; i++) {
            float x = cx + i * CELL;
            hud.slot(x, hy, SLOT);
            if (i == inv.selected) hud.rect(x - 1, hy - 1, SLOT + 2, SLOT + 2, 1, 1, 1, 0.25f);
            hud.item(inv.slots[i], x + 3, hy + 3, SLOT - 6);
        }
        if (!inv.cursor.isEmpty()) hud.item(inv.cursor, (float) mx - 16, (float) my - 16, 32);
        hud.end();
    }

    public void click(Inventory inv, Container chest, int sw, int sh, double mx, double my, boolean right) {
        layout(sw, sh);
        float cx = px + 12, cy = py + 40;
        for (int i = 0; i < 27; i++)
            if (inside(mx, my, cx + (i % 9) * CELL, cy + (i / 9) * CELL)) { Slots.click(chest.slots[i], inv.cursor, right); return; }
        float sty = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++)
            if (inside(mx, my, cx + (i % 9) * CELL, sty + (i / 9) * CELL)) { Slots.click(inv.slots[9 + i], inv.cursor, right); return; }
        float hy = sty + 3 * CELL + 8;
        for (int i = 0; i < 9; i++)
            if (inside(mx, my, cx + i * CELL, hy)) { Slots.click(inv.slots[i], inv.cursor, right); return; }
    }

    private boolean inside(double mx, double my, float x, float y) {
        return mx >= x && mx <= x + SLOT && my >= y && my <= y + SLOT;
    }
}
