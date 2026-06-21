package com.minicraft.world;

import com.minicraft.render.Tiles;

/** Block id constants + the static definition table. */
public final class Blocks {
    public static final int AIR = 0, STONE = 1, GRASS = 2, DIRT = 3, COBBLE = 4, PLANKS = 5,
            SAND = 6, LOG = 7, LEAVES = 8, WATER = 9, GLASS = 10, BEDROCK = 11, GRAVEL = 12,
            COAL_ORE = 13, IRON_ORE = 14, GOLD_ORE = 15, DIAMOND_ORE = 16, SNOW = 17, BRICK = 18,
            GLOWSTONE = 19, PUMPKIN = 20, CACTUS = 21, FLOWER = 22, TALLGRASS = 23, TORCH = 24,
            OBSIDIAN = 25, PORTAL = 26, NETHERRACK = 27, LAVA = 28, SOUL_SAND = 29,
            NETHER_BRICK = 30, QUARTZ = 31, END_STONE = 32, END_PORTAL = 33, END_FRAME = 34,
            MAGMA = 35,
            SPRUCE_PLANKS = 36, BIRCH_PLANKS = 37, GRANITE = 38, DIORITE = 39, ANDESITE = 40,
            STONE_BRICKS = 41, MOSSY_COBBLE = 42, SANDSTONE = 43, REDSTONE_ORE = 44,
            LAPIS_ORE = 45, EMERALD_ORE = 46, ICE = 47, CLAY = 48,
            WOOL_WHITE = 49, WOOL_RED = 50, WOOL_BLUE = 51, WOOL_GREEN = 52, WOOL_YELLOW = 53,
            WOOL_BLACK = 54, CRAFTING_TABLE = 55, FURNACE = 56, CHEST = 57, BOOKSHELF = 58,
            SPRUCE_LOG = 59, BIRCH_LOG = 60,
            COUNT = 61;

    public enum Render { AIR, SOLID, TRANSPARENT, LIQUID, CROSS }

    public static final class Def {
        public String name = "air";
        public Render render = Render.AIR;
        public boolean solid = false;
        public boolean opaque = false;
        public int light = 0;
        public float hardness = 0f;
        public int texTop, texSide, texBottom;
    }

    private static final Def[] DEFS = new Def[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) DEFS[i] = new Def();

        solid(STONE, "stone", Tiles.STONE, Tiles.STONE, Tiles.STONE, 1.5f);
        solid(GRASS, "grass", Tiles.GRASS_TOP, Tiles.GRASS_SIDE, Tiles.DIRT, 0.6f);
        solid(DIRT, "dirt", Tiles.DIRT, Tiles.DIRT, Tiles.DIRT, 0.5f);
        solid(COBBLE, "cobblestone", Tiles.COBBLE, Tiles.COBBLE, Tiles.COBBLE, 2.0f);
        solid(PLANKS, "planks", Tiles.PLANKS, Tiles.PLANKS, Tiles.PLANKS, 2.0f);
        solid(SAND, "sand", Tiles.SAND, Tiles.SAND, Tiles.SAND, 0.5f);
        solid(LOG, "log", Tiles.LOG_TOP, Tiles.LOG_SIDE, Tiles.LOG_TOP, 2.0f);
        solid(BEDROCK, "bedrock", Tiles.BEDROCK, Tiles.BEDROCK, Tiles.BEDROCK, -1.0f);
        solid(GRAVEL, "gravel", Tiles.GRAVEL, Tiles.GRAVEL, Tiles.GRAVEL, 0.6f);
        solid(COAL_ORE, "coal_ore", Tiles.COAL_ORE, Tiles.COAL_ORE, Tiles.COAL_ORE, 3.0f);
        solid(IRON_ORE, "iron_ore", Tiles.IRON_ORE, Tiles.IRON_ORE, Tiles.IRON_ORE, 3.0f);
        solid(GOLD_ORE, "gold_ore", Tiles.GOLD_ORE, Tiles.GOLD_ORE, Tiles.GOLD_ORE, 3.0f);
        solid(DIAMOND_ORE, "diamond_ore", Tiles.DIAMOND_ORE, Tiles.DIAMOND_ORE, Tiles.DIAMOND_ORE, 3.0f);
        solid(SNOW, "snow", Tiles.SNOW, Tiles.SNOW, Tiles.DIRT, 0.5f);
        solid(BRICK, "brick", Tiles.BRICK, Tiles.BRICK, Tiles.BRICK, 2.0f);
        solid(PUMPKIN, "pumpkin", Tiles.PUMPKIN_TOP, Tiles.PUMPKIN_SIDE, Tiles.PUMPKIN_TOP, 1.0f);
        solid(CACTUS, "cactus", Tiles.CACTUS_TOP, Tiles.CACTUS_SIDE, Tiles.CACTUS_TOP, 0.4f);
        solid(OBSIDIAN, "obsidian", Tiles.OBSIDIAN, Tiles.OBSIDIAN, Tiles.OBSIDIAN, 10.0f);
        solid(NETHERRACK, "netherrack", Tiles.NETHERRACK, Tiles.NETHERRACK, Tiles.NETHERRACK, 0.4f);
        solid(SOUL_SAND, "soul_sand", Tiles.SOUL_SAND, Tiles.SOUL_SAND, Tiles.SOUL_SAND, 0.5f);
        solid(NETHER_BRICK, "nether_brick", Tiles.NETHER_BRICK, Tiles.NETHER_BRICK, Tiles.NETHER_BRICK, 2.0f);
        solid(QUARTZ, "quartz", Tiles.QUARTZ, Tiles.QUARTZ, Tiles.QUARTZ, 0.8f);
        solid(END_STONE, "end_stone", Tiles.END_STONE, Tiles.END_STONE, Tiles.END_STONE, 3.0f);
        solid(END_FRAME, "end_portal_frame", Tiles.END_FRAME, Tiles.END_FRAME, Tiles.STONE, -1.0f);

        solid(GLOWSTONE, "glowstone", Tiles.GLOWSTONE, Tiles.GLOWSTONE, Tiles.GLOWSTONE, 0.3f);
        DEFS[GLOWSTONE].light = 15;
        solid(MAGMA, "magma", Tiles.MAGMA, Tiles.MAGMA, Tiles.MAGMA, 0.5f);
        DEFS[MAGMA].light = 3;

        // --- Added blocks ---
        solid(SPRUCE_PLANKS, "spruce_planks", Tiles.SPRUCE_PLANKS, Tiles.SPRUCE_PLANKS, Tiles.SPRUCE_PLANKS, 2.0f);
        solid(BIRCH_PLANKS, "birch_planks", Tiles.BIRCH_PLANKS, Tiles.BIRCH_PLANKS, Tiles.BIRCH_PLANKS, 2.0f);
        solid(GRANITE, "granite", Tiles.GRANITE, Tiles.GRANITE, Tiles.GRANITE, 1.5f);
        solid(DIORITE, "diorite", Tiles.DIORITE, Tiles.DIORITE, Tiles.DIORITE, 1.5f);
        solid(ANDESITE, "andesite", Tiles.ANDESITE, Tiles.ANDESITE, Tiles.ANDESITE, 1.5f);
        solid(STONE_BRICKS, "stone_bricks", Tiles.STONE_BRICKS, Tiles.STONE_BRICKS, Tiles.STONE_BRICKS, 1.5f);
        solid(MOSSY_COBBLE, "mossy_cobblestone", Tiles.MOSSY_COBBLE, Tiles.MOSSY_COBBLE, Tiles.MOSSY_COBBLE, 2.0f);
        solid(SANDSTONE, "sandstone", Tiles.SANDSTONE, Tiles.SANDSTONE, Tiles.SANDSTONE, 0.8f);
        solid(REDSTONE_ORE, "redstone_ore", Tiles.REDSTONE_ORE, Tiles.REDSTONE_ORE, Tiles.REDSTONE_ORE, 3.0f);
        solid(LAPIS_ORE, "lapis_ore", Tiles.LAPIS_ORE, Tiles.LAPIS_ORE, Tiles.LAPIS_ORE, 3.0f);
        solid(EMERALD_ORE, "emerald_ore", Tiles.EMERALD_ORE, Tiles.EMERALD_ORE, Tiles.EMERALD_ORE, 3.0f);
        solid(CLAY, "clay", Tiles.CLAY, Tiles.CLAY, Tiles.CLAY, 0.6f);
        solid(WOOL_WHITE, "white_wool", Tiles.WOOL_WHITE, Tiles.WOOL_WHITE, Tiles.WOOL_WHITE, 0.8f);
        solid(WOOL_RED, "red_wool", Tiles.WOOL_RED, Tiles.WOOL_RED, Tiles.WOOL_RED, 0.8f);
        solid(WOOL_BLUE, "blue_wool", Tiles.WOOL_BLUE, Tiles.WOOL_BLUE, Tiles.WOOL_BLUE, 0.8f);
        solid(WOOL_GREEN, "green_wool", Tiles.WOOL_GREEN, Tiles.WOOL_GREEN, Tiles.WOOL_GREEN, 0.8f);
        solid(WOOL_YELLOW, "yellow_wool", Tiles.WOOL_YELLOW, Tiles.WOOL_YELLOW, Tiles.WOOL_YELLOW, 0.8f);
        solid(WOOL_BLACK, "black_wool", Tiles.WOOL_BLACK, Tiles.WOOL_BLACK, Tiles.WOOL_BLACK, 0.8f);
        solid(CRAFTING_TABLE, "crafting_table", Tiles.CRAFTING_TOP, Tiles.CRAFTING_FRONT, Tiles.PLANKS, 2.5f);
        solid(FURNACE, "furnace", Tiles.FURNACE_TOP, Tiles.FURNACE_FRONT, Tiles.FURNACE_TOP, 3.5f);
        solid(CHEST, "chest", Tiles.CHEST_TOP, Tiles.CHEST_FRONT, Tiles.CHEST_TOP, 2.5f);
        solid(BOOKSHELF, "bookshelf", Tiles.PLANKS, Tiles.BOOKSHELF, Tiles.PLANKS, 1.5f);
        solid(SPRUCE_LOG, "spruce_log", Tiles.LOG_TOP, Tiles.SPRUCE_LOG_SIDE, Tiles.LOG_TOP, 2.0f);
        solid(BIRCH_LOG, "birch_log", Tiles.LOG_TOP, Tiles.BIRCH_LOG_SIDE, Tiles.LOG_TOP, 2.0f);

        // Ice: transparent solid (slightly see-through).
        transparent(ICE, "ice", Tiles.ICE, 0.5f);

        transparent(LEAVES, "leaves", Tiles.LEAVES, 0.2f);
        transparent(GLASS, "glass", Tiles.GLASS, 0.3f);

        liquid(WATER, "water", Tiles.WATER, 0);
        liquid(LAVA, "lava", Tiles.LAVA, 15);

        cross(FLOWER, "flower", Tiles.FLOWER, 0);
        cross(TALLGRASS, "tallgrass", Tiles.TALLGRASS, 0);
        cross(TORCH, "torch", Tiles.TORCH, 14);

        // Nether/End portal blocks: transparent, glowing, non-solid (walk-through).
        Def p = DEFS[PORTAL];
        p.name = "nether_portal"; p.render = Render.TRANSPARENT; p.solid = false;
        p.opaque = false; p.light = 11; p.hardness = -1f;
        p.texTop = p.texSide = p.texBottom = Tiles.PORTAL;

        Def e = DEFS[END_PORTAL];
        e.name = "end_portal"; e.render = Render.TRANSPARENT; e.solid = false;
        e.opaque = false; e.light = 15; e.hardness = -1f;
        e.texTop = e.texSide = e.texBottom = Tiles.END_PORTAL;
    }

    private static void solid(int id, String name, int top, int side, int bot, float hard) {
        Def d = DEFS[id];
        d.name = name; d.render = Render.SOLID; d.solid = true; d.opaque = true;
        d.hardness = hard; d.texTop = top; d.texSide = side; d.texBottom = bot;
    }
    private static void transparent(int id, String name, int tex, float hard) {
        Def d = DEFS[id];
        d.name = name; d.render = Render.TRANSPARENT; d.solid = true; d.opaque = false;
        d.hardness = hard; d.texTop = d.texSide = d.texBottom = tex;
    }
    private static void liquid(int id, String name, int tex, int light) {
        Def d = DEFS[id];
        d.name = name; d.render = Render.LIQUID; d.solid = false; d.opaque = false;
        d.hardness = -1f; d.light = light; d.texTop = d.texSide = d.texBottom = tex;
    }
    private static void cross(int id, String name, int tex, int light) {
        Def d = DEFS[id];
        d.name = name; d.render = Render.CROSS; d.solid = false; d.opaque = false;
        d.hardness = 0f; d.light = light; d.texTop = d.texSide = d.texBottom = tex;
    }

    public static Def get(int id) { return DEFS[id & 0xFF]; }
    public static boolean isOpaque(int id) { return DEFS[id & 0xFF].opaque; }
    public static boolean isSolid(int id) { return DEFS[id & 0xFF].solid; }
    public static boolean isAir(int id) { return (id & 0xFF) == AIR; }
    public static boolean isLiquid(int id) { return DEFS[id & 0xFF].render == Render.LIQUID; }

    private Blocks() {}
}
