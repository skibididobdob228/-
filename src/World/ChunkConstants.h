#pragma once
#include <cstdint>

namespace mc {

constexpr int CHUNK_SX = 16;
constexpr int CHUNK_SZ = 16;
constexpr int CHUNK_SY = 128;          // world height
constexpr int SEA_LEVEL = 48;
constexpr int CHUNK_VOLUME = CHUNK_SX * CHUNK_SY * CHUNK_SZ;

inline int blockIndex(int x, int y, int z) {
    return (y * CHUNK_SZ + z) * CHUNK_SX + x;
}

inline bool inChunkBounds(int x, int y, int z) {
    return x >= 0 && x < CHUNK_SX && y >= 0 && y < CHUNK_SY && z >= 0 && z < CHUNK_SZ;
}

} // namespace mc
