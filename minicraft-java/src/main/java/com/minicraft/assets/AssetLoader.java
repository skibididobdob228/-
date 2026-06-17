package com.minicraft.assets;

import com.minicraft.render.TextureAtlas;
import com.minicraft.render.Tiles;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Optionally overlays REAL Minecraft block textures onto the atlas, read from
 * the player's OWN locally-installed copy of Minecraft.
 *
 * Important: this never downloads or bundles Mojang assets. It only reads from
 * a Minecraft .jar that already exists on the user's machine (their license).
 * If no copy is found, the procedural textures are used unchanged.
 *
 * Search order:
 *   1. The MINICRAFT_MC_JAR environment variable (explicit path to a version jar)
 *   2. The standard .minecraft/versions/<v>/<v>.jar locations per OS
 */
public final class AssetLoader {

    // atlas tile -> texture file name (under assets/minecraft/textures/block/)
    private static final Map<Integer, String> MAP = new LinkedHashMap<>();
    // tiles that are grayscale in vanilla and need a foliage/biome tint
    private static final Map<Integer, float[]> TINT = new LinkedHashMap<>();

    static {
        MAP.put(Tiles.STONE, "stone");
        MAP.put(Tiles.DIRT, "dirt");
        MAP.put(Tiles.GRASS_TOP, "grass_block_top");
        MAP.put(Tiles.GRASS_SIDE, "grass_block_side");
        MAP.put(Tiles.SAND, "sand");
        MAP.put(Tiles.COBBLE, "cobblestone");
        MAP.put(Tiles.PLANKS, "oak_planks");
        MAP.put(Tiles.LOG_TOP, "oak_log_top");
        MAP.put(Tiles.LOG_SIDE, "oak_log");
        MAP.put(Tiles.LEAVES, "oak_leaves");
        MAP.put(Tiles.BEDROCK, "bedrock");
        MAP.put(Tiles.GRAVEL, "gravel");
        MAP.put(Tiles.COAL_ORE, "coal_ore");
        MAP.put(Tiles.IRON_ORE, "iron_ore");
        MAP.put(Tiles.GOLD_ORE, "gold_ore");
        MAP.put(Tiles.DIAMOND_ORE, "diamond_ore");
        MAP.put(Tiles.SNOW, "snow");
        MAP.put(Tiles.BRICK, "bricks");
        MAP.put(Tiles.GLOWSTONE, "glowstone");
        MAP.put(Tiles.PUMPKIN_TOP, "pumpkin_top");
        MAP.put(Tiles.PUMPKIN_SIDE, "pumpkin_side");
        MAP.put(Tiles.CACTUS_TOP, "cactus_top");
        MAP.put(Tiles.CACTUS_SIDE, "cactus_side");
        MAP.put(Tiles.WATER, "water_still");
        MAP.put(Tiles.GLASS, "glass");
        MAP.put(Tiles.OBSIDIAN, "obsidian");
        MAP.put(Tiles.NETHERRACK, "netherrack");
        MAP.put(Tiles.SOUL_SAND, "soul_sand");
        MAP.put(Tiles.NETHER_BRICK, "nether_bricks");
        MAP.put(Tiles.QUARTZ, "quartz_block_side");
        MAP.put(Tiles.END_STONE, "end_stone");
        MAP.put(Tiles.LAVA, "lava_still");
        MAP.put(Tiles.MAGMA, "magma");
        MAP.put(Tiles.PORTAL, "nether_portal");
        MAP.put(Tiles.FLOWER, "poppy");
        MAP.put(Tiles.TALLGRASS, "short_grass");
        MAP.put(Tiles.TORCH, "torch");

        float[] foliage = {0.49f, 0.70f, 0.30f};
        TINT.put(Tiles.GRASS_TOP, foliage);
        TINT.put(Tiles.LEAVES, foliage);
        TINT.put(Tiles.TALLGRASS, foliage);
    }

    /** Returns true if real textures were applied. */
    public static boolean tryLoadOfficial(TextureAtlas atlas) {
        File jar = locateJar();
        if (jar == null) {
            System.out.println("[INFO] No local Minecraft install found; using procedural textures. " +
                    "(Set MINICRAFT_MC_JAR to a version .jar to use your own copy.)");
            return false;
        }
        try (ZipFile zip = new ZipFile(jar)) {
            int applied = 0;
            for (Map.Entry<Integer, String> e : MAP.entrySet()) {
                String path = "assets/minecraft/textures/block/" + e.getValue() + ".png";
                ZipEntry entry = zip.getEntry(path);
                if (entry == null) continue;
                byte[] tile = decodeTopTile(zip.getInputStream(entry).readAllBytes(), TINT.get(e.getKey()));
                if (tile != null) { atlas.setTile(e.getKey(), tile); applied++; }
            }
            System.out.println("[INFO] Loaded " + applied + " official textures from " + jar.getName());
            return applied > 0;
        } catch (Exception ex) {
            System.out.println("[WARN] Could not read Minecraft jar (" + ex.getMessage() + "); using procedural textures.");
            return false;
        }
    }

    /** Decode a PNG, take the top 16x16 (first animation frame), optional tint. */
    private static byte[] decodeTopTile(byte[] png, float[] tint) {
        ByteBuffer raw = BufferUtils.createByteBuffer(png.length);
        raw.put(png).flip();
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer img = STBImage.stbi_load_from_memory(raw, w, h, c, 4);
            if (img == null) return null;
            int width = w.get(0);
            byte[] out = new byte[16 * 16 * 4];
            // Scale/crop to 16x16 from the top of the source.
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int sx = Math.min(x * width / 16, width - 1);
                    int si = (y * width + sx) * 4;
                    int di = (y * 16 + x) * 4;
                    int r = img.get(si) & 0xFF, g = img.get(si + 1) & 0xFF;
                    int b = img.get(si + 2) & 0xFF, a = img.get(si + 3) & 0xFF;
                    if (tint != null) {
                        r = (int)(r * tint[0]); g = (int)(g * tint[1]); b = (int)(b * tint[2]);
                    }
                    out[di] = (byte) r; out[di + 1] = (byte) g; out[di + 2] = (byte) b; out[di + 3] = (byte) a;
                }
            STBImage.stbi_image_free(img);
            return out;
        }
    }

    private static File locateJar() {
        String env = System.getenv("MINICRAFT_MC_JAR");
        if (env != null && new File(env).isFile()) return new File(env);

        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        File base;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            base = new File((appdata != null ? appdata : home) + "/.minecraft/versions");
        } else if (os.contains("mac")) {
            base = new File(home + "/Library/Application Support/minecraft/versions");
        } else {
            base = new File(home + "/.minecraft/versions");
        }
        if (!base.isDirectory()) return null;
        File best = null;
        File[] versions = base.listFiles(File::isDirectory);
        if (versions == null) return null;
        for (File v : versions) {
            File jar = new File(v, v.getName() + ".jar");
            if (jar.isFile() && (best == null || jar.lastModified() > best.lastModified())) best = jar;
        }
        return best;
    }

    private AssetLoader() {}
}
