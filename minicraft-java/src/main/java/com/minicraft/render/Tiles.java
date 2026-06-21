package com.minicraft.render;

/** Indices into the texture atlas (a grid of ATLAS_TILES x ATLAS_TILES tiles). */
public final class Tiles {
    public static final int STONE = 0, DIRT = 1, GRASS_TOP = 2, GRASS_SIDE = 3, SAND = 4,
            COBBLE = 5, PLANKS = 6, LOG_TOP = 7, LOG_SIDE = 8, LEAVES = 9, WATER = 10,
            GLASS = 11, BEDROCK = 12, GRAVEL = 13, COAL_ORE = 14, IRON_ORE = 15, GOLD_ORE = 16,
            DIAMOND_ORE = 17, SNOW = 18, BRICK = 19, GLOWSTONE = 20, PUMPKIN_TOP = 21,
            PUMPKIN_SIDE = 22, CACTUS_TOP = 23, CACTUS_SIDE = 24, FLOWER = 25, TALLGRASS = 26,
            TORCH = 27, OBSIDIAN = 28, PORTAL = 29, NETHERRACK = 30, LAVA = 31, SOUL_SAND = 32,
            NETHER_BRICK = 33, QUARTZ = 34, END_STONE = 35, END_PORTAL = 36, END_FRAME = 37,
            MAGMA = 38,
            // --- Added blocks ---
            SPRUCE_PLANKS = 39, BIRCH_PLANKS = 40, GRANITE = 41, DIORITE = 42, ANDESITE = 43,
            STONE_BRICKS = 44, MOSSY_COBBLE = 45, SANDSTONE = 46, REDSTONE_ORE = 47,
            LAPIS_ORE = 48, EMERALD_ORE = 49, ICE = 50, CLAY = 51,
            WOOL_WHITE = 52, WOOL_RED = 53, WOOL_BLUE = 54, WOOL_GREEN = 55, WOOL_YELLOW = 56,
            WOOL_BLACK = 57,
            CRAFTING_TOP = 58, CRAFTING_SIDE = 59, CRAFTING_FRONT = 60,
            FURNACE_FRONT = 61, FURNACE_SIDE = 62, FURNACE_TOP = 63,
            CHEST_FRONT = 64, CHEST_SIDE = 65, CHEST_TOP = 66, BOOKSHELF = 67,
            SPRUCE_LOG_SIDE = 68, BIRCH_LOG_SIDE = 69,
            // --- Item icons ---
            ITEM_STICK = 70, ITEM_COAL = 71, ITEM_IRON = 72, ITEM_GOLD = 73, ITEM_DIAMOND = 74,
            ITEM_REDSTONE = 75, ITEM_LAPIS = 76, ITEM_EMERALD = 77, ITEM_APPLE = 78,
            ITEM_PICK_WOOD = 79, ITEM_PICK_STONE = 80, ITEM_PICK_IRON = 81, ITEM_PICK_DIAMOND = 82,
            ITEM_AXE_WOOD = 83, ITEM_SHOVEL_WOOD = 84, ITEM_SWORD_WOOD = 85,
            ITEM_AXE_STONE = 86, ITEM_SWORD_STONE = 87, ITEM_SWORD_IRON = 88,
            COUNT = 89;

    public static final int ATLAS_TILES = 16;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = ATLAS_TILES * TILE_PX;

    private Tiles() {}
}
