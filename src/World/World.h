#pragma once
#include "World/Chunk.h"
#include "World/TerrainGenerator.h"
#include "Core/ThreadPool.h"
#include <unordered_map>
#include <memory>
#include <cstdint>
#include <string>
#include <glm/glm.hpp>

namespace mc {

class Shader;
class Camera;

struct RaycastHit {
    bool hit = false;
    glm::ivec3 block{0};      // the solid block hit
    glm::ivec3 previous{0};   // empty cell just before it (for placement)
    glm::ivec3 normal{0};
};

class World {
public:
    explicit World(uint32_t seed);
    ~World();

    // Stream chunks around the camera and push completed meshes to the GPU.
    void update(const glm::vec3& center, int renderDistance);

    BlockId getBlock(int wx, int wy, int wz) const;
    bool setBlock(int wx, int wy, int wz, BlockId id);

    RaycastHit raycast(const glm::vec3& origin, const glm::vec3& dir, float maxDist) const;

    void renderOpaque(Shader& shader, Camera& camera);
    void renderTransparent(Shader& shader, Camera& camera);

    bool save(const std::string& path) const;
    bool load(const std::string& path);

    uint32_t seed() const { return seed_; }
    int findSpawnHeight(int wx, int wz) const;

    size_t loadedChunks() const { return chunks_.size(); }
    int lastDrawn() const { return lastDrawn_; }

private:
    using Key = int64_t;
    static Key key(int cx, int cz) { return (Key(cx) << 32) ^ uint32_t(cz); }

    Chunk* getChunk(int cx, int cz) const;
    Chunk* getOrCreateChunk(int cx, int cz);
    void uploadMesh(Chunk& chunk);
    void markDirtyWithNeighbors(int cx, int cz, int lx, int lz);

    uint32_t seed_;
    TerrainGenerator generator_;
    ThreadPool pool_;
    mutable std::unordered_map<Key, std::unique_ptr<Chunk>> chunks_;
    std::unordered_map<Key, std::vector<BlockId>> editedStore_;

    int lastDrawn_ = 0;
};

} // namespace mc
