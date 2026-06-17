#include "World/World.h"
#include "World/ChunkMesher.h"
#include "Render/Shader.h"
#include "Render/Camera.h"
#include "Core/Logger.h"
#include <GL/glew.h>
#include <glm/gtc/matrix_transform.hpp>
#include <cmath>
#include <vector>
#include <algorithm>
#include <fstream>

namespace mc {

World::World(uint32_t seed) : seed_(seed), generator_(seed) {}

World::~World() {
    // Wait for outstanding jobs to drain so workers don't touch freed chunks.
    while (pool_.pending() > 0) {}
    for (auto& [k, c] : chunks_) {
        if (c->gpu.vaoOpaque) glDeleteVertexArrays(1, &c->gpu.vaoOpaque);
        if (c->gpu.vboOpaque) glDeleteBuffers(1, &c->gpu.vboOpaque);
        if (c->gpu.vaoTransp) glDeleteVertexArrays(1, &c->gpu.vaoTransp);
        if (c->gpu.vboTransp) glDeleteBuffers(1, &c->gpu.vboTransp);
    }
}

Chunk* World::getChunk(int cx, int cz) const {
    auto it = chunks_.find(key(cx, cz));
    return it == chunks_.end() ? nullptr : it->second.get();
}

Chunk* World::getOrCreateChunk(int cx, int cz) {
    Key k = key(cx, cz);
    auto it = chunks_.find(k);
    if (it != chunks_.end()) return it->second.get();
    auto c = std::make_unique<Chunk>(cx, cz);
    Chunk* ptr = c.get();
    chunks_.emplace(k, std::move(c));
    return ptr;
}

static void floorDiv(int a, int b, int& q, int& r) {
    q = a / b;
    r = a % b;
    if (r < 0) { r += b; --q; }
}

BlockId World::getBlock(int wx, int wy, int wz) const {
    if (wy < 0 || wy >= CHUNK_SY) return BLOCK_AIR;
    int cx, lx, cz, lz;
    floorDiv(wx, CHUNK_SX, cx, lx);
    floorDiv(wz, CHUNK_SZ, cz, lz);
    Chunk* c = getChunk(cx, cz);
    if (!c || c->state.load() < ChunkState::Generated) return BLOCK_AIR;
    return c->get(lx, wy, lz);
}

void World::markDirtyWithNeighbors(int cx, int cz, int lx, int lz) {
    if (Chunk* c = getChunk(cx, cz)) c->dirty.store(true);
    if (lx == 0)             if (Chunk* n = getChunk(cx - 1, cz)) n->dirty.store(true);
    if (lx == CHUNK_SX - 1)  if (Chunk* n = getChunk(cx + 1, cz)) n->dirty.store(true);
    if (lz == 0)             if (Chunk* n = getChunk(cx, cz - 1)) n->dirty.store(true);
    if (lz == CHUNK_SZ - 1)  if (Chunk* n = getChunk(cx, cz + 1)) n->dirty.store(true);
}

bool World::setBlock(int wx, int wy, int wz, BlockId id) {
    if (wy < 0 || wy >= CHUNK_SY) return false;
    int cx, lx, cz, lz;
    floorDiv(wx, CHUNK_SX, cx, lx);
    floorDiv(wz, CHUNK_SZ, cz, lz);
    Chunk* c = getChunk(cx, cz);
    if (!c || c->state.load() < ChunkState::Generated) return false;
    c->set(lx, wy, lz, id);
    editedStore_[key(cx, cz)] = c->blocks(); // persist the edit
    markDirtyWithNeighbors(cx, cz, lx, lz);
    return true;
}

int World::findSpawnHeight(int wx, int wz) const {
    Biome b;
    int h = generator_.surfaceHeight(wx, wz, b);
    return std::max(h, SEA_LEVEL) + 2;
}

RaycastHit World::raycast(const glm::vec3& origin, const glm::vec3& dir, float maxDist) const {
    // Amanatides & Woo voxel traversal.
    RaycastHit r;
    glm::vec3 d = glm::normalize(dir);
    glm::ivec3 p(std::floor(origin.x), std::floor(origin.y), std::floor(origin.z));
    glm::ivec3 step(d.x > 0 ? 1 : -1, d.y > 0 ? 1 : -1, d.z > 0 ? 1 : -1);
    glm::vec3 tMax, tDelta;
    for (int i = 0; i < 3; ++i) {
        if (d[i] == 0) { tMax[i] = 1e30f; tDelta[i] = 1e30f; }
        else {
            float voxelBoundary = p[i] + (step[i] > 0 ? 1 : 0);
            tMax[i] = (voxelBoundary - origin[i]) / d[i];
            tDelta[i] = std::abs(1.0f / d[i]);
        }
    }
    glm::ivec3 lastNormal(0);
    float dist = 0;
    while (dist <= maxDist) {
        BlockId b = getBlock(p.x, p.y, p.z);
        if (b != BLOCK_AIR && Blocks::get(b).render != RenderKind::Liquid) {
            r.hit = true;
            r.block = p;
            r.normal = lastNormal;
            r.previous = p + lastNormal;
            return r;
        }
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            p.x += step.x; dist = tMax.x; tMax.x += tDelta.x; lastNormal = glm::ivec3(-step.x, 0, 0);
        } else if (tMax.y < tMax.z) {
            p.y += step.y; dist = tMax.y; tMax.y += tDelta.y; lastNormal = glm::ivec3(0, -step.y, 0);
        } else {
            p.z += step.z; dist = tMax.z; tMax.z += tDelta.z; lastNormal = glm::ivec3(0, 0, -step.z);
        }
    }
    return r;
}

void World::uploadMesh(Chunk& c) {
    auto upload = [](unsigned& vao, unsigned& vbo, const std::vector<Vertex>& v, int& count) {
        if (vao == 0) glGenVertexArrays(1, &vao);
        if (vbo == 0) glGenBuffers(1, &vbo);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, v.size() * sizeof(Vertex),
                     v.empty() ? nullptr : v.data(), GL_STATIC_DRAW);
        const int stride = sizeof(Vertex);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, stride, (void*)offsetof(Vertex, x));
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, stride, (void*)offsetof(Vertex, u));
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, stride, (void*)offsetof(Vertex, nx));
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, GL_FALSE, stride, (void*)offsetof(Vertex, ao));
        glBindVertexArray(0);
        count = (int)v.size();
    };
    upload(c.gpu.vaoOpaque, c.gpu.vboOpaque, c.mesh.opaque, c.gpu.opaqueCount);
    upload(c.gpu.vaoTransp, c.gpu.vboTransp, c.mesh.transparent, c.gpu.transpCount);
    c.gpu.uploaded = true;
    // Free CPU mesh memory now that it lives on the GPU.
    c.mesh.opaque.clear(); c.mesh.opaque.shrink_to_fit();
    c.mesh.transparent.clear(); c.mesh.transparent.shrink_to_fit();
}

void World::update(const glm::vec3& center, int renderDistance) {
    int pcx, pcz, dummy;
    floorDiv((int)std::floor(center.x), CHUNK_SX, pcx, dummy);
    floorDiv((int)std::floor(center.z), CHUNK_SZ, pcz, dummy);

    // 1) Ensure chunks within render distance exist and start generation.
    int genBudget = 4; // limit new generation jobs per frame
    for (int dz = -renderDistance; dz <= renderDistance && genBudget > 0; ++dz)
        for (int dx = -renderDistance; dx <= renderDistance && genBudget > 0; ++dx) {
            int cx = pcx + dx, cz = pcz + dz;
            if (dx * dx + dz * dz > (renderDistance + 1) * (renderDistance + 1)) continue;
            Chunk* c = getChunk(cx, cz);
            if (!c) c = getOrCreateChunk(cx, cz);
            if (c->state.load() == ChunkState::Empty) {
                c->state.store(ChunkState::Generating);
                Key k = key(cx, cz);
                Chunk* ptr = c;
                pool_.enqueue([this, ptr, k] {
                    auto it = editedStore_.find(k);
                    if (it != editedStore_.end()) {
                        ptr->blocks() = it->second;
                    } else {
                        generator_.generate(*ptr);
                    }
                    ptr->state.store(ChunkState::Generated);
                });
                --genBudget;
            }
        }

    // 2) Queue meshing for generated/dirty chunks.
    int meshBudget = 6;
    for (auto& [k, cptr] : chunks_) {
        if (meshBudget <= 0) break;
        Chunk* c = cptr.get();
        ChunkState s = c->state.load();
        if ((s == ChunkState::Generated || s == ChunkState::Ready) && c->dirty.load()) {
            c->dirty.store(false);
            c->state.store(ChunkState::Meshing);
            ChunkNeighbors nb;
            nb.west  = getChunk(c->cx() - 1, c->cz());
            nb.east  = getChunk(c->cx() + 1, c->cz());
            nb.north = getChunk(c->cx(), c->cz() - 1);
            nb.south = getChunk(c->cx(), c->cz() + 1);
            Chunk* ptr = c;
            pool_.enqueue([ptr, nb] {
                ptr->computeLight();
                buildChunkMesh(*ptr, nb);
                ptr->meshReady.store(true);
                ptr->state.store(ChunkState::Ready);
            });
            --meshBudget;
        }
    }
    // Generated chunks start dirty so they get meshed once.
    for (auto& [k, cptr] : chunks_) {
        Chunk* c = cptr.get();
        if (c->state.load() == ChunkState::Generated && !c->dirty.load() && !c->meshReady.load())
            c->dirty.store(true);
    }

    // 3) Upload completed meshes (bounded per frame to avoid hitches).
    int uploadBudget = 4;
    for (auto& [k, cptr] : chunks_) {
        if (uploadBudget <= 0) break;
        Chunk* c = cptr.get();
        if (c->meshReady.load() && c->state.load() == ChunkState::Ready) {
            uploadMesh(*c);
            c->meshReady.store(false);
            --uploadBudget;
        }
    }

    // 4) Unload chunks well outside render distance (keep edited data in store).
    int unloadDist = renderDistance + 3;
    std::vector<Key> toRemove;
    for (auto& [k, cptr] : chunks_) {
        Chunk* c = cptr.get();
        int ddx = c->cx() - pcx, ddz = c->cz() - pcz;
        if (std::abs(ddx) > unloadDist || std::abs(ddz) > unloadDist) {
            ChunkState s = c->state.load();
            if (s == ChunkState::Generating || s == ChunkState::Meshing) continue;
            if (c->meshReady.load()) continue;
            toRemove.push_back(k);
        }
    }
    for (Key k : toRemove) {
        Chunk* c = chunks_[k].get();
        if (c->gpu.vaoOpaque) glDeleteVertexArrays(1, &c->gpu.vaoOpaque);
        if (c->gpu.vboOpaque) glDeleteBuffers(1, &c->gpu.vboOpaque);
        if (c->gpu.vaoTransp) glDeleteVertexArrays(1, &c->gpu.vaoTransp);
        if (c->gpu.vboTransp) glDeleteBuffers(1, &c->gpu.vboTransp);
        chunks_.erase(k);
    }
}

void World::renderOpaque(Shader& shader, Camera& camera) {
    camera.updateFrustum();
    lastDrawn_ = 0;
    for (auto& [k, cptr] : chunks_) {
        Chunk* c = cptr.get();
        if (!c->gpu.uploaded || c->gpu.opaqueCount == 0) continue;
        glm::vec3 mn, mx; c->aabb(mn, mx);
        if (!camera.aabbVisible(mn, mx)) continue;
        glm::mat4 model = glm::translate(glm::mat4(1.0f), c->worldOrigin());
        shader.set("uModel", model);
        glBindVertexArray(c->gpu.vaoOpaque);
        glDrawArrays(GL_TRIANGLES, 0, c->gpu.opaqueCount);
        ++lastDrawn_;
    }
    glBindVertexArray(0);
}

void World::renderTransparent(Shader& shader, Camera& camera) {
    // Sort chunks back-to-front by distance for correct blending.
    std::vector<std::pair<float, Chunk*>> list;
    for (auto& [k, cptr] : chunks_) {
        Chunk* c = cptr.get();
        if (!c->gpu.uploaded || c->gpu.transpCount == 0) continue;
        glm::vec3 mn, mx; c->aabb(mn, mx);
        if (!camera.aabbVisible(mn, mx)) continue;
        glm::vec3 cc = (mn + mx) * 0.5f;
        list.emplace_back(glm::distance(cc, camera.position), c);
    }
    std::sort(list.begin(), list.end(),
              [](auto& a, auto& b) { return a.first > b.first; });
    for (auto& [dist, c] : list) {
        glm::mat4 model = glm::translate(glm::mat4(1.0f), c->worldOrigin());
        shader.set("uModel", model);
        glBindVertexArray(c->gpu.vaoTransp);
        glDrawArrays(GL_TRIANGLES, 0, c->gpu.transpCount);
    }
    glBindVertexArray(0);
}

bool World::save(const std::string& path) const {
    std::ofstream f(path, std::ios::binary);
    if (!f) return false;
    const char magic[4] = {'M', 'C', 'W', '1'};
    f.write(magic, 4);
    f.write((const char*)&seed_, sizeof(seed_));
    uint32_t n = (uint32_t)editedStore_.size();
    f.write((const char*)&n, sizeof(n));
    for (auto& [k, blocks] : editedStore_) {
        f.write((const char*)&k, sizeof(k));
        // Simple RLE over the block array.
        uint32_t count = (uint32_t)blocks.size();
        f.write((const char*)&count, sizeof(count));
        size_t i = 0;
        while (i < blocks.size()) {
            BlockId id = blocks[i];
            uint16_t run = 1;
            while (i + run < blocks.size() && blocks[i + run] == id && run < 65535) ++run;
            f.write((const char*)&id, sizeof(id));
            f.write((const char*)&run, sizeof(run));
            i += run;
        }
    }
    return true;
}

bool World::load(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    char magic[4];
    f.read(magic, 4);
    if (magic[0] != 'M' || magic[1] != 'C' || magic[2] != 'W') return false;
    uint32_t savedSeed;
    f.read((char*)&savedSeed, sizeof(savedSeed));
    seed_ = savedSeed;
    generator_ = TerrainGenerator(savedSeed);
    uint32_t n;
    f.read((char*)&n, sizeof(n));
    editedStore_.clear();
    for (uint32_t e = 0; e < n; ++e) {
        Key k; f.read((char*)&k, sizeof(k));
        uint32_t count; f.read((char*)&count, sizeof(count));
        std::vector<BlockId> blocks; blocks.reserve(count);
        while (blocks.size() < count) {
            BlockId id; uint16_t run;
            f.read((char*)&id, sizeof(id));
            f.read((char*)&run, sizeof(run));
            for (uint16_t r = 0; r < run && blocks.size() < count; ++r) blocks.push_back(id);
        }
        editedStore_[k] = std::move(blocks);
    }
    LOG_INFO("Loaded world: seed=%u, %u edited chunks", seed_, n);
    return true;
}

} // namespace mc
