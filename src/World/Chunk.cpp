#include "World/Chunk.h"
#include <queue>
#include <cstring>

namespace mc {

Chunk::Chunk(int cx, int cz) : cx_(cx), cz_(cz) {
    blocks_.assign(CHUNK_VOLUME, BLOCK_AIR);
    skyLight_.assign(CHUNK_VOLUME, 0);
    blockLight_.assign(CHUNK_VOLUME, 0);
}

void Chunk::computeLight() {
    std::fill(skyLight_.begin(), skyLight_.end(), 0);
    std::fill(blockLight_.begin(), blockLight_.end(), 0);

    // --- Sky light: top-down columns, then horizontal BFS spread. ---
    std::queue<int> skyQ; // packed index
    for (int z = 0; z < CHUNK_SZ; ++z)
        for (int x = 0; x < CHUNK_SX; ++x) {
            int light = 15;
            for (int y = CHUNK_SY - 1; y >= 0; --y) {
                BlockId b = blocks_[blockIndex(x, y, z)];
                if (Blocks::isOpaque(b)) { light = 0; }
                else if (b != BLOCK_AIR) { if (light > 2) light -= 2; } // leaves/water dim
                skyLight_[blockIndex(x, y, z)] = (uint8_t)light;
                if (light == 15) skyQ.push(blockIndex(x, y, z));
            }
        }

    auto unpack = [](int idx, int& x, int& y, int& z) {
        x = idx % CHUNK_SX;
        z = (idx / CHUNK_SX) % CHUNK_SZ;
        y = idx / (CHUNK_SX * CHUNK_SZ);
    };
    const int dirs[6][3] = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    auto spread = [&](std::vector<uint8_t>& light, std::queue<int>& q) {
        while (!q.empty()) {
            int idx = q.front(); q.pop();
            int x, y, z; unpack(idx, x, y, z);
            int cur = light[idx];
            if (cur <= 1) continue;
            for (auto& d : dirs) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!inChunkBounds(nx, ny, nz)) continue;
                int ni = blockIndex(nx, ny, nz);
                if (Blocks::isOpaque(blocks_[ni])) continue;
                uint8_t want = (uint8_t)(cur - 1);
                if (light[ni] < want) { light[ni] = want; q.push(ni); }
            }
        }
    };
    spread(skyLight_, skyQ);

    // --- Block light: BFS from emissive voxels. ---
    std::queue<int> blockQ;
    for (int i = 0; i < CHUNK_VOLUME; ++i) {
        uint8_t e = Blocks::get(blocks_[i]).lightEmission;
        if (e > 0) { blockLight_[i] = e; blockQ.push(i); }
    }
    spread(blockLight_, blockQ);
}

} // namespace mc
