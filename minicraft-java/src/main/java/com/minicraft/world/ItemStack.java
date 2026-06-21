package com.minicraft.world;

/** A stack of items (or empty). */
public final class ItemStack {
    public int item;   // Items id; -1 when empty
    public int count;

    public ItemStack() { this.item = -1; this.count = 0; }
    public ItemStack(int item, int count) { this.item = item; this.count = count; }

    public boolean isEmpty() { return item < 0 || count <= 0; }
    public void clear() { item = -1; count = 0; }
    public ItemStack copy() { return new ItemStack(item, count); }

    public void set(int item, int count) { this.item = item; this.count = count; }
    public void set(ItemStack o) { this.item = o.item; this.count = o.count; }
}
