#include "World/TerrainGenerator.h"
#include "World/Chunk.h"
#include <cmath>
#include <algorithm>

namespace mc {

namespace {
inline uint32_t hash3(int x, int y, int z, uint32_t seed) {
    uint32_t n = uint32_t(x) * 374761393u + uint32_t(y) * 668265263u +
                 uint32_t(z) * 2147483647u + seed * 362437u;
    n = (n ^ (n >> 13)) * 1274126177u;
    return n ^ (n >> 16);
}
inline float rnd01(int x, int y, int z, uint32_t seed) {
    return (hash3(x, y, z, seed) & 0xFFFFFF) / float(0x1000000);
}
} // namespace

TerrainGenerator::TerrainGenerator(uint32_t seed)
    : seed_(seed),
      heightNoise_(seed),
      biomeNoise_(seed ^ 0x9E3779B9u),
      caveNoise_(seed ^ 0x85EBCA77u),
      oreNoise_(seed ^ 0xC2B2AE3Du) {}

Biome TerrainGenerator::biomeAt(int wx, int wz) const {
    float temp = biomeNoise_.fbm2(wx * 0.0035f, wz * 0.0035f, 3);
    float humid = biomeNoise_.fbm2((wx + 4000) * 0.0035f, (wz - 4000) * 0.0035f, 3);
    float cont = heightNoise_.fbm2(wx * 0.0015f, wz * 0.0015f, 3);

    if (cont < -0.30f) return Biome::Ocean;
    if (cont > 0.45f) return Biome::Mountains;
    if (temp > 0.35f && humid < -0.10f) return Biome::Desert;
    if (temp < -0.35f) return Biome::Snowy;
    if (humid > 0.15f) return Biome::Forest;
    return Biome::Plains;
}

int TerrainGenerator::surfaceHeight(int wx, int wz, Biome& outBiome) const {
    outBiome = biomeAt(wx, wz);
    float base = heightNoise_.fbm2(wx * 0.006f, wz * 0.006f, 5, 2.0f, 0.5f);
    float detail = heightNoise_.fbm2(wx * 0.03f, wz * 0.03f, 3) * 0.25f;
    float n = base + detail;

    int h;
    switch (outBiome) {
        case Biome::Ocean:     h = SEA_LEVEL - 8 + int(n * 6); break;
        case Biome::Beach:     h = SEA_LEVEL + int(n * 2); break;
        case Biome::Desert:    h = SEA_LEVEL + 2 + int(n * 8); break;
        case Biome::Plains:    h = SEA_LEVEL + 3 + int(n * 8); break;
        case Biome::Forest:    h = SEA_LEVEL + 4 + int(n * 10); break;
        case Biome::Snowy:     h = SEA_LEVEL + 4 + int(n * 10); break;
        case Biome::Mountains: h = SEA_LEVEL + 12 + int((n + 0.3f) * 40); break;
        default:               h = SEA_LEVEL; break;
    }
    return std::clamp(h, 4, CHUNK_SY - 20);
}

void TerrainGenerator::placeTree(Chunk& c, int lx, int topY, int lz, Biome biome) const {
    if (biome == Biome::Desert) {
        // Cactus column.
        int height = 2 + int(rnd01(lx, topY, lz, seed_ + 7) * 2);
        for (int i = 1; i <= height; ++i)
            if (topY + i < CHUNK_SY) c.set(lx, topY + i, lz, BLOCK_CACTUS);
        return;
    }
    int trunk = 4 + int(rnd01(lx, topY, lz, seed_ + 1) * 3);
    int crownY = topY + trunk;
    // Leaves canopy.
    for (int dy = -2; dy <= 1; ++dy) {
        int r = (dy <= -1) ? 2 : 1;
        int y = crownY + dy;
        if (y < 0 || y >= CHUNK_SY) continue;
        for (int dx = -r; dx <= r; ++dx)
            for (int dz = -r; dz <= r; ++dz) {
                if (std::abs(dx) == r && std::abs(dz) == r &&
                    rnd01(lx + dx, y, lz + dz, seed_ + 2) < 0.5f) continue;
                int x = lx + dx, z = lz + dz;
                if (!inChunkBounds(x, y, z)) continue;
                if (c.get(x, y, z) == BLOCK_AIR) c.set(x, y, z, BLOCK_LEAVES);
            }
    }
    // Trunk.
    for (int i = 0; i < trunk; ++i)
        if (topY + i < CHUNK_SY) c.set(lx, topY + i, lz, BLOCK_LOG);
}

void TerrainGenerator::generate(Chunk& chunk) const {
    const int baseX = chunk.cx() * CHUNK_SX;
    const int baseZ = chunk.cz() * CHUNK_SZ;

    // Terrain + water + surface decoration.
    for (int z = 0; z < CHUNK_SZ; ++z)
        for (int x = 0; x < CHUNK_SX; ++x) {
            int wx = baseX + x, wz = baseZ + z;
            Biome biome;
            int h = surfaceHeight(wx, wz, biome);

            for (int y = 0; y <= std::max(h, SEA_LEVEL); ++y) {
                BlockId b = BLOCK_AIR;
                if (y == 0) {
                    b = BLOCK_BEDROCK;
                } else if (y < h - 4) {
                    b = BLOCK_STONE;
                } else if (y < h) {
                    b = (biome == Biome::Desert) ? BLOCK_SAND : BLOCK_DIRT;
                } else if (y == h) {
                    // Top block by biome.
                    if (biome == Biome::Desert) b = BLOCK_SAND;
                    else if (biome == Biome::Snowy) b = BLOCK_SNOW;
                    else if (biome == Biome::Ocean || (h < SEA_LEVEL + 1 && biome != Biome::Mountains))
                        b = BLOCK_SAND;
                    else if (biome == Biome::Mountains && h > SEA_LEVEL + 40)
                        b = BLOCK_STONE;
                    else b = BLOCK_GRASS;
                } else if (y <= SEA_LEVEL) {
                    b = BLOCK_WATER;
                }

                // Caves: carve with 3D noise below the surface (not into water).
                if (b == BLOCK_STONE || b == BLOCK_DIRT) {
                    float cave = caveNoise_.fbm3(wx * 0.04f, y * 0.06f, wz * 0.04f, 3);
                    if (cave > 0.55f && y > 2 && y < h - 1) b = BLOCK_AIR;
                }

                // Ores by depth.
                if (b == BLOCK_STONE) {
                    float o = rnd01(wx, y, wz, seed_ + 99);
                    if (y < 16 && o > 0.992f) b = BLOCK_DIAMOND_ORE;
                    else if (y < 32 && o > 0.990f) b = BLOCK_GOLD_ORE;
                    else if (y < 48 && o > 0.985f) b = BLOCK_IRON_ORE;
                    else if (o > 0.975f) b = BLOCK_COAL_ORE;
                    else if (o < 0.010f) b = BLOCK_GRAVEL;
                }
                chunk.set(x, y, z, b);
            }
        }

    // Surface vegetation & trees (kept inside the chunk to avoid border writes).
    for (int z = 2; z < CHUNK_SZ - 2; ++z)
        for (int x = 2; x < CHUNK_SX - 2; ++x) {
            int wx = baseX + x, wz = baseZ + z;
            Biome biome;
            int h = surfaceHeight(wx, wz, biome);
            if (h < SEA_LEVEL) continue;
            BlockId surface = chunk.get(x, h, z);
            if (surface != BLOCK_GRASS && surface != BLOCK_SAND && surface != BLOCK_SNOW) continue;
            if (h + 1 >= CHUNK_SY) continue;

            float r = rnd01(wx, 0, wz, seed_ + 5);
            float treeDensity = (biome == Biome::Forest) ? 0.06f
                              : (biome == Biome::Plains) ? 0.012f
                              : (biome == Biome::Desert) ? 0.01f : 0.0f;
            if (r < treeDensity) {
                placeTree(chunk, x, h + 1, z, biome);
            } else if (surface == BLOCK_GRASS) {
                float pr = rnd01(wx, 1, wz, seed_ + 6);
                if (pr > 0.85f) chunk.set(x, h + 1, z, BLOCK_TALLGRASS);
                else if (pr > 0.82f) chunk.set(x, h + 1, z, BLOCK_FLOWER);
            }
        }
}

} // namespace mc
