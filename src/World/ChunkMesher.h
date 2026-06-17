#pragma once

namespace mc {

class Chunk;

// Neighbour chunks needed to cull faces and sample light at chunk borders.
// Any pointer may be null (treated as open air at that edge).
struct ChunkNeighbors {
    Chunk* west = nullptr;  // -X
    Chunk* east = nullptr;  // +X
    Chunk* north = nullptr; // -Z
    Chunk* south = nullptr; // +Z
};

// Builds the opaque + transparent meshes for `chunk` into chunk.mesh.
// Pure CPU work — safe to run on a worker thread.
void buildChunkMesh(Chunk& chunk, const ChunkNeighbors& nb);

} // namespace mc
