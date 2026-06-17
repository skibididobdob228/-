#pragma once
#include <cstdint>
#include <string>
#include <array>

namespace mc {

using BlockId = uint8_t;

// Stable numeric ids. Order matters for save-file compatibility.
enum : BlockId {
    BLOCK_AIR = 0,
    BLOCK_STONE,
    BLOCK_GRASS,
    BLOCK_DIRT,
    BLOCK_COBBLE,
    BLOCK_PLANKS,
    BLOCK_SAND,
    BLOCK_LOG,
    BLOCK_LEAVES,
    BLOCK_WATER,
    BLOCK_GLASS,
    BLOCK_BEDROCK,
    BLOCK_GRAVEL,
    BLOCK_COAL_ORE,
    BLOCK_IRON_ORE,
    BLOCK_GOLD_ORE,
    BLOCK_DIAMOND_ORE,
    BLOCK_SNOW,
    BLOCK_BRICK,
    BLOCK_GLOWSTONE,
    BLOCK_PLANK_SLAB_UNUSED, // reserved
    BLOCK_PUMPKIN,
    BLOCK_CACTUS,
    BLOCK_FLOWER,            // cross-shaped plant (cutout)
    BLOCK_TALLGRASS,         // cross-shaped plant (cutout)
    BLOCK_TORCH,             // emissive cross-shaped (simplified as small cube)
    BLOCK_COUNT
};

// How a block renders / interacts. `RenderKind` decides the meshing path.
enum class RenderKind : uint8_t {
    Air,        // nothing
    Solid,      // full opaque cube
    Transparent,// full cube but see-through (glass, leaves) — cutout
    Liquid,     // water-style translucent, slightly lowered top
    Cross,      // two crossed quads (flowers, grass)
};

struct BlockDef {
    const char* name = "air";
    RenderKind render = RenderKind::Air;
    bool solid = false;        // collides with the player
    bool opaque = false;       // blocks light & hides neighbour faces
    uint8_t lightEmission = 0; // 0..15 block-light emitted
    float hardness = 0.0f;     // seconds-ish to break by hand (0 = instant, <0 = unbreakable)
    // Atlas tile indices for top / side / bottom faces.
    uint16_t texTop = 0;
    uint16_t texSide = 0;
    uint16_t texBottom = 0;
};

// Global block table. Populated once at startup.
class Blocks {
public:
    static void init();
    static const BlockDef& get(BlockId id) { return defs_[id]; }
    static bool isOpaque(BlockId id) { return defs_[id].opaque; }
    static bool isSolid(BlockId id) { return defs_[id].solid; }
    static bool isAir(BlockId id) { return id == BLOCK_AIR; }
    static bool isLiquid(BlockId id) { return defs_[id].render == RenderKind::Liquid; }
    static RenderKind render(BlockId id) { return defs_[id].render; }

private:
    static std::array<BlockDef, BLOCK_COUNT> defs_;
};

} // namespace mc
