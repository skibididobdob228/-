package com.minicraft.world;

/** Player inventory: 9 hotbar + 27 storage, a 3x3 craft grid, output and cursor. */
public class Inventory {
    public static final int HOTBAR = 9;
    public static final int MAIN = 36;           // 0..8 hotbar, 9..35 storage

    public final ItemStack[] slots = new ItemStack[MAIN];
    public final ItemStack[] craft = new ItemStack[9];
    public final ItemStack output = new ItemStack();
    public final ItemStack cursor = new ItemStack();
    public int selected = 0;

    public Inventory() {
        for (int i = 0; i < slots.length; i++) slots[i] = new ItemStack();
        for (int i = 0; i < craft.length; i++) craft[i] = new ItemStack();
    }

    public ItemStack selectedStack() { return slots[selected]; }

    /** Add items, merging into existing stacks then empty slots. Returns leftover. */
    public int add(int item, int count) {
        int max = Items.maxStack(item);
        // merge
        for (int i = 0; i < slots.length && count > 0; i++) {
            ItemStack s = slots[i];
            if (!s.isEmpty() && s.item == item && s.count < max) {
                int space = max - s.count;
                int take = Math.min(space, count);
                s.count += take; count -= take;
            }
        }
        // fill empties (hotbar first feels natural)
        for (int i = 0; i < slots.length && count > 0; i++) {
            ItemStack s = slots[i];
            if (s.isEmpty()) {
                int take = Math.min(max, count);
                s.set(item, take); count -= take;
            }
        }
        return count;
    }

    public boolean addOne(int item) { return add(item, 1) == 0; }

    /** Consume one of the selected hotbar item (survival placement). */
    public boolean consumeSelected() {
        ItemStack s = slots[selected];
        if (s.isEmpty()) return false;
        s.count--;
        if (s.count <= 0) s.clear();
        return true;
    }

    public void scroll(int dir) {
        selected = Math.floorMod(selected - dir, HOTBAR);
    }

    /** Give the player a friendly starter survival kit. */
    public void giveStarterKit() {
        add(Items.PICK_WOOD, 1);
        add(Items.AXE_WOOD, 1);
        add(Items.SWORD_WOOD, 1);
        add(Blocks.PLANKS, 16);
        add(Items.APPLE, 4);
    }

    /** Fill the hotbar with a creative selection. */
    public void giveCreativeHotbar() {
        int[] picks = {Blocks.GRASS, Blocks.STONE, Blocks.COBBLE, Blocks.PLANKS, Blocks.GLASS,
                       Blocks.LOG, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.TORCH};
        for (int i = 0; i < HOTBAR; i++) slots[i].set(picks[i], 1);
    }
}
