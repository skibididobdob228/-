#pragma once
#include "World/Block.h"
#include "World/ChunkConstants.h"
#include <vector>
#include <atomic>
#include <cstdint>
#include <glm/glm.hpp>

namespace mc {

// A single mesh vertex: position, atlas UV, normal, and (ao, sky, block) light.
struct Vertex {
    float x, y, z;
    float u, v;
    float nx, ny, nz;
    float ao, sky, block;
};

struct MeshData {
    std::vector<Vertex> opaque;       // solid blocks
    std::vector<Vertex> transparent;  // glass, leaves, water, cross plants
    bool empty() const { return opaque.empty() && transparent.empty(); }
};

// GPU handles for one chunk's two meshes.
struct ChunkGpu {
    unsigned vaoOpaque = 0, vboOpaque = 0;
    unsigned vaoTransp = 0, vboTransp = 0;
    int opaqueCount = 0, transpCount = 0;
    bool uploaded = false;
};

enum class ChunkState : uint8_t {
    Empty,       // allocated, no voxels yet
    Generating,  // worker is filling voxels
    Generated,   // voxels ready, needs mesh
    Meshing,     // worker is building mesh
    Ready,       // mesh built (CPU), maybe needs GPU upload
};

class World;

class Chunk {
public:
    Chunk(int cx, int cz);

    int cx() const { return cx_; }
    int cz() const { return cz_; }

    BlockId get(int x, int y, int z) const {
        if (!inChunkBounds(x, y, z)) return BLOCK_AIR;
        return blocks_[blockIndex(x, y, z)];
    }
    void set(int x, int y, int z, BlockId id) {
        if (inChunkBounds(x, y, z)) blocks_[blockIndex(x, y, z)] = id;
    }

    uint8_t skyLight(int x, int y, int z) const {
        if (!inChunkBounds(x, y, z)) return 15;
        return skyLight_[blockIndex(x, y, z)];
    }
    uint8_t blockLight(int x, int y, int z) const {
        if (!inChunkBounds(x, y, z)) return 0;
        return blockLight_[blockIndex(x, y, z)];
    }

    // Recompute sky + block light using only this chunk's voxels (thread-safe).
    void computeLight();

    glm::vec3 worldOrigin() const {
        return glm::vec3(cx_ * CHUNK_SX, 0, cz_ * CHUNK_SZ);
    }
    void aabb(glm::vec3& mn, glm::vec3& mx) const {
        mn = worldOrigin();
        mx = mn + glm::vec3(CHUNK_SX, CHUNK_SY, CHUNK_SZ);
    }

    std::vector<BlockId>& blocks() { return blocks_; }
    const std::vector<BlockId>& blocks() const { return blocks_; }

    std::atomic<ChunkState> state{ChunkState::Empty};
    MeshData mesh;          // produced by worker, consumed by main thread
    ChunkGpu gpu;
    std::atomic<bool> dirty{false};   // needs remesh
    std::atomic<bool> meshReady{false}; // CPU mesh waiting for upload

private:
    int cx_, cz_;
    std::vector<BlockId> blocks_;
    std::vector<uint8_t> skyLight_;
    std::vector<uint8_t> blockLight_;
};

} // namespace mc
