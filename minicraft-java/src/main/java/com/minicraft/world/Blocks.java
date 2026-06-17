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
            MAGMA = 35, COUNT = 36;

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
