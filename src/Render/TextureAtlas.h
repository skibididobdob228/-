#pragma once
#include <cstdint>
#include <vector>

namespace mc {

// Builds a 256x256 RGBA texture atlas procedurally (no external image files
// required) and uploads it to the GPU with mipmaps. Each 16x16 tile maps to a
// Tile enum index.
class TextureAtlas {
public:
    TextureAtlas();
    ~TextureAtlas();

    void bind(int unit = 0) const;
    unsigned id() const { return texture_; }

    // CPU-side RGBA buffer, kept so the HUD can sample tiles for item icons.
    const std::vector<uint8_t>& pixels() const { return pixels_; }

private:
    void generate();
    unsigned texture_ = 0;
    std::vector<uint8_t> pixels_; // ATLAS_PX*ATLAS_PX*4
};

} // namespace mc
