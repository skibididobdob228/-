#include "Render/TextureAtlas.h"
#include "Render/TileIds.h"
#include <GL/glew.h>
#include <cmath>
#include <cstdint>

namespace mc {

namespace {
// Deterministic hash -> [0,1). Used to add per-pixel grain to tiles.
inline float h01(int x, int y, int salt) {
    uint32_t n = uint32_t(x) * 374761393u + uint32_t(y) * 668265263u + uint32_t(salt) * 2147483647u;
    n = (n ^ (n >> 13)) * 1274126177u;
    n ^= n >> 16;
    return (n & 0xFFFFFF) / float(0x1000000);
}

struct RGBA { uint8_t r, g, b, a; };

inline RGBA mix(RGBA c, float k) { // multiply rgb by k (shade)
    auto cl = [](float v) { return (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v); };
    return { cl(c.r * k), cl(c.g * k), cl(c.b * k), c.a };
}
} // namespace

TextureAtlas::TextureAtlas() {
    pixels_.assign(ATLAS_PX * ATLAS_PX * 4, 0);
    generate();

    glGenTextures(1, &texture_);
    glBindTexture(GL_TEXTURE_2D, texture_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS_PX, ATLAS_PX, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, pixels_.data());
    glGenerateMipmap(GL_TEXTURE_2D);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
}

TextureAtlas::~TextureAtlas() {
    if (texture_) glDeleteTextures(1, &texture_);
}

void TextureAtlas::bind(int unit) const {
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(GL_TEXTURE_2D, texture_);
}

void TextureAtlas::generate() {
    auto put = [&](int tile, int px, int py, RGBA c) {
        int col = tile % ATLAS_TILES;
        int row = tile / ATLAS_TILES;
        int X = col * TILE_PX + px;
        int Y = row * TILE_PX + py;
        size_t i = (size_t(Y) * ATLAS_PX + X) * 4;
        pixels_[i + 0] = c.r;
        pixels_[i + 1] = c.g;
        pixels_[i + 2] = c.b;
        pixels_[i + 3] = c.a;
    };

    // Fill a tile with a base colour plus per-pixel grain.
    auto fillNoise = [&](int tile, RGBA base, float grain, int salt) {
        for (int y = 0; y < TILE_PX; ++y)
            for (int x = 0; x < TILE_PX; ++x) {
                float n = (h01(x, y, salt) - 0.5f) * 2.0f * grain;
                put(tile, x, y, mix(base, 1.0f + n));
            }
    };

    const RGBA TRANS{0, 0, 0, 0};

    // --- Stone & rocks ---
    fillNoise(TILE_STONE, {128, 128, 132, 255}, 0.10f, 1);
    fillNoise(TILE_COBBLE, {120, 120, 124, 255}, 0.22f, 2);
    fillNoise(TILE_BEDROCK, {70, 70, 74, 255}, 0.30f, 3);
    fillNoise(TILE_GRAVEL, {130, 122, 118, 255}, 0.28f, 4);

    // --- Dirt / grass ---
    fillNoise(TILE_DIRT, {134, 96, 67, 255}, 0.12f, 5);
    fillNoise(TILE_GRASS_TOP, {86, 145, 62, 255}, 0.14f, 6);
    // Grass side: dirt with a green fringe on the top rows.
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 7) - 0.5f) * 0.2f;
            RGBA c = (y < 4) ? mix(RGBA{86, 145, 62, 255}, 1 + n)
                             : mix(RGBA{134, 96, 67, 255}, 1 + n);
            if (y == 4 && (x % 2) == 0) c = mix(RGBA{86, 145, 62, 255}, 1.0f);
            put(TILE_GRASS_SIDE, x, y, c);
        }

    fillNoise(TILE_SAND, {219, 207, 153, 255}, 0.08f, 8);
    fillNoise(TILE_SNOW, {236, 240, 245, 255}, 0.05f, 9);

    // --- Wood ---
    // Planks: horizontal plank lines.
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 10) - 0.5f) * 0.12f;
            RGBA c = mix(RGBA{160, 124, 76, 255}, 1 + n);
            if (y % 4 == 0) c = mix(c, 0.7f); // plank seam
            put(TILE_PLANKS, x, y, c);
        }
    // Log side: vertical bark.
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 11) - 0.5f) * 0.18f;
            RGBA c = mix(RGBA{104, 78, 47, 255}, 1 + n);
            if (x % 5 == 0) c = mix(c, 0.75f);
            put(TILE_LOG_SIDE, x, y, c);
        }
    // Log top: concentric rings.
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float dx = x - 7.5f, dy = y - 7.5f;
            float r = std::sqrt(dx * dx + dy * dy);
            float ring = 0.5f + 0.5f * std::sin(r * 2.0f);
            RGBA c = mix(RGBA{150, 118, 73, 255}, 0.8f + ring * 0.3f);
            put(TILE_LOG_TOP, x, y, c);
        }

    // --- Leaves (cutout: some transparent holes) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float hole = h01(x, y, 12);
            if (hole < 0.12f) { put(TILE_LEAVES, x, y, TRANS); continue; }
            float n = (h01(x, y, 13) - 0.5f) * 0.3f;
            put(TILE_LEAVES, x, y, mix(RGBA{54, 110, 40, 255}, 1 + n));
        }

    // --- Water (translucent blue) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 14) - 0.5f) * 0.15f;
            RGBA c = mix(RGBA{54, 102, 198, 255}, 1 + n);
            c.a = 170;
            put(TILE_WATER, x, y, c);
        }

    // --- Glass (mostly transparent with a frame) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            bool border = (x == 0 || y == 0 || x == 15 || y == 15);
            RGBA c = border ? RGBA{200, 220, 230, 230} : RGBA{210, 230, 240, 40};
            put(TILE_GLASS, x, y, c);
        }

    // --- Ores: stone base with coloured speckles ---
    auto ore = [&](int tile, RGBA gem, int salt) {
        for (int y = 0; y < TILE_PX; ++y)
            for (int x = 0; x < TILE_PX; ++x) {
                float n = (h01(x, y, 1) - 0.5f) * 0.10f;
                RGBA c = mix(RGBA{128, 128, 132, 255}, 1 + n);
                if (h01(x, y, salt) > 0.80f) c = gem;
                put(tile, x, y, c);
            }
    };
    ore(TILE_COAL_ORE, {40, 40, 40, 255}, 20);
    ore(TILE_IRON_ORE, {196, 160, 120, 255}, 21);
    ore(TILE_GOLD_ORE, {240, 210, 90, 255}, 22);
    ore(TILE_DIAMOND_ORE, {110, 220, 220, 255}, 23);

    // --- Brick ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            int row = y / 4;
            int offset = (row % 2) * 4;
            bool mortar = (y % 4 == 0) || ((x + offset) % 8 == 0);
            RGBA c = mortar ? RGBA{200, 200, 195, 255} : RGBA{150, 60, 50, 255};
            put(TILE_BRICK, x, y, c);
        }

    // --- Glowstone (bright speckled yellow) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float g = h01(x, y, 30);
            RGBA c = g > 0.5f ? RGBA{255, 230, 140, 255} : RGBA{200, 150, 60, 255};
            put(TILE_GLOWSTONE, x, y, c);
        }

    // --- Pumpkin ---
    fillNoise(TILE_PUMPKIN_TOP, {214, 140, 40, 255}, 0.1f, 31);
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 32) - 0.5f) * 0.1f;
            RGBA c = mix(RGBA{220, 130, 35, 255}, 1 + n);
            if (x % 4 == 0) c = mix(c, 0.8f); // ribs
            put(TILE_PUMPKIN_SIDE, x, y, c);
        }

    // --- Cactus ---
    fillNoise(TILE_CACTUS_TOP, {90, 150, 70, 255}, 0.1f, 33);
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) {
            float n = (h01(x, y, 34) - 0.5f) * 0.12f;
            RGBA c = mix(RGBA{74, 130, 58, 255}, 1 + n);
            if (x == 1 || x == 14) c = mix(c, 0.7f);
            put(TILE_CACTUS_SIDE, x, y, c);
        }

    // --- Flower (cross plant on transparent bg) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) put(TILE_FLOWER, x, y, TRANS);
    // stem
    for (int y = 7; y < 16; ++y) put(TILE_FLOWER, 7, y, {60, 130, 50, 255});
    for (int y = 7; y < 16; ++y) put(TILE_FLOWER, 8, y, {60, 130, 50, 255});
    // petals
    for (int y = 3; y < 8; ++y)
        for (int x = 5; x < 11; ++x) {
            float d = std::abs(x - 7.5f) + std::abs(y - 5.5f);
            if (d < 4.0f) put(TILE_FLOWER, x, y, {220, 70, 90, 255});
        }
    put(TILE_FLOWER, 7, 5, {250, 220, 80, 255});
    put(TILE_FLOWER, 8, 5, {250, 220, 80, 255});

    // --- Tall grass (cross plant) ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) put(TILE_TALLGRASS, x, y, TRANS);
    for (int x = 2; x < 14; ++x) {
        int top = 4 + (int)(h01(x, 0, 40) * 4);
        for (int y = top; y < 16; ++y) {
            float n = (h01(x, y, 41) - 0.5f) * 0.25f;
            put(TILE_TALLGRASS, x, y, mix(RGBA{80, 150, 55, 255}, 1 + n));
        }
    }

    // --- Torch ---
    for (int y = 0; y < TILE_PX; ++y)
        for (int x = 0; x < TILE_PX; ++x) put(TILE_TORCH, x, y, TRANS);
    for (int y = 6; y < 16; ++y) { put(TILE_TORCH, 7, y, {120, 80, 40, 255}); put(TILE_TORCH, 8, y, {120, 80, 40, 255}); }
    for (int y = 3; y < 7; ++y)  { put(TILE_TORCH, 7, y, {255, 210, 90, 255}); put(TILE_TORCH, 8, y, {255, 230, 120, 255}); }
}

} // namespace mc
