#include "World/Block.h"
#include "Render/TileIds.h"

namespace mc {

std::array<BlockDef, BLOCK_COUNT> Blocks::defs_{};

void Blocks::init() {
    auto& d = defs_;

    auto solid = [](const char* name, uint16_t top, uint16_t side, uint16_t bot,
                    float hardness) {
        BlockDef b;
        b.name = name; b.render = RenderKind::Solid; b.solid = true; b.opaque = true;
        b.hardness = hardness; b.texTop = top; b.texSide = side; b.texBottom = bot;
        return b;
    };

    d[BLOCK_AIR]      = BlockDef{}; // defaults: air

    d[BLOCK_STONE]    = solid("stone",  TILE_STONE, TILE_STONE, TILE_STONE, 1.5f);
    d[BLOCK_GRASS]    = solid("grass",  TILE_GRASS_TOP, TILE_GRASS_SIDE, TILE_DIRT, 0.6f);
    d[BLOCK_DIRT]     = solid("dirt",   TILE_DIRT, TILE_DIRT, TILE_DIRT, 0.5f);
    d[BLOCK_COBBLE]   = solid("cobblestone", TILE_COBBLE, TILE_COBBLE, TILE_COBBLE, 2.0f);
    d[BLOCK_PLANKS]   = solid("planks", TILE_PLANKS, TILE_PLANKS, TILE_PLANKS, 2.0f);
    d[BLOCK_SAND]     = solid("sand",   TILE_SAND, TILE_SAND, TILE_SAND, 0.5f);
    d[BLOCK_LOG]      = solid("log",    TILE_LOG_TOP, TILE_LOG_SIDE, TILE_LOG_TOP, 2.0f);
    d[BLOCK_BEDROCK]  = solid("bedrock", TILE_BEDROCK, TILE_BEDROCK, TILE_BEDROCK, -1.0f);
    d[BLOCK_GRAVEL]   = solid("gravel", TILE_GRAVEL, TILE_GRAVEL, TILE_GRAVEL, 0.6f);
    d[BLOCK_COAL_ORE] = solid("coal_ore", TILE_COAL_ORE, TILE_COAL_ORE, TILE_COAL_ORE, 3.0f);
    d[BLOCK_IRON_ORE] = solid("iron_ore", TILE_IRON_ORE, TILE_IRON_ORE, TILE_IRON_ORE, 3.0f);
    d[BLOCK_GOLD_ORE] = solid("gold_ore", TILE_GOLD_ORE, TILE_GOLD_ORE, TILE_GOLD_ORE, 3.0f);
    d[BLOCK_DIAMOND_ORE] = solid("diamond_ore", TILE_DIAMOND_ORE, TILE_DIAMOND_ORE, TILE_DIAMOND_ORE, 3.0f);
    d[BLOCK_SNOW]     = solid("snow",   TILE_SNOW, TILE_SNOW, TILE_DIRT, 0.5f);
    d[BLOCK_BRICK]    = solid("brick",  TILE_BRICK, TILE_BRICK, TILE_BRICK, 2.0f);
    d[BLOCK_PUMPKIN]  = solid("pumpkin", TILE_PUMPKIN_TOP, TILE_PUMPKIN_SIDE, TILE_PUMPKIN_TOP, 1.0f);
    d[BLOCK_CACTUS]   = solid("cactus", TILE_CACTUS_TOP, TILE_CACTUS_SIDE, TILE_CACTUS_TOP, 0.4f);

    // Glowstone: opaque solid that emits light.
    d[BLOCK_GLOWSTONE] = solid("glowstone", TILE_GLOWSTONE, TILE_GLOWSTONE, TILE_GLOWSTONE, 0.3f);
    d[BLOCK_GLOWSTONE].lightEmission = 15;

    // Leaves: full cube but see-through (cutout), does not fully block light.
    {
        BlockDef b;
        b.name = "leaves"; b.render = RenderKind::Transparent; b.solid = true;
        b.opaque = false; b.hardness = 0.2f;
        b.texTop = b.texSide = b.texBottom = TILE_LEAVES;
        d[BLOCK_LEAVES] = b;
    }
    // Glass.
    {
        BlockDef b;
        b.name = "glass"; b.render = RenderKind::Transparent; b.solid = true;
        b.opaque = false; b.hardness = 0.3f;
        b.texTop = b.texSide = b.texBottom = TILE_GLASS;
        d[BLOCK_GLASS] = b;
    }
    // Water: liquid, no collision, translucent.
    {
        BlockDef b;
        b.name = "water"; b.render = RenderKind::Liquid; b.solid = false;
        b.opaque = false; b.hardness = -1.0f;
        b.texTop = b.texSide = b.texBottom = TILE_WATER;
        d[BLOCK_WATER] = b;
    }
    // Cross-shaped plants.
    auto cross = [](const char* name, uint16_t tex, uint8_t light) {
        BlockDef b;
        b.name = name; b.render = RenderKind::Cross; b.solid = false;
        b.opaque = false; b.hardness = 0.0f; b.lightEmission = light;
        b.texTop = b.texSide = b.texBottom = tex;
        return b;
    };
    d[BLOCK_FLOWER]    = cross("flower", TILE_FLOWER, 0);
    d[BLOCK_TALLGRASS] = cross("tallgrass", TILE_TALLGRASS, 0);
    d[BLOCK_TORCH]     = cross("torch", TILE_TORCH, 14);
}

} // namespace mc
