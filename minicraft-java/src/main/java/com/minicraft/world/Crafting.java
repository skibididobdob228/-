package com.minicraft.world;

import java.util.ArrayList;
import java.util.List;

/** Shaped + shapeless crafting recipes, matched against a 2x2 or 3x3 grid. */
public final class Crafting {

    static final class Recipe {
        boolean shapeless;
        int[] shape;       // for shaped: row-major (w*h), -1 = empty
        int w, h;
        int[] ingredients; // for shapeless
        int outItem, outCount;
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    private static void shaped(int outItem, int outCount, int w, int h, int... shape) {
        Recipe r = new Recipe();
        r.shapeless = false; r.w = w; r.h = h; r.shape = shape; r.outItem = outItem; r.outCount = outCount;
        RECIPES.add(r);
    }
    private static void shapeless(int outItem, int outCount, int... ingredients) {
        Recipe r = new Recipe();
        r.shapeless = true; r.ingredients = ingredients; r.outItem = outItem; r.outCount = outCount;
        RECIPES.add(r);
    }

    static {
        int E = -1;
        // Logs -> planks (shapeless).
        shapeless(Blocks.PLANKS, 4, Blocks.LOG);
        shapeless(Blocks.SPRUCE_PLANKS, 4, Blocks.SPRUCE_LOG);
        shapeless(Blocks.BIRCH_PLANKS, 4, Blocks.BIRCH_LOG);

        // Planks -> sticks (2 vertical) for each plank type.
        for (int p : new int[]{Blocks.PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS})
            shaped(Items.STICK, 4, 1, 2, p, p);

        // Crafting table (2x2 planks).
        shaped(Blocks.CRAFTING_TABLE, 1, 2, 2, Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS);

        // Chest (8 planks ring).
        shaped(Blocks.CHEST, 1, 3, 3,
                Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS,
                Blocks.PLANKS, E, Blocks.PLANKS,
                Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS);
        // Furnace (8 cobblestone ring).
        shaped(Blocks.FURNACE, 1, 3, 3,
                Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                Blocks.COBBLE, E, Blocks.COBBLE,
                Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE);
        // Bookshelf (planks + books-ish: use planks rows + 3 planks middle as a stand-in).
        shaped(Blocks.BOOKSHELF, 1, 3, 3,
                Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS,
                Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS,
                Blocks.PLANKS, Blocks.PLANKS, Blocks.PLANKS);
        // Torches: coal over stick.
        shaped(Blocks.TORCH, 4, 1, 2, Items.COAL, Items.STICK);
        // Stone bricks (2x2 stone).
        shaped(Blocks.STONE_BRICKS, 4, 2, 2, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE);
        // Glass is normally smelted; allow sand->glass as a convenience here.
        shapeless(Blocks.GLASS, 1, Blocks.SAND);

        // Tools: pickaxe (3 head + 2 sticks), axe, shovel, sword.
        pickaxe(Blocks.PLANKS, Items.PICK_WOOD);
        pickaxe(Blocks.COBBLE, Items.PICK_STONE);
        pickaxe(Items.IRON_INGOT, Items.PICK_IRON);
        pickaxe(Items.DIAMOND, Items.PICK_DIAMOND);
        axe(Blocks.PLANKS, Items.AXE_WOOD);
        axe(Blocks.COBBLE, Items.AXE_STONE);
        // shovel: 1 head + 2 sticks (vertical).
        shaped(Items.SHOVEL_WOOD, 1, 1, 3, Blocks.PLANKS, Items.STICK, Items.STICK);
        sword(Blocks.PLANKS, Items.SWORD_WOOD);
        sword(Blocks.COBBLE, Items.SWORD_STONE);
        sword(Items.IRON_INGOT, Items.SWORD_IRON);
    }

    private static void pickaxe(int mat, int out) {
        int E = -1;
        shaped(out, 1, 3, 3, mat, mat, mat, E, Items.STICK, E, E, Items.STICK, E);
    }
    private static void axe(int mat, int out) {
        int E = -1;
        shaped(out, 1, 3, 3, mat, mat, E, mat, Items.STICK, E, E, Items.STICK, E);
    }
    private static void sword(int mat, int out) {
        shaped(out, 1, 1, 3, mat, mat, Items.STICK);
    }

    /** Compute the output for a craft grid (dim 2 or 3). Returns empty if none. */
    public static ItemStack match(ItemStack[] grid, int dim) {
        for (Recipe r : RECIPES) {
            if (r.shapeless) {
                if (matchShapeless(r, grid)) return new ItemStack(r.outItem, r.outCount);
            } else {
                if (matchShaped(r, grid, dim)) return new ItemStack(r.outItem, r.outCount);
            }
        }
        return new ItemStack();
    }

    private static boolean matchShapeless(Recipe r, ItemStack[] grid) {
        List<Integer> items = new ArrayList<>();
        for (ItemStack s : grid) if (!s.isEmpty()) items.add(s.item);
        if (items.size() != r.ingredients.length) return false;
        List<Integer> need = new ArrayList<>();
        for (int i : r.ingredients) need.add(i);
        for (int it : items) if (!need.remove((Integer) it)) return false;
        return need.isEmpty();
    }

    private static boolean matchShaped(Recipe r, ItemStack[] grid, int dim) {
        // Find bounding box of used cells.
        int minR = dim, minC = dim, maxR = -1, maxC = -1;
        for (int row = 0; row < dim; row++)
            for (int col = 0; col < dim; col++)
                if (!grid[row * dim + col].isEmpty()) {
                    minR = Math.min(minR, row); maxR = Math.max(maxR, row);
                    minC = Math.min(minC, col); maxC = Math.max(maxC, col);
                }
        if (maxR < 0) return false;
        int gh = maxR - minR + 1, gw = maxC - minC + 1;
        if (gw != r.w || gh != r.h) return false;
        for (int row = 0; row < r.h; row++)
            for (int col = 0; col < r.w; col++) {
                int want = r.shape[row * r.w + col];
                ItemStack have = grid[(minR + row) * dim + (minC + col)];
                if (want < 0) { if (!have.isEmpty()) return false; }
                else { if (have.isEmpty() || have.item != want) return false; }
            }
        return true;
    }

    /** Consume one of each ingredient used in the grid after a successful craft. */
    public static void consume(ItemStack[] grid) {
        for (ItemStack s : grid)
            if (!s.isEmpty()) { s.count--; if (s.count <= 0) s.clear(); }
    }

    private Crafting() {}
}
