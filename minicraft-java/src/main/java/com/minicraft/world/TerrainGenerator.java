package com.minicraft.world;

import com.minicraft.math.Noise;

import static com.minicraft.world.Chunk.*;

/** Procedural generation for every dimension (varied terrain, rivers, biomes). */
public class TerrainGenerator {
    public enum Biome { OCEAN, BEACH, PLAINS, FOREST, TAIGA, DESERT, MOUNTAINS, SNOWY }

    private final long seed;
    private final Dimension dim;
    private final Noise height, biome, cave, ore, mountain, river;

    public TerrainGenerator(long seed, Dimension dim) {
        this.seed = seed + dim.seedOffset * 1_000_000L;
        this.dim = dim;
        this.height = new Noise(this.seed);
        this.biome = new Noise(this.seed ^ 0x9E3779B9L);
        this.cave = new Noise(this.seed ^ 0x85EBCA77L);
        this.ore = new Noise(this.seed ^ 0xC2B2AE3DL);
        this.mountain = new Noise(this.seed ^ 0x27D4EB2FL);
        this.river = new Noise(this.seed ^ 0x165667B1L);
    }

    public static final class Sample { public int h; public Biome biome; public boolean river; }

    private long hash3(int x, int y, int z, long salt) {
        long n = x * 374761393L + y * 668265263L + z * 2147483647L + (seed + salt) * 362437L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return n ^ (n >> 16);
    }
    private float rnd(int x, int y, int z, long salt) { return (hash3(x, y, z, salt) & 0xFFFFFF) / (float) 0x1000000; }

    /** Full terrain sample for a world column. */
    public Sample sample(int wx, int wz) {
        Sample s = new Sample();
        double cont = height.fbm2(wx * 0.0009f, wz * 0.0009f, 4);
        double hills = height.fbm2(wx * 0.012f, wz * 0.012f, 4);
        double mtn = mountain.fbm2(wx * 0.0016f, wz * 0.0016f, 4);
        double temp = biome.fbm2(wx * 0.0035f, wz * 0.0035f, 3);
        double humid = biome.fbm2((wx + 4000) * 0.0035f, (wz - 4000) * 0.0035f, 3);

        int h = SEA_LEVEL + (int) (cont * 24) + (int) (hills * 7);
        boolean isMountain = mtn > 0.34;
        if (isMountain) {
            double m = (mtn - 0.34) / 0.66;
            double ridge = 1.0 - Math.abs(height.fbm2(wx * 0.005f, wz * 0.005f, 3)); // sharp ridges
            h += (int) (m * ridge * 78);
        }

        // Rivers: winding channels along the zero-crossings of a low-freq noise.
        double rv = Math.abs(river.fbm2(wx * 0.0016f, wz * 0.0016f, 2));
        boolean isRiver = false;
        if (rv < 0.035 && !isMountain && cont > -0.32) {
            double t = 1 - rv / 0.035;        // 0 at bank, 1 at centre
            int bed = SEA_LEVEL - 3;
            h = (int) (h + (bed - h) * t * 0.95);
            isRiver = t > 0.25;
        }
        h = Math.max(4, Math.min(h, SY - 20));

        Biome b;
        if (cont < -0.32) b = Biome.OCEAN;
        else if (isMountain && h > SEA_LEVEL + 28) b = Biome.MOUNTAINS;
        else if (h <= SEA_LEVEL + 1 && !isRiver) b = Biome.BEACH;
        else if (temp < -0.45) b = Biome.SNOWY;
        else if (temp < -0.12) b = Biome.TAIGA;
        else if (temp > 0.40 && humid < 0.0) b = Biome.DESERT;
        else if (humid > 0.22) b = Biome.FOREST;
        else b = Biome.PLAINS;

        s.h = h; s.biome = b; s.river = isRiver;
        return s;
    }

    public int spawnHeight(int wx, int wz) {
        return switch (dim) {
            case OVERWORLD -> Math.max(sample(wx, wz).h, SEA_LEVEL) + 2;
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
        Sample[] col = new Sample[SX * SZ];
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                int wx = baseX + x, wz = baseZ + z;
                Sample s = sample(wx, wz);
                col[z * SX + x] = s;
                int h = s.h;
                Biome b = s.biome;
                boolean peak = h > SEA_LEVEL + 50;

                for (int y = 0; y <= Math.max(h, SEA_LEVEL); y++) {
                    int blk = Blocks.AIR;
                    if (y == 0) blk = Blocks.BEDROCK;
                    else if (y < h - 4) blk = Blocks.STONE;
                    else if (y < h) {
                        blk = (b == Biome.DESERT) ? Blocks.SAND
                            : (b == Biome.BEACH || b == Biome.OCEAN) ? Blocks.SAND
                            : (b == Biome.MOUNTAINS && peak) ? Blocks.STONE : Blocks.DIRT;
                    } else if (y == h) {
                        if (peak) blk = Blocks.SNOW;
                        else if (b == Biome.DESERT) blk = Blocks.SAND;
                        else if (b == Biome.SNOWY) blk = Blocks.SNOW;
                        else if (b == Biome.BEACH || b == Biome.OCEAN) blk = Blocks.SAND;
                        else if (b == Biome.MOUNTAINS && h > SEA_LEVEL + 34) blk = Blocks.STONE;
                        else blk = Blocks.GRASS;
                    } else if (y <= SEA_LEVEL) {
                        blk = (b == Biome.SNOWY && y == SEA_LEVEL) ? Blocks.ICE : Blocks.WATER;
                    }

                    if (blk == Blocks.STONE || blk == Blocks.DIRT) {
                        float cv = cave.fbm3(wx * 0.04f, y * 0.06f, wz * 0.04f, 3);
                        if (cv > 0.55f && y > 2 && y < h - 1) blk = Blocks.AIR;
                    }
                    int preOre = blk;
                    if (blk == Blocks.STONE) {
                        float o = rnd(wx, y, wz, 99);
                        if (y < 14 && o > 0.992f) blk = Blocks.DIAMOND_ORE;
                        else if (y < 22 && o > 0.993f && b == Biome.MOUNTAINS) blk = Blocks.EMERALD_ORE;
                        else if (y < 24 && o > 0.990f) blk = Blocks.GOLD_ORE;
                        else if (y < 24 && o < 0.006f) blk = Blocks.LAPIS_ORE;
                        else if (y < 30 && o > 0.989f) blk = Blocks.REDSTONE_ORE;
                        else if (y < 48 && o > 0.985f) blk = Blocks.IRON_ORE;
                        else if (o > 0.975f) blk = Blocks.COAL_ORE;
                        else if (o < 0.010f) blk = Blocks.GRAVEL;
                    }
                    c.set(x, y, z, blk);
                    // Grow a small vein downward so ores cluster instead of dotting.
                    if (blk != preOre && y > 1 && c.get(x, y - 1, z) == Blocks.STONE && rnd(wx, y, wz, 150) > 0.35f)
                        c.set(x, y - 1, z, blk);
                }
            }

        // Vegetation (sparser than before, biome-appropriate wood).
        for (int z = 2; z < SZ - 2; z++)
            for (int x = 2; x < SX - 2; x++) {
                int wx = baseX + x, wz = baseZ + z;
                Sample s = col[z * SX + x];
                int h = s.h;
                if (h <= SEA_LEVEL || h + 1 >= SY) continue;
                int surface = c.get(x, h, z);
                if (surface != Blocks.GRASS && surface != Blocks.SAND && surface != Blocks.SNOW) continue;

                float r = rnd(wx, 0, wz, 5);
                float density = switch (s.biome) {
                    case FOREST -> 0.030f;
                    case TAIGA -> 0.035f;
                    case PLAINS -> 0.004f;
                    case SNOWY -> 0.010f;
                    case DESERT -> 0.008f;
                    default -> 0f;
                };
                if (r < density) placeTree(c, x, h + 1, z, s.biome, wx, wz);
                else if (surface == Blocks.GRASS) {
                    float pr = rnd(wx, 1, wz, 6);
                    if (pr > 0.90f) c.set(x, h + 1, z, Blocks.TALLGRASS);
                    else if (pr > 0.88f) c.set(x, h + 1, z, Blocks.FLOWER);
                }
            }
    }

    private void placeTree(Chunk c, int lx, int topY, int lz, Biome b, int wx, int wz) {
        if (b == Biome.DESERT) {
            int hgt = 2 + (int) (rnd(lx, topY, lz, 7) * 2);
            for (int i = 1; i <= hgt; i++) if (topY + i < SY) c.set(lx, topY + i, lz, Blocks.CACTUS);
            return;
        }
        int log = (b == Biome.TAIGA || b == Biome.SNOWY) ? Blocks.SPRUCE_LOG
                : (b == Biome.FOREST && rnd(wx, 5, wz, 8) > 0.6f) ? Blocks.BIRCH_LOG : Blocks.LOG;
        boolean conifer = (log == Blocks.SPRUCE_LOG);
        int trunk = (conifer ? 5 : 4) + (int) (rnd(lx, topY, lz, 1) * 3);
        int crownY = topY + trunk;

        if (conifer) {
            // Layered spruce canopy.
            for (int dy = 0; dy < 4; dy++) {
                int r = (dy == 0) ? 2 : (dy < 3 ? 1 : 0);
                int y = crownY - dy;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r) continue;
                        int xx = lx + dx, zz = lz + dz;
                        if (inBounds(xx, y, zz) && c.get(xx, y, zz) == Blocks.AIR) c.set(xx, y, zz, Blocks.LEAVES);
                    }
            }
            if (crownY + 1 < SY) c.set(lx, crownY + 1, lz, Blocks.LEAVES);
        } else {
            for (int dy = -2; dy <= 1; dy++) {
                int r = dy <= -1 ? 2 : 1, y = crownY + dy;
                if (y < 0 || y >= SY) continue;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r && rnd(lx + dx, y, lz + dz, 2) < 0.5f) continue;
                        int xx = lx + dx, zz = lz + dz;
                        if (inBounds(xx, y, zz) && c.get(xx, y, zz) == Blocks.AIR) c.set(xx, y, zz, Blocks.LEAVES);
                    }
            }
        }
        for (int i = 0; i < trunk; i++) if (topY + i < SY) c.set(lx, topY + i, lz, log);
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
                        if (d < 0.18f) {
                            blk = Blocks.NETHERRACK;
                            if (rnd(wx, y, wz, 11) > 0.985f) blk = Blocks.QUARTZ;
                            if (y < lavaLevel + 2 && rnd(wx, y, wz, 12) > 0.97f) blk = Blocks.MAGMA;
                        } else if (y <= lavaLevel) blk = Blocks.LAVA;
                    }
                    c.set(x, y, z, blk);
                }
                if (rnd(wx, 7, wz, 20) > 0.98f)
                    for (int k = 0; k < 3; k++) { int yy = ceiling - 1 - k; if (c.get(x, yy, z) == Blocks.NETHERRACK) c.set(x, yy, z, Blocks.GLOWSTONE); }
                if (c.get(x, lavaLevel + 1, z) == Blocks.NETHERRACK && rnd(wx, 3, wz, 21) > 0.9f)
                    c.set(x, lavaLevel + 1, z, Blocks.SOUL_SAND);
            }
    }

    private void generateEnd(Chunk c) {
        int baseX = c.cx * SX, baseZ = c.cz * SZ;
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                int wx = baseX + x, wz = baseZ + z;
                float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                float island = height.fbm3(wx * 0.012f, 0, wz * 0.012f, 4);
                float threshold = dist < 40 ? -1.0f : dist < 80 ? 0.15f : 0.25f - height.fbm2(wx * 0.004f, wz * 0.004f, 2) * 0.2f;
                if (island > threshold) {
                    int top = 60 + (int) (island * 12);
                    int bottom = 52 - (int) ((island - threshold) * 18);
                    for (int y = bottom; y <= top; y++) c.set(x, y, z, Blocks.END_STONE);
                }
            }
    }
}
