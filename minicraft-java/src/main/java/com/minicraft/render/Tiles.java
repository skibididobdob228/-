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
            MAGMA = 38, COUNT = 39;

    public static final int ATLAS_TILES = 16;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = ATLAS_TILES * TILE_PX;

    private Tiles() {}
}
