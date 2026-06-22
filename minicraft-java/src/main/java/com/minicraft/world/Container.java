package com.minicraft.world;

/** A block's stored items (e.g. a chest) — 27 slots. */
public class Container {
    public final ItemStack[] slots = new ItemStack[27];
    public Container() { for (int i = 0; i < slots.length; i++) slots[i] = new ItemStack(); }
}
