#pragma once
#include <cstdint>

namespace mc {

// Indices into the procedurally generated texture atlas. The atlas is a
// 16x16 grid of tiles; index = row*16 + col but we only care about the index.
enum Tile : uint16_t {
    TILE_STONE = 0,
    TILE_DIRT,
    TILE_GRASS_TOP,
    TILE_GRASS_SIDE,
    TILE_SAND,
    TILE_COBBLE,
    TILE_PLANKS,
    TILE_LOG_TOP,
    TILE_LOG_SIDE,
    TILE_LEAVES,
    TILE_WATER,
    TILE_GLASS,
    TILE_BEDROCK,
    TILE_GRAVEL,
    TILE_COAL_ORE,
    TILE_IRON_ORE,
    TILE_GOLD_ORE,
    TILE_DIAMOND_ORE,
    TILE_SNOW,
    TILE_BRICK,
    TILE_GLOWSTONE,
    TILE_PUMPKIN_TOP,
    TILE_PUMPKIN_SIDE,
    TILE_CACTUS_TOP,
    TILE_CACTUS_SIDE,
    TILE_FLOWER,
    TILE_TALLGRASS,
    TILE_TORCH,
    TILE_COUNT
};

constexpr int ATLAS_TILES = 16;     // tiles per row/col
constexpr int TILE_PX = 16;         // pixels per tile
constexpr int ATLAS_PX = ATLAS_TILES * TILE_PX;

} // namespace mc
