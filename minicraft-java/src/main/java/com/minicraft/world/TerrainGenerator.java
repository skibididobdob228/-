package com.minicraft.world;

import com.minicraft.math.Noise;

import static com.minicraft.world.Chunk.*;

/** Procedural generation for every dimension. */
public class TerrainGenerator {
    public enum Biome { OCEAN, BEACH, PLAINS, FOREST, DESERT, MOUNTAINS, SNOWY }

    private final long seed;
    private final Dimension dim;
    private final Noise height, biome, cave, ore;

    public TerrainGenerator(long seed, Dimension dim) {
        this.seed = seed + dim.seedOffset * 1_000_000L;
        this.dim = dim;
        this.height = new Noise(this.seed);
        this.biome = new Noise(this.seed ^ 0x9E3779B9L);
        this.cave = new Noise(this.seed ^ 0x85EBCA77L);
        this.ore = new Noise(this.seed ^ 0xC2B2AE3DL);
    }

    private long hash3(int x, int y, int z, long salt) {
        long n = x * 374761393L + y * 668265263L + z * 2147483647L + (seed + salt) * 362437L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return n ^ (n >> 16);
    }
    private float rnd(int x, int y, int z, long salt) { return (hash3(x, y, z, salt) & 0xFFFFFF) / (float) 0x1000000; }

    public Biome biomeAt(int wx, int wz) {
        float temp = biome.fbm2(wx * 0.0035f, wz * 0.0035f, 3);
        float humid = biome.fbm2((wx + 4000) * 0.0035f, (wz - 4000) * 0.0035f, 3);
        float cont = height.fbm2(wx * 0.0015f, wz * 0.0015f, 3);
        if (cont < -0.30f) return Biome.OCEAN;
        if (cont > 0.45f) return Biome.MOUNTAINS;
        if (temp > 0.35f && humid < -0.10f) return Biome.DESERT;
        if (temp < -0.35f) return Biome.SNOWY;
        if (humid > 0.15f) return Biome.FOREST;
        return Biome.PLAINS;
    }

    public int surfaceHeight(int wx, int wz) {
        Biome b = biomeAt(wx, wz);
        float base = height.fbm2(wx * 0.006f, wz * 0.006f, 5);
        float detail = height.fbm2(wx * 0.03f, wz * 0.03f, 3) * 0.25f;
        float n = base + detail;
        int h = switch (b) {
            case OCEAN -> SEA_LEVEL - 8 + (int)(n * 6);
            case BEACH -> SEA_LEVEL + (int)(n * 2);
            case DESERT -> SEA_LEVEL + 2 + (int)(n * 8);
            case PLAINS -> SEA_LEVEL + 3 + (int)(n * 8);
            case FOREST -> SEA_LEVEL + 4 + (int)(n * 10);
            case SNOWY -> SEA_LEVEL + 4 + (int)(n * 10);
            case MOUNTAINS -> SEA_LEVEL + 12 + (int)((n + 0.3f) * 40);
        };
        return Math.max(4, Math.min(h, SY - 20));
    }

    public int spawnHeight(int wx, int wz) {
        return switch (dim) {
            case OVERWORLD -> Math.max(surfaceHeight(wx, wz), SEA_LEVEL) + 2;
            case NETHER -> 40;
            case END -> 50;
        };
    }

    public void generate(Chunk chunk) {
        switch (dim) {
            case OVERWORLD -> generateOverworld(chunk);
            case NETHER -> generateNether(chunk);
            case END -> generateEnd(chunk);
        }
    }

    private void generateOverworld(Chunk c) {
        int baseX = c.cx * SX, baseZ = c.cz * SZ;
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                int wx = baseX + x, wz = baseZ + z;
                Biome b = biomeAt(wx, wz);
                int h = surfaceHeight(wx, wz);
                for (int y = 0; y <= Math.max(h, SEA_LEVEL); y++) {
                    int blk = Blocks.AIR;
                    if (y == 0) blk = Blocks.BEDROCK;
                    else if (y < h - 4) blk = Blocks.STONE;
                    else if (y < h) blk = (b == Biome.DESERT) ? Blocks.SAND : Blocks.DIRT;
                    else if (y == h) {
                        if (b == Biome.DESERT) blk = Blocks.SAND;
                        else if (b == Biome.SNOWY) blk = Blocks.SNOW;
                        else if (b == Biome.OCEAN || (h < SEA_LEVEL + 1 && b != Biome.MOUNTAINS)) blk = Blocks.SAND;
                        else if (b == Biome.MOUNTAINS && h > SEA_LEVEL + 40) blk = Blocks.STONE;
                        else blk = Blocks.GRASS;
                    } else if (y <= SEA_LEVEL) blk = Blocks.WATER;

                    if (blk == Blocks.STONE || blk == Blocks.DIRT) {
                        float cv = cave.fbm3(wx * 0.04f, y * 0.06f, wz * 0.04f, 3);
                        if (cv > 0.55f && y > 2 && y < h - 1) blk = Blocks.AIR;
                    }
                    if (blk == Blocks.STONE) {
                        float o = rnd(wx, y, wz, 99);
                        if (y < 16 && o > 0.992f) blk = Blocks.DIAMOND_ORE;
                        else if (y < 32 && o > 0.990f) blk = Blocks.GOLD_ORE;
                        else if (y < 48 && o > 0.985f) blk = Blocks.IRON_ORE;
                        else if (o > 0.975f) blk = Blocks.COAL_ORE;
                        else if (o < 0.010f) blk = Blocks.GRAVEL;
                    }
                    c.set(x, y, z, blk);
                }
            }
        // Trees & plants (kept inside the chunk).
        for (int z = 2; z < SZ - 2; z++)
            for (int x = 2; x < SX - 2; x++) {
                int wx = baseX + x, wz = baseZ + z;
                Biome b = biomeAt(wx, wz);
                int h = surfaceHeight(wx, wz);
                if (h < SEA_LEVEL || h + 1 >= SY) continue;
                int surface = c.get(x, h, z);
                if (surface != Blocks.GRASS && surface != Blocks.SAND && surface != Blocks.SNOW) continue;
                float r = rnd(wx, 0, wz, 5);
                float density = b == Biome.FOREST ? 0.06f : b == Biome.PLAINS ? 0.012f : b == Biome.DESERT ? 0.01f : 0f;
                if (r < density) placeTree(c, x, h + 1, z, b);
                else if (surface == Blocks.GRASS) {
                    float pr = rnd(wx, 1, wz, 6);
                    if (pr > 0.85f) c.set(x, h + 1, z, Blocks.TALLGRASS);
                    else if (pr > 0.82f) c.set(x, h + 1, z, Blocks.FLOWER);
                }
            }
    }

    private void placeTree(Chunk c, int lx, int topY, int lz, Biome b) {
        if (b == Biome.DESERT) {
            int hgt = 2 + (int)(rnd(lx, topY, lz, 7) * 2);
            for (int i = 1; i <= hgt; i++) if (topY + i < SY) c.set(lx, topY + i, lz, Blocks.CACTUS);
            return;
        }
        int trunk = 4 + (int)(rnd(lx, topY, lz, 1) * 3);
        int crownY = topY + trunk;
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy <= -1 ? 2 : 1, yy = crownY + dy;
            if (yy < 0 || yy >= SY) continue;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && rnd(lx + dx, yy, lz + dz, 2) < 0.5f) continue;
                    int xx = lx + dx, zz = lz + dz;
                    if (inBounds(xx, yy, zz) && c.get(xx, yy, zz) == Blocks.AIR) c.set(xx, yy, zz, Blocks.LEAVES);
                }
        }
        for (int i = 0; i < trunk; i++) if (topY + i < SY) c.set(lx, topY + i, lz, Blocks.LOG);
    }

    private void generateNether(Chunk c) {
        int baseX = c.cx * SX, baseZ = c.cz * SZ;
        int lavaLevel = 31, ceiling = 120;
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                int wx = baseX + x, wz = baseZ + z;
                for (int y = 0; y < ceiling + 6; y++) {
                    int blk = Blocks.AIR;
                    if (y == 0 || y >= ceiling + 4) blk = Blocks.BEDROCK;
                    else if (y < ceiling) {
                        float d = cave.fbm3(wx * 0.045f, y * 0.05f, wz * 0.045f, 4);
                        boolean solid = d < 0.18f; // carve open tunnels
                        if (solid) {
                            blk = Blocks.NETHERRACK;
                            if (rnd(wx, y, wz, 11) > 0.985f) blk = Blocks.QUARTZ;
                            if (y < lavaLevel + 2 && rnd(wx, y, wz, 12) > 0.97f) blk = Blocks.MAGMA;
                        } else if (y <= lavaLevel) {
                            blk = Blocks.LAVA;
                        }
                    }
                    c.set(x, y, z, blk);
                }
                // Glowstone clusters hanging from the ceiling.
                if (rnd(wx, 7, wz, 20) > 0.98f) {
                    for (int k = 0; k < 3; k++) {
                        int yy = ceiling - 1 - k;
                        if (c.get(x, yy, z) == Blocks.NETHERRACK) c.set(x, yy, z, Blocks.GLOWSTONE);
                    }
                }
                // Soul sand patches near the lava shores.
                if (c.get(x, lavaLevel + 1, z) == Blocks.NETHERRACK && rnd(wx, 3, wz, 21) > 0.9f)
                    c.set(x, lavaLevel + 1, z, Blocks.SOUL_SAND);
            }
    }

    private void generateEnd(Chunk c) {
        int baseX = c.cx * SX, baseZ = c.cz * SZ;
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                int wx = baseX + x, wz = baseZ + z;
                float distFromCenter = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                // Central main island is always present; outer islands from noise.
                float island = height.fbm3(wx * 0.012f, 0, wz * 0.012f, 4);
                float threshold;
                if (distFromCenter < 40) threshold = -1.0f;          // solid central island
                else if (distFromCenter < 80) threshold = 0.15f;     // gap (the void ring)
                else threshold = 0.25f - height.fbm2(wx * 0.004f, wz * 0.004f, 2) * 0.2f;

                if (island > threshold) {
                    int top = 60 + (int)(island * 12);
                    int bottom = 52 - (int)((island - threshold) * 18);
                    for (int y = bottom; y <= top; y++) c.set(x, y, z, Blocks.END_STONE);
                }
            }
    }
}
