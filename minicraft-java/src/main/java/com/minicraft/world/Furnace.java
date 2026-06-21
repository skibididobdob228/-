package com.minicraft.world;

/** A furnace's contents and smelting state. Ticked while its GUI is open. */
public class Furnace {
    public final ItemStack input = new ItemStack();
    public final ItemStack fuel = new ItemStack();
    public final ItemStack output = new ItemStack();

    public float cook;                 // seconds into the current smelt
    public final float cookGoal = 8f;  // seconds per item
    public float fuelLeft, fuelTotal;  // seconds of burn remaining / total

    public void tick(float dt) {
        int result = smeltResult(input.item);
        boolean canSmelt = !input.isEmpty() && result >= 0
                && (output.isEmpty() || (output.item == result && output.count < Items.maxStack(result)));

        if (fuelLeft <= 0 && canSmelt && !fuel.isEmpty()) {
            float fv = fuelValue(fuel.item);
            if (fv > 0) {
                fuelTotal = fuelLeft = fv;
                fuel.count--; if (fuel.count <= 0) fuel.clear();
            }
        }
        if (fuelLeft > 0) {
            fuelLeft -= dt;
            if (canSmelt) {
                cook += dt;
                if (cook >= cookGoal) {
                    cook = 0;
                    if (output.isEmpty()) output.set(result, 1); else output.count++;
                    input.count--; if (input.count <= 0) input.clear();
                }
            } else cook = 0;
        } else cook = 0;
    }

    public boolean isLit() { return fuelLeft > 0; }
    public float cookProgress() { return Math.min(1f, cook / cookGoal); }
    public float fuelProgress() { return fuelTotal > 0 ? Math.max(0f, fuelLeft / fuelTotal) : 0f; }

    /** Smelting result item for an input, or -1 if it can't be smelted. */
    public static int smeltResult(int item) {
        return switch (item) {
            case Blocks.IRON_ORE -> Items.IRON_INGOT;
            case Blocks.GOLD_ORE -> Items.GOLD_INGOT;
            case Blocks.SAND -> Blocks.GLASS;
            case Blocks.COBBLE -> Blocks.STONE;
            case Blocks.CLAY -> Blocks.BRICK;
            case Blocks.LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG -> Items.COAL; // charcoal
            default -> -1;
        };
    }

    /** Burn time in seconds for a fuel item, or 0 if not a fuel. */
    public static float fuelValue(int item) {
        if (item == Items.COAL) return 48f;
        if (item == Blocks.LOG || item == Blocks.SPRUCE_LOG || item == Blocks.BIRCH_LOG) return 12f;
        if (item == Blocks.PLANKS || item == Blocks.SPRUCE_PLANKS || item == Blocks.BIRCH_PLANKS) return 12f;
        if (item == Items.STICK) return 4f;
        if (item == Blocks.CRAFTING_TABLE || item == Blocks.BOOKSHELF || item == Blocks.CHEST) return 12f;
        return 0f;
    }
}
