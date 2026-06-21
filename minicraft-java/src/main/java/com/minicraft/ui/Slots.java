package com.minicraft.ui;

import com.minicraft.world.ItemStack;
import com.minicraft.world.Items;

/** Shared mouse interaction for a single inventory slot vs the cursor stack. */
public final class Slots {
    public static void click(ItemStack slot, ItemStack cur, boolean right) {
        if (right) {
            if (cur.isEmpty() && !slot.isEmpty()) {
                int half = (slot.count + 1) / 2;
                cur.set(slot.item, half);
                slot.count -= half; if (slot.count <= 0) slot.clear();
            } else if (!cur.isEmpty()) {
                if (slot.isEmpty()) { slot.set(cur.item, 1); cur.count--; }
                else if (slot.item == cur.item && slot.count < Items.maxStack(slot.item)) { slot.count++; cur.count--; }
                if (cur.count <= 0) cur.clear();
            }
            return;
        }
        if (cur.isEmpty()) {
            if (!slot.isEmpty()) { cur.set(slot); slot.clear(); }
        } else if (slot.isEmpty()) {
            slot.set(cur); cur.clear();
        } else if (slot.item == cur.item) {
            int max = Items.maxStack(slot.item);
            int move = Math.min(cur.count, max - slot.count);
            slot.count += move; cur.count -= move;
            if (cur.count <= 0) cur.clear();
        } else {
            int it = slot.item, ct = slot.count;
            slot.set(cur); cur.set(it, ct);
        }
    }

    private Slots() {}
}
