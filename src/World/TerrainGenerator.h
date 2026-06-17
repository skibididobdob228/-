#pragma once
#include "Math/Noise.h"
#include "World/Block.h"
#include <cstdint>

namespace mc {

class Chunk;

enum class Biome : uint8_t { Ocean, Beach, Plains, Forest, Desert, Mountains, Snowy };

// Fills a chunk's voxels deterministically from a seed: terrain height by
// biome, water up to sea level, 3D-noise caves, ore veins, trees and plants.
class TerrainGenerator {
public:
    explicit TerrainGenerator(uint32_t seed);

    void generate(Chunk& chunk) const;

    int surfaceHeight(int worldX, int worldZ, Biome& outBiome) const;
    Biome biomeAt(int worldX, int worldZ) const;

private:
    uint32_t seed_;
    Noise heightNoise_;
    Noise biomeNoise_;
    Noise caveNoise_;
    Noise oreNoise_;

    void placeTree(Chunk& chunk, int lx, int topY, int lz, Biome biome) const;
};

} // namespace mc
