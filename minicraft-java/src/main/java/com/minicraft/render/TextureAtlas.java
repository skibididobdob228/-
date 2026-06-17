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
