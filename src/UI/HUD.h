#pragma once
#include "Render/Shader.h"
#include <vector>
#include <glm/glm.hpp>

namespace mc {

class Player;
class TextureAtlas;

// Immediate-mode 2D overlay: crosshair, hotbar, item icons, health & hunger.
// Builds a vertex batch each frame and draws it with one ortho shader.
class HUD {
public:
    HUD();
    ~HUD();

    bool init();
    void render(int screenW, int screenH, const Player& player, const TextureAtlas& atlas);

    // Full-screen tint (death / pause overlays).
    void overlay(int screenW, int screenH, const glm::vec4& color);

private:
    struct UIVertex { float x, y, u, v, r, g, b, a; };

    void quad(float x, float y, float w, float h, const glm::vec4& col);
    void texQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1,
                 const glm::vec4& col);
    void flush(int screenW, int screenH, bool textured);

    Shader shader_;
    unsigned vao_ = 0, vbo_ = 0;
    std::vector<UIVertex> verts_;
};

} // namespace mc
