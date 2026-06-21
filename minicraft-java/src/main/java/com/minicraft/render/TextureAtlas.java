package com.minicraft.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static com.minicraft.render.Tiles.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Builds the texture atlas. By default every tile is generated procedurally so
 * the game needs no external assets. {@link com.minicraft.assets.AssetLoader}
 * may overlay real textures from the player's own local Minecraft copy.
 */
public class TextureAtlas {
    private int texture;
    private final byte[] pixels = new byte[ATLAS_PX * ATLAS_PX * 4];

    public TextureAtlas() {
        generate();
    }

    /** Upload after any external textures have been overlaid. */
    public void upload() {
        ByteBuffer buf = BufferUtils.createByteBuffer(pixels.length);
        buf.put(pixels).flip();
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS_PX, ATLAS_PX, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, texture);
    }

    public int id() { return texture; }

    /** Overlay a 16x16 RGBA tile (used by the official-asset loader). */
    public void setTile(int tile, byte[] rgba16) {
        int col = tile % ATLAS_TILES, row = tile / ATLAS_TILES;
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int src = (y * TILE_PX + x) * 4;
                put(tile, x, y, rgba16[src] & 0xFF, rgba16[src + 1] & 0xFF,
                        rgba16[src + 2] & 0xFF, rgba16[src + 3] & 0xFF);
                // suppress unused warning for col/row
            }
        if (col < 0 || row < 0) throw new IllegalStateException();
    }

    private void put(int tile, int px, int py, int r, int g, int b, int a) {
        int col = tile % ATLAS_TILES, row = tile / ATLAS_TILES;
        int X = col * TILE_PX + px, Y = row * TILE_PX + py;
        int i = (Y * ATLAS_PX + X) * 4;
        pixels[i] = (byte) clamp(r); pixels[i + 1] = (byte) clamp(g);
        pixels[i + 2] = (byte) clamp(b); pixels[i + 3] = (byte) clamp(a);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static float h01(int x, int y, int salt) {
        int n = x * 374761393 + y * 668265263 + salt * 2147483647;
        n = (n ^ (n >> 13)) * 1274126177;
        n ^= n >> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    private void fillNoise(int tile, int r, int g, int b, float grain, int salt) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, salt) - 0.5f) * 2f * grain;
                put(tile, x, y, (int)(r * (1 + n)), (int)(g * (1 + n)), (int)(b * (1 + n)), 255);
            }
    }

    private void generate() {
        fillNoise(STONE, 128, 128, 132, 0.10f, 1);
        fillNoise(COBBLE, 120, 120, 124, 0.22f, 2);
        fillNoise(BEDROCK, 70, 70, 74, 0.30f, 3);
        fillNoise(GRAVEL, 130, 122, 118, 0.28f, 4);
        fillNoise(DIRT, 134, 96, 67, 0.12f, 5);
        fillNoise(GRASS_TOP, 86, 145, 62, 0.14f, 6);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 7) - 0.5f) * 0.2f;
                if (y < 4) put(GRASS_SIDE, x, y, (int)(86*(1+n)), (int)(145*(1+n)), (int)(62*(1+n)), 255);
                else put(GRASS_SIDE, x, y, (int)(134*(1+n)), (int)(96*(1+n)), (int)(67*(1+n)), 255);
            }
        fillNoise(SAND, 219, 207, 153, 0.08f, 8);
        fillNoise(SNOW, 236, 240, 245, 0.05f, 9);

        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 10) - 0.5f) * 0.12f;
                int v = (y % 4 == 0) ? 70 : 100;
                put(PLANKS, x, y, (int)(160*(1+n)*v/100), (int)(124*(1+n)*v/100), (int)(76*(1+n)*v/100), 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 11) - 0.5f) * 0.18f;
                int v = (x % 5 == 0) ? 75 : 100;
                put(LOG_SIDE, x, y, (int)(104*(1+n)*v/100), (int)(78*(1+n)*v/100), (int)(47*(1+n)*v/100), 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float dx = x - 7.5f, dy = y - 7.5f;
                float r = (float) Math.sqrt(dx*dx + dy*dy);
                float ring = 0.8f + 0.3f * (0.5f + 0.5f * (float)Math.sin(r * 2.0));
                put(LOG_TOP, x, y, (int)(150*ring), (int)(118*ring), (int)(73*ring), 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                if (h01(x, y, 12) < 0.12f) { put(LEAVES, x, y, 0, 0, 0, 0); continue; }
                float n = (h01(x, y, 13) - 0.5f) * 0.3f;
                put(LEAVES, x, y, (int)(54*(1+n)), (int)(110*(1+n)), (int)(40*(1+n)), 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 14) - 0.5f) * 0.15f;
                put(WATER, x, y, (int)(54*(1+n)), (int)(102*(1+n)), (int)(198*(1+n)), 170);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean border = x == 0 || y == 0 || x == 15 || y == 15;
                if (border) put(GLASS, x, y, 200, 220, 230, 230);
                else put(GLASS, x, y, 210, 230, 240, 40);
            }
        ore(COAL_ORE, 40, 40, 40, 20);
        ore(IRON_ORE, 196, 160, 120, 21);
        ore(GOLD_ORE, 240, 210, 90, 22);
        ore(DIAMOND_ORE, 110, 220, 220, 23);

        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int row = y / 4, offset = (row % 2) * 4;
                boolean mortar = (y % 4 == 0) || ((x + offset) % 8 == 0);
                if (mortar) put(BRICK, x, y, 200, 200, 195, 255);
                else put(BRICK, x, y, 150, 60, 50, 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean g = h01(x, y, 30) > 0.5f;
                if (g) put(GLOWSTONE, x, y, 255, 230, 140, 255);
                else put(GLOWSTONE, x, y, 200, 150, 60, 255);
            }
        fillNoise(PUMPKIN_TOP, 214, 140, 40, 0.1f, 31);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 32) - 0.5f) * 0.1f;
                int v = (x % 4 == 0) ? 80 : 100;
                put(PUMPKIN_SIDE, x, y, (int)(220*(1+n)*v/100), (int)(130*(1+n)*v/100), (int)(35*(1+n)*v/100), 255);
            }
        fillNoise(CACTUS_TOP, 90, 150, 70, 0.1f, 33);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 34) - 0.5f) * 0.12f;
                int v = (x == 1 || x == 14) ? 70 : 100;
                put(CACTUS_SIDE, x, y, (int)(74*(1+n)*v/100), (int)(130*(1+n)*v/100), (int)(58*(1+n)*v/100), 255);
            }

        // Flower / tall grass / torch (cross plants on transparent background).
        clear(FLOWER); clear(TALLGRASS); clear(TORCH);
        for (int y = 7; y < 16; y++) { put(FLOWER, 7, y, 60, 130, 50, 255); put(FLOWER, 8, y, 60, 130, 50, 255); }
        for (int y = 3; y < 8; y++)
            for (int x = 5; x < 11; x++)
                if (Math.abs(x - 7.5f) + Math.abs(y - 5.5f) < 4f) put(FLOWER, x, y, 220, 70, 90, 255);
        put(FLOWER, 7, 5, 250, 220, 80, 255); put(FLOWER, 8, 5, 250, 220, 80, 255);
        for (int x = 2; x < 14; x++) {
            int top = 4 + (int)(h01(x, 0, 40) * 4);
            for (int y = top; y < 16; y++) {
                float n = (h01(x, y, 41) - 0.5f) * 0.25f;
                put(TALLGRASS, x, y, (int)(80*(1+n)), (int)(150*(1+n)), (int)(55*(1+n)), 255);
            }
        }
        for (int y = 6; y < 16; y++) { put(TORCH, 7, y, 120, 80, 40, 255); put(TORCH, 8, y, 120, 80, 40, 255); }
        for (int y = 3; y < 7; y++) { put(TORCH, 7, y, 255, 210, 90, 255); put(TORCH, 8, y, 255, 230, 120, 255); }

        // --- Dimension blocks ---
        fillNoise(OBSIDIAN, 30, 24, 44, 0.25f, 50);
        fillNoise(NETHERRACK, 110, 40, 40, 0.30f, 51);
        fillNoise(SOUL_SAND, 84, 64, 52, 0.25f, 52);
        fillNoise(NETHER_BRICK, 48, 24, 28, 0.20f, 53);
        fillNoise(QUARTZ, 235, 230, 222, 0.06f, 54);
        fillNoise(END_STONE, 222, 224, 170, 0.10f, 55);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 56) - 0.5f);
                boolean hot = n > 0.2f;
                if (hot) put(LAVA, x, y, 255, 160, 40, 255);
                else put(LAVA, x, y, 210, 90, 20, 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 57) - 0.5f);
                if (n > 0.25f) put(MAGMA, x, y, 255, 150, 40, 255);
                else put(MAGMA, x, y, 60, 20, 20, 255);
            }
        // Nether portal: swirling purple.
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float s = (float)(0.5 + 0.5 * Math.sin(x * 0.9 + y * 0.5));
                float t = (float)(0.5 + 0.5 * Math.cos(y * 0.8 - x * 0.4));
                int r = (int)(120 + 90 * s), g = (int)(20 + 40 * t), b = (int)(160 + 80 * s);
                put(PORTAL, x, y, r, g, b, 190);
            }
        // End portal: starry dark.
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean star = h01(x, y, 58) > 0.86f;
                if (star) put(END_PORTAL, x, y, 200, 220, 255, 230);
                else put(END_PORTAL, x, y, 6, 4, 20, 235);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 59) - 0.5f) * 0.15f;
                put(END_FRAME, x, y, (int)(150*(1+n)), (int)(150*(1+n)), (int)(140*(1+n)), 255);
            }
        // Cells 1..3 in the end frame get a greenish "eye".
        for (int y = 3; y < 13; y++)
            for (int x = 3; x < 13; x++) put(END_FRAME, x, y, 60, 120, 90, 255);

        generateExtra();
        darken(0.80f); // muted, closer to vanilla Minecraft
    }

    /** Multiply every texel's RGB toward darker, vanilla-like tones. */
    private void darken(float f) {
        for (int i = 0; i < pixels.length; i += 4) {
            if ((pixels[i + 3] & 0xFF) == 0) continue;
            pixels[i] = (byte) clamp((int) ((pixels[i] & 0xFF) * f));
            pixels[i + 1] = (byte) clamp((int) ((pixels[i + 1] & 0xFF) * f));
            pixels[i + 2] = (byte) clamp((int) ((pixels[i + 2] & 0xFF) * f));
        }
    }

    private void generateExtra() {
        // Wood planks variants.
        planks(SPRUCE_PLANKS, 104, 78, 52);
        planks(BIRCH_PLANKS, 198, 180, 140);
        // Stone variants.
        fillNoise(GRANITE, 150, 100, 90, 0.14f, 60);
        fillNoise(DIORITE, 200, 200, 200, 0.16f, 61);
        fillNoise(ANDESITE, 130, 132, 134, 0.12f, 62);
        fillNoise(SANDSTONE, 216, 202, 150, 0.06f, 63);
        // Stone bricks.
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean seam = (y % 8 == 0) || (x % 8 == 0 && y % 8 < 8);
                float n = (h01(x, y, 64) - 0.5f) * 0.10f;
                if (seam) put(STONE_BRICKS, x, y, 90, 90, 94, 255);
                else put(STONE_BRICKS, x, y, (int)(120*(1+n)), (int)(120*(1+n)), (int)(124*(1+n)), 255);
            }
        // Mossy cobblestone.
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 2) - 0.5f) * 0.22f;
                boolean moss = h01(x, y, 65) > 0.6f;
                if (moss) put(MOSSY_COBBLE, x, y, (int)(70*(1+n)), (int)(110*(1+n)), (int)(55*(1+n)), 255);
                else put(MOSSY_COBBLE, x, y, (int)(120*(1+n)), (int)(120*(1+n)), (int)(124*(1+n)), 255);
            }
        // Ores.
        ore(REDSTONE_ORE, 200, 30, 30, 70);
        ore(LAPIS_ORE, 40, 70, 200, 71);
        ore(EMERALD_ORE, 40, 200, 90, 72);
        // Ice (translucent blue).
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 66) - 0.5f) * 0.08f;
                put(ICE, x, y, (int)(150*(1+n)), (int)(190*(1+n)), (int)(230*(1+n)), 200);
            }
        fillNoise(CLAY, 160, 165, 175, 0.06f, 67);
        // Wool colours.
        wool(WOOL_WHITE, 235, 235, 235);
        wool(WOOL_RED, 180, 50, 50);
        wool(WOOL_BLUE, 50, 70, 180);
        wool(WOOL_GREEN, 70, 150, 60);
        wool(WOOL_YELLOW, 220, 200, 60);
        wool(WOOL_BLACK, 35, 35, 38);
        // Spruce / birch log bark.
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 68) - 0.5f) * 0.18f;
                int v = (x % 5 == 0) ? 70 : 100;
                put(SPRUCE_LOG_SIDE, x, y, (int)(70*(1+n)*v/100), (int)(52*(1+n)*v/100), (int)(35*(1+n)*v/100), 255);
            }
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 69) - 0.5f) * 0.10f;
                boolean mark = (x == 3 || x == 11) && (y % 6 < 2);
                if (mark) put(BIRCH_LOG_SIDE, x, y, 60, 60, 55, 255);
                else put(BIRCH_LOG_SIDE, x, y, (int)(220*(1+n)), (int)(218*(1+n)), (int)(205*(1+n)), 255);
            }
        // Crafting table.
        planks(CRAFTING_TOP, 150, 110, 70);
        for (int y = 0; y < 8; y++) for (int x = 0; x < TILE_PX; x++) { } // grid drawn below
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean grid = (x == 0 || x == 8 || x == 15 || y == 0 || y == 8 || y == 15);
                if (grid) put(CRAFTING_TOP, x, y, 90, 60, 35, 255);
            }
        planks(CRAFTING_SIDE, 140, 100, 64);
        planks(CRAFTING_FRONT, 140, 100, 64);
        for (int y = 4; y < 12; y++) for (int x = 3; x < 13; x++) put(CRAFTING_FRONT, x, y, 90, 64, 40, 255);
        // Furnace.
        fillNoise(FURNACE_TOP, 110, 110, 114, 0.10f, 73);
        fillNoise(FURNACE_SIDE, 110, 110, 114, 0.10f, 74);
        fillNoise(FURNACE_FRONT, 110, 110, 114, 0.10f, 75);
        for (int y = 5; y < 13; y++) for (int x = 4; x < 12; x++) put(FURNACE_FRONT, x, y, 40, 40, 44, 255);
        for (int y = 8; y < 12; y++) for (int x = 5; x < 11; x++) put(FURNACE_FRONT, x, y, 230, 120, 40, 255);
        // Chest.
        fillNoise(CHEST_TOP, 150, 110, 60, 0.06f, 76);
        fillNoise(CHEST_SIDE, 150, 110, 60, 0.06f, 77);
        fillNoise(CHEST_FRONT, 150, 110, 60, 0.06f, 78);
        for (int x = 0; x < TILE_PX; x++) { put(CHEST_FRONT, x, 7, 90, 60, 30, 255); put(CHEST_FRONT, x, 8, 90, 60, 30, 255); }
        put(CHEST_FRONT, 7, 8, 60, 60, 60, 255); put(CHEST_FRONT, 8, 8, 60, 60, 60, 255); // latch
        // Bookshelf.
        planks(BOOKSHELF, 150, 110, 70);
        int[] bookCols = {180,60,60, 60,120,180, 70,160,70, 200,180,60, 150,90,170};
        for (int row = 0; row < 2; row++)
            for (int bx = 0; bx < 14; bx += 3) {
                int ci = ((bx / 3) % 5) * 3;
                int x0 = 1 + bx;
                int y0 = 2 + row * 7;
                for (int yy = y0; yy < y0 + 5 && yy < 16; yy++)
                    for (int xx = x0; xx < x0 + 2 && xx < 16; xx++)
                        put(BOOKSHELF, xx, yy, bookCols[ci], bookCols[ci+1], bookCols[ci+2], 255);
            }

        // --- Item icons ---
        clear(ITEM_STICK);
        for (int y = 4; y < 14; y++) { put(ITEM_STICK, 7, y, 140, 100, 55, 255); put(ITEM_STICK, 8, y, 120, 84, 45, 255); }
        gem(ITEM_COAL, 40, 40, 40);
        gem(ITEM_IRON, 220, 200, 185);
        gem(ITEM_GOLD, 245, 215, 70);
        gem(ITEM_DIAMOND, 110, 230, 230);
        gem(ITEM_REDSTONE, 210, 40, 40);
        gem(ITEM_LAPIS, 50, 80, 200);
        gem(ITEM_EMERALD, 50, 210, 100);
        // Apple.
        clear(ITEM_APPLE);
        for (int y = 4; y < 14; y++)
            for (int x = 4; x < 13; x++)
                if (Math.hypot(x - 8, y - 9) < 4.2) put(ITEM_APPLE, x, y, 200, 40, 45, 255);
        put(ITEM_APPLE, 8, 3, 90, 60, 40, 255); put(ITEM_APPLE, 9, 4, 70, 140, 60, 255);
        // Tools (handle + coloured head).
        tool(ITEM_PICK_WOOD, 150, 110, 70, 0);
        tool(ITEM_PICK_STONE, 130, 130, 134, 0);
        tool(ITEM_PICK_IRON, 220, 200, 185, 0);
        tool(ITEM_PICK_DIAMOND, 110, 230, 230, 0);
        tool(ITEM_AXE_WOOD, 150, 110, 70, 1);
        tool(ITEM_AXE_STONE, 130, 130, 134, 1);
        tool(ITEM_SHOVEL_WOOD, 150, 110, 70, 2);
        tool(ITEM_SWORD_WOOD, 150, 110, 70, 3);
        tool(ITEM_SWORD_STONE, 130, 130, 134, 3);
        tool(ITEM_SWORD_IRON, 220, 200, 185, 3);
    }

    private void planks(int tile, int r, int g, int b) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 10) - 0.5f) * 0.12f;
                int v = (y % 4 == 0) ? 72 : 100;
                put(tile, x, y, (int)(r*(1+n)*v/100), (int)(g*(1+n)*v/100), (int)(b*(1+n)*v/100), 255);
            }
    }

    private void wool(int tile, int r, int g, int b) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 90) - 0.5f) * 0.10f;
                put(tile, x, y, (int)(r*(1+n)), (int)(g*(1+n)), (int)(b*(1+n)), 255);
            }
    }

    private void gem(int tile, int r, int g, int b) {
        clear(tile);
        for (int y = 5; y < 12; y++)
            for (int x = 5; x < 12; x++) {
                float n = (h01(x, y, 91) - 0.5f) * 0.2f;
                if (Math.abs(x - 8) + Math.abs(y - 8) <= 4)
                    put(tile, x, y, (int)(r*(1+n)), (int)(g*(1+n)), (int)(b*(1+n)), 255);
            }
    }

    /** kind: 0 pickaxe, 1 axe, 2 shovel, 3 sword. */
    private void tool(int tile, int r, int g, int b, int kind) {
        clear(tile);
        // Wooden handle along the diagonal.
        for (int i = 4; i < 13; i++) put(tile, i, 16 - i, 140, 100, 55, 255);
        for (int i = 4; i < 13; i++) if (16 - i - 1 >= 0) put(tile, i, 15 - i, 120, 84, 45, 255);
        if (kind == 3) { // sword: blade up the diagonal, guard
            for (int i = 3; i < 12; i++) put(tile, i, 13 - i, r, g, b, 255);
            put(tile, 4, 11, 120, 84, 45, 255); put(tile, 5, 12, 120, 84, 45, 255);
            return;
        }
        // Head at top-right.
        for (int y = 2; y < 6; y++)
            for (int x = 9; x < 14; x++) {
                boolean head = switch (kind) {
                    case 0 -> y == 2 || x == 9 || x == 13;        // pickaxe bar
                    case 1 -> x >= 11 && y <= 4;                  // axe blade
                    default -> x >= 11 && x <= 12 && y <= 5;      // shovel scoop
                };
                if (head) put(tile, x, y, r, g, b, 255);
            }
    }

    private void ore(int tile, int r, int g, int b, int salt) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                float n = (h01(x, y, 1) - 0.5f) * 0.10f;
                if (h01(x, y, salt) > 0.80f) put(tile, x, y, r, g, b, 255);
                else put(tile, x, y, (int)(128*(1+n)), (int)(128*(1+n)), (int)(132*(1+n)), 255);
            }
    }

    private void clear(int tile) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) put(tile, x, y, 0, 0, 0, 0);
    }
}
