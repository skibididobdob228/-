package com.minicraft.world;

import com.minicraft.render.Tiles;

/**
 * The item registry. Item ids 0..Blocks.COUNT-1 are placeable block items;
 * ids >= Blocks.COUNT are non-block items (sticks, ingots, tools, food).
 */
public final class Items {
    public static final int STICK = Blocks.COUNT, COAL = Blocks.COUNT + 1,
            IRON_INGOT = Blocks.COUNT + 2, GOLD_INGOT = Blocks.COUNT + 3, DIAMOND = Blocks.COUNT + 4,
            REDSTONE = Blocks.COUNT + 5, LAPIS = Blocks.COUNT + 6, EMERALD = Blocks.COUNT + 7,
            APPLE = Blocks.COUNT + 8, PICK_WOOD = Blocks.COUNT + 9, PICK_STONE = Blocks.COUNT + 10,
            PICK_IRON = Blocks.COUNT + 11, PICK_DIAMOND = Blocks.COUNT + 12, AXE_WOOD = Blocks.COUNT + 13,
            AXE_STONE = Blocks.COUNT + 14, SHOVEL_WOOD = Blocks.COUNT + 15, SWORD_WOOD = Blocks.COUNT + 16,
            SWORD_STONE = Blocks.COUNT + 17, SWORD_IRON = Blocks.COUNT + 18,
            COUNT = Blocks.COUNT + 19;

    public enum Tool { NONE, PICKAXE, AXE, SHOVEL, SWORD }

    public static final class Def {
        public String name;
        public int icon;          // atlas tile for the icon
        public int blockId = -1;  // placeable block, or -1
        public int maxStack = 64;
        public Tool tool = Tool.NONE;
        public int tier = 0;      // 0 wood,1 stone,2 iron,3 diamond
    }

    private static final Def[] DEFS = new Def[COUNT];

    static {
        // Block items mirror the block table.
        for (int b = 1; b < Blocks.COUNT; b++) {
            Def d = new Def();
            d.name = Blocks.get(b).name;
            d.blockId = b;
            d.icon = iconForBlock(b);
            DEFS[b] = d;
        }
        DEFS[0] = new Def(); DEFS[0].name = "air"; DEFS[0].icon = -1;

        item(STICK, "stick", Tiles.ITEM_STICK);
        item(COAL, "coal", Tiles.ITEM_COAL);
        item(IRON_INGOT, "iron_ingot", Tiles.ITEM_IRON);
        item(GOLD_INGOT, "gold_ingot", Tiles.ITEM_GOLD);
        item(DIAMOND, "diamond", Tiles.ITEM_DIAMOND);
        item(REDSTONE, "redstone", Tiles.ITEM_REDSTONE);
        item(LAPIS, "lapis_lazuli", Tiles.ITEM_LAPIS);
        item(EMERALD, "emerald", Tiles.ITEM_EMERALD);
        item(APPLE, "apple", Tiles.ITEM_APPLE);

        tool(PICK_WOOD, "wooden_pickaxe", Tiles.ITEM_PICK_WOOD, Tool.PICKAXE, 0);
        tool(PICK_STONE, "stone_pickaxe", Tiles.ITEM_PICK_STONE, Tool.PICKAXE, 1);
        tool(PICK_IRON, "iron_pickaxe", Tiles.ITEM_PICK_IRON, Tool.PICKAXE, 2);
        tool(PICK_DIAMOND, "diamond_pickaxe", Tiles.ITEM_PICK_DIAMOND, Tool.PICKAXE, 3);
        tool(AXE_WOOD, "wooden_axe", Tiles.ITEM_AXE_WOOD, Tool.AXE, 0);
        tool(AXE_STONE, "stone_axe", Tiles.ITEM_AXE_STONE, Tool.AXE, 1);
        tool(SHOVEL_WOOD, "wooden_shovel", Tiles.ITEM_SHOVEL_WOOD, Tool.SHOVEL, 0);
        tool(SWORD_WOOD, "wooden_sword", Tiles.ITEM_SWORD_WOOD, Tool.SWORD, 0);
        tool(SWORD_STONE, "stone_sword", Tiles.ITEM_SWORD_STONE, Tool.SWORD, 1);
        tool(SWORD_IRON, "iron_sword", Tiles.ITEM_SWORD_IRON, Tool.SWORD, 2);
    }

    private static int iconForBlock(int b) {
        Blocks.Def d = Blocks.get(b);
        // Use the most recognisable face as the icon.
        return d.texSide;
    }

    private static void item(int id, String name, int icon) {
        Def d = new Def(); d.name = name; d.icon = icon; DEFS[id] = d;
    }
    private static void tool(int id, String name, int icon, Tool t, int tier) {
        Def d = new Def(); d.name = name; d.icon = icon; d.tool = t; d.tier = tier; d.maxStack = 1;
        DEFS[id] = d;
    }

    public static Def get(int id) { return DEFS[id]; }
    public static int icon(int id) { return DEFS[id].icon; }
    public static boolean isBlock(int id) { return id >= 0 && id < Blocks.COUNT && DEFS[id].blockId > 0; }
    public static int blockId(int id) { return DEFS[id].blockId; }
    public static int maxStack(int id) { return DEFS[id].maxStack; }
    public static String name(int id) { return DEFS[id].name; }

    /** What item a broken block yields (-1 = nothing). */
    public static int dropFor(int blockId) {
        return switch (blockId) {
            case Blocks.AIR, Blocks.LEAVES, Blocks.TALLGRASS, Blocks.FLOWER,
                 Blocks.WATER, Blocks.LAVA, Blocks.PORTAL, Blocks.END_PORTAL -> -1;
            case Blocks.GRASS -> Blocks.DIRT;
            case Blocks.STONE -> Blocks.COBBLE;
            case Blocks.COAL_ORE -> COAL;
            case Blocks.IRON_ORE -> IRON_INGOT;
            case Blocks.GOLD_ORE -> GOLD_INGOT;
            case Blocks.DIAMOND_ORE -> DIAMOND;
            case Blocks.REDSTONE_ORE -> REDSTONE;
            case Blocks.LAPIS_ORE -> LAPIS;
            case Blocks.EMERALD_ORE -> EMERALD;
            default -> blockId; // drops itself
        };
    }

    private Items() {}
}
