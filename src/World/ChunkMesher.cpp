#include "World/ChunkMesher.h"
#include "World/Chunk.h"
#include "World/Block.h"
#include "Render/TileIds.h"
#include <array>
#include <cmath>

namespace mc {

namespace {

// 6 cube faces. base = corner where (u,v)=(0,0); du,dv = in-plane unit steps.
struct Face {
    int nx, ny, nz;
    int base[3];
    int du[3];
    int dv[3];
};

const Face FACES[6] = {
    {  1, 0, 0, {1,0,0}, {0,0,1}, {0,1,0} }, // +X
    { -1, 0, 0, {0,0,1}, {0,0,-1},{0,1,0} }, // -X
    {  0, 1, 0, {0,1,1}, {1,0,0}, {0,0,-1}}, // +Y (top)
    {  0,-1, 0, {0,0,0}, {1,0,0}, {0,0,1} }, // -Y (bottom)
    {  0, 0, 1, {1,0,1}, {-1,0,0},{0,1,0} }, // +Z
    {  0, 0,-1, {0,0,0}, {1,0,0}, {0,1,0} }, // -Z
};

// Per-face brightness so flat-lit faces still read as 3D (Minecraft-style).
const float FACE_SHADE[6] = { 0.78f, 0.78f, 1.0f, 0.55f, 0.65f, 0.65f };

float aoLevel(bool s1, bool s2, bool corner) {
    if (s1 && s2) return 0.0f;
    int n = (s1 ? 1 : 0) + (s2 ? 1 : 0) + (corner ? 1 : 0);
    return float(3 - n);
}
float aoToBrightness(float ao) {
    static const float lut[4] = {0.45f, 0.62f, 0.80f, 1.0f};
    int i = (int)ao;
    if (i < 0) i = 0; if (i > 3) i = 3;
    return lut[i];
}

// Resolves a local coordinate possibly outside the centre chunk to the right
// neighbour. Returns air when a needed (esp. diagonal) neighbour is missing.
struct Access {
    Chunk* c;
    const ChunkNeighbors& nb;

    BlockId block(int x, int y, int z) const {
        if (y < 0 || y >= CHUNK_SY) return BLOCK_AIR;
        bool xo = (x < 0 || x >= CHUNK_SX);
        bool zo = (z < 0 || z >= CHUNK_SZ);
        if (xo && zo) return BLOCK_AIR;
        if (!xo && !zo) return c->get(x, y, z);
        if (xo) {
            Chunk* n = (x < 0) ? nb.west : nb.east;
            if (!n) return BLOCK_AIR;
            return n->get((x + CHUNK_SX) % CHUNK_SX, y, z);
        }
        Chunk* n = (z < 0) ? nb.north : nb.south;
        if (!n) return BLOCK_AIR;
        return n->get(x, y, (z + CHUNK_SZ) % CHUNK_SZ);
    }
    void light(int x, int y, int z, int& sky, int& blk) const {
        if (y < 0) { sky = 0; blk = 0; return; }
        if (y >= CHUNK_SY) { sky = 15; blk = 0; return; }
        bool xo = (x < 0 || x >= CHUNK_SX);
        bool zo = (z < 0 || z >= CHUNK_SZ);
        if (xo && zo) { sky = 15; blk = 0; return; }
        Chunk* n = c;
        int lx = x, lz = z;
        if (xo) { n = (x < 0) ? nb.west : nb.east; lx = (x + CHUNK_SX) % CHUNK_SX; }
        else if (zo) { n = (z < 0) ? nb.north : nb.south; lz = (z + CHUNK_SZ) % CHUNK_SZ; }
        if (!n) { sky = 15; blk = 0; return; }
        sky = n->skyLight(lx, y, lz);
        blk = n->blockLight(lx, y, lz);
    }
    bool opaque(int x, int y, int z) const { return Blocks::isOpaque(block(x, y, z)); }
};

// Whether a face between `self` and `neighbour` should be emitted.
bool faceVisible(BlockId self, BlockId nb) {
    if (nb == BLOCK_AIR) return true;
    if (Blocks::isOpaque(nb)) return false;
    if (nb == self) return false;       // hide internal faces of same translucent type
    return true;
}

void atlasUV(uint16_t tile, float s, float t, float& u, float& v) {
    int col = tile % ATLAS_TILES;
    int row = tile / ATLAS_TILES;
    const float inv = 1.0f / ATLAS_PX;
    const float inset = 0.5f * inv; // avoid mipmap bleed
    float tileSize = float(TILE_PX) * inv;
    u = col * tileSize + inset + s * (tileSize - 2 * inset);
    v = row * tileSize + inset + t * (tileSize - 2 * inset);
}

uint16_t faceTile(const BlockDef& def, int faceIdx) {
    if (faceIdx == 2) return def.texTop;     // +Y
    if (faceIdx == 3) return def.texBottom;  // -Y
    return def.texSide;
}

} // namespace

void buildChunkMesh(Chunk& chunk, const ChunkNeighbors& nb) {
    chunk.mesh.opaque.clear();
    chunk.mesh.transparent.clear();
    Access acc{&chunk, nb};

    for (int y = 0; y < CHUNK_SY; ++y)
        for (int z = 0; z < CHUNK_SZ; ++z)
            for (int x = 0; x < CHUNK_SX; ++x) {
                BlockId id = chunk.get(x, y, z);
                if (id == BLOCK_AIR) continue;
                const BlockDef& def = Blocks::get(id);
                RenderKind kind = def.render;

                bool transparentMesh = (kind != RenderKind::Solid);
                auto& out = transparentMesh ? chunk.mesh.transparent : chunk.mesh.opaque;

                if (kind == RenderKind::Cross) {
                    // Two crossed quads. Light from this cell.
                    int sky, blk; acc.light(x, y, z, sky, blk);
                    float fsky = sky / 15.0f, fblk = blk / 15.0f;
                    float u0, v0, u1, v1;
                    atlasUV(def.texSide, 0, 1, u0, v1);
                    atlasUV(def.texSide, 1, 0, u1, v0);
                    const float p = 0.146f; // inset so quads fit the cell diagonal
                    struct QV { float x, z; float u; };
                    // Quad A: (p,p)->(1-p,1-p)
                    glm::vec3 A0(x + p, y, z + p), A1(x + 1 - p, y, z + 1 - p);
                    glm::vec3 B0(x + 1 - p, y, z + p), B1(x + p, y, z + 1 - p);
                    auto emitCross = [&](glm::vec3 a, glm::vec3 b) {
                        float top = y + 1.0f;
                        Vertex va{a.x, a.y, a.z, u0, v1, 0,1,0, 1.0f, fsky, fblk};
                        Vertex vb{b.x, b.y, b.z, u1, v1, 0,1,0, 1.0f, fsky, fblk};
                        Vertex vc{b.x, top, b.z, u1, v0, 0,1,0, 1.0f, fsky, fblk};
                        Vertex vd{a.x, top, a.z, u0, v0, 0,1,0, 1.0f, fsky, fblk};
                        out.push_back(va); out.push_back(vb); out.push_back(vc);
                        out.push_back(va); out.push_back(vc); out.push_back(vd);
                    };
                    emitCross(A0, A1);
                    emitCross(B0, B1);
                    continue;
                }

                for (int f = 0; f < 6; ++f) {
                    const Face& face = FACES[f];
                    int nxp = x + face.nx, nyp = y + face.ny, nzp = z + face.nz;
                    BlockId neighbour = acc.block(nxp, nyp, nzp);
                    if (!faceVisible(id, neighbour)) continue;

                    uint16_t tile = faceTile(def, f);
                    float shade = FACE_SHADE[f];

                    // Liquid surface: lower the top face when open above.
                    float topDrop = 0.0f;
                    bool isWaterTop = (kind == RenderKind::Liquid && f == 2);
                    if (isWaterTop) topDrop = 0.12f;

                    Vertex quad[4];
                    float aoVals[4];
                    for (int corner = 0; corner < 4; ++corner) {
                        int cu = (corner == 1 || corner == 2) ? 1 : 0;
                        int cv = (corner >= 2) ? 1 : 0;
                        float px = x + face.base[0] + cu * face.du[0] + cv * face.dv[0];
                        float py = y + face.base[1] + cu * face.du[1] + cv * face.dv[1];
                        float pz = z + face.base[2] + cu * face.du[2] + cv * face.dv[2];
                        if (isWaterTop) py -= topDrop;

                        // AO sampling around this corner in the neighbour plane.
                        int suX = (cu ? face.du[0] : -face.du[0]);
                        int suY = (cu ? face.du[1] : -face.du[1]);
                        int suZ = (cu ? face.du[2] : -face.du[2]);
                        int svX = (cv ? face.dv[0] : -face.dv[0]);
                        int svY = (cv ? face.dv[1] : -face.dv[1]);
                        int svZ = (cv ? face.dv[2] : -face.dv[2]);
                        int bx = x + face.nx, by = y + face.ny, bz = z + face.nz;
                        bool s1 = acc.opaque(bx + suX, by + suY, bz + suZ);
                        bool s2 = acc.opaque(bx + svX, by + svY, bz + svZ);
                        bool cc = acc.opaque(bx + suX + svX, by + suY + svY, bz + suZ + svZ);
                        float ao = aoLevel(s1, s2, cc);
                        aoVals[corner] = ao;

                        // Smooth light: average non-opaque cells around the corner.
                        int accSky = 0, accBlk = 0, cnt = 0;
                        auto sample = [&](int ox, int oy, int oz) {
                            if (acc.opaque(bx + ox, by + oy, bz + oz)) return;
                            int s, b; acc.light(bx + ox, by + oy, bz + oz, s, b);
                            accSky += s; accBlk += b; ++cnt;
                        };
                        sample(0, 0, 0);
                        sample(suX, suY, suZ);
                        sample(svX, svY, svZ);
                        sample(suX + svX, suY + svY, suZ + svZ);
                        if (cnt == 0) { acc.light(bx, by, bz, accSky, accBlk); cnt = 1; }
                        float fsky = (accSky / float(cnt)) / 15.0f;
                        float fblk = (accBlk / float(cnt)) / 15.0f;

                        float s = float(cu), t = (cv ? 0.0f : 1.0f);
                        float uu, vv; atlasUV(tile, s, t, uu, vv);

                        quad[corner] = Vertex{
                            px, py, pz, uu, vv,
                            float(face.nx), float(face.ny), float(face.nz),
                            aoToBrightness(ao) * shade, fsky, fblk};
                    }

                    // Flip the quad's diagonal to avoid AO interpolation seams.
                    bool flip = (aoVals[0] + aoVals[2]) < (aoVals[1] + aoVals[3]);
                    if (flip) {
                        out.push_back(quad[1]); out.push_back(quad[2]); out.push_back(quad[3]);
                        out.push_back(quad[1]); out.push_back(quad[3]); out.push_back(quad[0]);
                    } else {
                        out.push_back(quad[0]); out.push_back(quad[1]); out.push_back(quad[2]);
                        out.push_back(quad[0]); out.push_back(quad[2]); out.push_back(quad[3]);
                    }
                }
            }
}

} // namespace mc
