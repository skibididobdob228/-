package com.minicraft.ui;

import com.minicraft.world.Furnace;
import com.minicraft.world.Inventory;
import com.minicraft.world.ItemStack;
import com.minicraft.world.Items;

/** Furnace GUI: input + fuel -> output, plus the player storage and hotbar. */
public class FurnaceScreen {
    private static final float CELL = 42, SLOT = 38;
    private int sw, sh;
    private float px, py, pw, ph;

    private void layout(int sw, int sh) {
        this.sw = sw; this.sh = sh;
        pw = 9 * CELL + 24;
        ph = 9 * CELL + 70;
        px = (sw - pw) / 2f;
        py = (sh - ph) / 2f;
    }

    private float[] inputXY() { return new float[]{px + 40, py + 50}; }
    private float[] fuelXY() { return new float[]{px + 40, py + 50 + 2 * CELL}; }
    private float[] outXY() { return new float[]{px + 40 + 4 * CELL, py + 50 + CELL}; }

    public void render(HUD hud, int sw, int sh, Inventory inv, Furnace f, double mx, double my) {
        layout(sw, sh);
        hud.begin(sw, sh);
        hud.rect(0, 0, sw, sh, 0, 0, 0, 0.55f);
        hud.panel(px, py, pw, ph);
        hud.text("Furnace", px + 12, py + 10, 1.6f, 0.15f, 0.15f, 0.15f, 1);

        float[] in = inputXY(), fu = fuelXY(), ou = outXY();
        hud.slot(in[0], in[1], SLOT); hud.item(f.input, in[0] + 3, in[1] + 3, SLOT - 6);
        hud.slot(fu[0], fu[1], SLOT); hud.item(f.fuel, fu[0] + 3, fu[1] + 3, SLOT - 6);
        hud.slot(ou[0], ou[1], SLOT); hud.item(f.output, ou[0] + 3, ou[1] + 3, SLOT - 6);

        // Flame indicator under the input.
        float flameH = 22 * f.fuelProgress();
        hud.rect(in[0] + 10, in[1] + CELL + 6 + (22 - flameH), 16, flameH, 0.95f, 0.55f, 0.15f, 1f);
        // Smelt progress arrow.
        float arrowX = in[0] + CELL + 8, arrowY = in[1] + 12;
        hud.rect(arrowX, arrowY, 3 * CELL - 24, 8, 0.3f, 0.3f, 0.3f, 1f);
        hud.rect(arrowX, arrowY, (3 * CELL - 24) * f.cookProgress(), 8, 0.85f, 0.85f, 0.4f, 1f);

        // Storage + hotbar.
        float stx = px + 12, sty = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++) {
            float x = stx + (i % 9) * CELL, y = sty + (i / 9) * CELL;
            hud.slot(x, y, SLOT); hud.item(inv.slots[9 + i], x + 3, y + 3, SLOT - 6);
        }
        float hy = sty + 3 * CELL + 8;
        for (int i = 0; i < 9; i++) {
            float x = stx + i * CELL;
            hud.slot(x, hy, SLOT);
            if (i == inv.selected) hud.rect(x - 1, hy - 1, SLOT + 2, SLOT + 2, 1, 1, 1, 0.25f);
            hud.item(inv.slots[i], x + 3, hy + 3, SLOT - 6);
        }
        if (!inv.cursor.isEmpty()) hud.item(inv.cursor, (float) mx - 16, (float) my - 16, 32);
        hud.end();
    }

    public void click(Inventory inv, Furnace f, int sw, int sh, double mx, double my, boolean right) {
        layout(sw, sh);
        float[] in = inputXY(), fu = fuelXY(), ou = outXY();
        if (inside(mx, my, in[0], in[1])) { Slots.click(f.input, inv.cursor, right); return; }
        if (inside(mx, my, fu[0], fu[1])) { Slots.click(f.fuel, inv.cursor, right); return; }
        if (inside(mx, my, ou[0], ou[1])) {
            if (!f.output.isEmpty() && (inv.cursor.isEmpty() || inv.cursor.item == f.output.item)) {
                if (inv.cursor.isEmpty()) inv.cursor.set(f.output);
                else inv.cursor.count += f.output.count;
                f.output.clear();
            }
            return;
        }
        float stx = px + 12, sty = py + ph - 4 * CELL - 16;
        for (int i = 0; i < 27; i++)
            if (inside(mx, my, stx + (i % 9) * CELL, sty + (i / 9) * CELL)) { Slots.click(inv.slots[9 + i], inv.cursor, right); return; }
        float hy = sty + 3 * CELL + 8;
        for (int i = 0; i < 9; i++)
            if (inside(mx, my, stx + i * CELL, hy)) { Slots.click(inv.slots[i], inv.cursor, right); return; }
    }

    private boolean inside(double mx, double my, float x, float y) {
        return mx >= x && mx <= x + SLOT && my >= y && my <= y + SLOT;
    }
}
