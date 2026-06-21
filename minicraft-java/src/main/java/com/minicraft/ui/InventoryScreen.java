package com.minicraft.ui;

import com.minicraft.world.*;

import java.util.ArrayList;
import java.util.List;

/** Inventory GUI: survival crafting (3x3) or a creative item palette. */
public class InventoryScreen {
    private static final float CELL = 42, SLOT = 38;
    private int sw, sh;
    private float px, py, pw, ph;

    private final List<Integer> palette = new ArrayList<>();

    public InventoryScreen() {
        for (int b = 1; b < Blocks.COUNT; b++)
            if (b != Blocks.AIR && b != Blocks.PORTAL && b != Blocks.END_PORTAL) palette.add(b);
        for (int it = Blocks.COUNT; it < Items.COUNT; it++) palette.add(it);
    }

    private void layout(int sw, int sh, boolean creative) {
        this.sw = sw; this.sh = sh;
        pw = 9 * CELL + 24;
        ph = creative ? (11 * CELL + 60) : (9 * CELL + 70);
        px = (sw - pw) / 2f;
        py = (sh - ph) / 2f;
    }

    private float gridX(int origin, int col) { return px + 12 + col * CELL + origin * 0; }

    public void render(HUD hud, int sw, int sh, Inventory inv, boolean creative, double mx, double my) {
        layout(sw, sh, creative);
        hud.begin(sw, sh);
        hud.rect(0, 0, sw, sh, 0, 0, 0, 0.55f);
        hud.panel(px, py, pw, ph);
        hud.text(creative ? "Creative Inventory" : "Crafting", px + 12, py + 10, 1.6f, 0.15f, 0.15f, 0.15f, 1);

        if (creative) {
            float ox = px + 12, oy = py + 40;
            for (int i = 0; i < palette.size(); i++) {
                int col = i % 9, row = i / 9;
                float x = ox + col * CELL, y = oy + row * CELL;
                hud.slot(x, y, SLOT);
                hud.item(new ItemStack(palette.get(i), 1), x + 3, y + 3, SLOT - 6);
            }
        } else {
            // 3x3 crafting grid + output.
            float cx = px + 16, cy = py + 44;
            for (int i = 0; i < 9; i++) {
                float x = cx + (i % 3) * CELL, y = cy + (i / 3) * CELL;
                hud.slot(x, y, SLOT);
                hud.item(inv.craft[i], x + 3, y + 3, SLOT - 6);
            }
            float outX = cx + 3 * CELL + 40, outY = cy + CELL;
            hud.slot(outX, outY, SLOT);
            hud.item(inv.output, outX + 3, outY + 3, SLOT - 6);
            hud.text("=>", outX - 28, outY + 8, 1.6f, 0.1f, 0.1f, 0.1f, 1);
        }

        // Storage (3 rows) + hotbar.
        float sx = px + 12, sy = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++) {
            float x = sx + (i % 9) * CELL, y = sy + (i / 9) * CELL;
            hud.slot(x, y, SLOT);
            hud.item(inv.slots[9 + i], x + 3, y + 3, SLOT - 6);
        }
        float hy = sy + 3 * CELL + 8;
        for (int i = 0; i < 9; i++) {
            float x = sx + i * CELL;
            hud.slot(x, hy, SLOT);
            if (i == inv.selected) hud.rect(x - 1, hy - 1, SLOT + 2, SLOT + 2, 1, 1, 1, 0.25f);
            hud.item(inv.slots[i], x + 3, hy + 3, SLOT - 6);
        }

        // Cursor-held item follows the mouse.
        if (!inv.cursor.isEmpty()) hud.item(inv.cursor, (float) mx - 16, (float) my - 16, 32);

        hud.end();
    }

    /** Returns the inventory slot index under the mouse, or -1. Areas encoded:
     *  0..8 hotbar, 9..35 storage, 100..108 craft, 200 output, 300+i palette. */
    private int hitTest(Inventory inv, boolean creative, double mx, double my) {
        layout(sw, sh, creative);
        if (creative) {
            float ox = px + 12, oy = py + 40;
            for (int i = 0; i < palette.size(); i++) {
                float x = ox + (i % 9) * CELL, y = oy + (i / 9) * CELL;
                if (inside(mx, my, x, y)) return 300 + i;
            }
        } else {
            float cx = px + 16, cy = py + 44;
            for (int i = 0; i < 9; i++) {
                float x = cx + (i % 3) * CELL, y = cy + (i / 3) * CELL;
                if (inside(mx, my, x, y)) return 100 + i;
            }
            float outX = cx + 3 * CELL + 40, outY = cy + CELL;
            if (inside(mx, my, outX, outY)) return 200;
        }
        float sx = px + 12, sy = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++) {
            float x = sx + (i % 9) * CELL, y = sy + (i / 9) * CELL;
            if (inside(mx, my, x, y)) return 9 + i;
        }
        float hy = sy + 3 * CELL + 8;
        for (int i = 0; i < 9; i++)
            if (inside(mx, my, sx + i * CELL, hy)) return i;
        return -1;
    }

    private boolean inside(double mx, double my, float x, float y) {
        return mx >= x && mx <= x + SLOT && my >= y && my <= y + SLOT;
    }

    public void click(Inventory inv, boolean creative, int sw, int sh, double mx, double my, boolean right) {
        this.sw = sw; this.sh = sh;
        int t = hitTest(inv, creative, mx, my);
        ItemStack cur = inv.cursor;
        if (t < 0) { if (creative) cur.clear(); return; } // trash in creative

        if (t >= 300) { // creative palette
            int item = palette.get(t - 300);
            cur.set(item, right ? 1 : Items.maxStack(item));
            return;
        }
        if (t == 200) { // craft output
            if (!inv.output.isEmpty() && (cur.isEmpty() || cur.item == inv.output.item)) {
                if (cur.isEmpty()) cur.set(inv.output);
                else cur.count += inv.output.count;
                Crafting.consume(inv.craft);
                refreshCraft(inv);
            }
            return;
        }
        ItemStack slot = (t >= 100) ? inv.craft[t - 100] : inv.slots[t];
        com.minicraft.ui.Slots.click(slot, cur, right);
        if (t >= 100) refreshCraft(inv);
    }

    public void refreshCraft(Inventory inv) {
        inv.output.set(Crafting.match(inv.craft, 3));
    }

    /** Return leftover craft-grid items to the inventory (called when closing). */
    public void returnCraftItems(Inventory inv) {
        for (ItemStack s : inv.craft)
            if (!s.isEmpty()) { inv.add(s.item, s.count); s.clear(); }
        if (!inv.cursor.isEmpty()) { inv.add(inv.cursor.item, inv.cursor.count); inv.cursor.clear(); }
        inv.output.clear();
    }
}
