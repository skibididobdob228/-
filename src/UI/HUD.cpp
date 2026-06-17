#include "UI/HUD.h"
#include "Player/Player.h"
#include "Render/TextureAtlas.h"
#include "Render/TileIds.h"
#include "World/Block.h"
#include <GL/glew.h>
#include <glm/gtc/matrix_transform.hpp>

namespace mc {

static const char* UI_VERT = R"(#version 330 core
layout(location=0) in vec2 aPos;
layout(location=1) in vec2 aUV;
layout(location=2) in vec4 aColor;
uniform mat4 uProj;
out vec2 vUV;
out vec4 vColor;
void main(){
    vUV = aUV;
    vColor = aColor;
    gl_Position = uProj * vec4(aPos, 0.0, 1.0);
}
)";

static const char* UI_FRAG = R"(#version 330 core
in vec2 vUV;
in vec4 vColor;
out vec4 FragColor;
uniform sampler2D uTex;
uniform int uTextured;
void main(){
    if(uTextured == 1){
        vec4 t = texture(uTex, vUV);
        if(t.a < 0.05) discard;
        FragColor = t * vColor;
    } else {
        FragColor = vColor;
    }
}
)";

HUD::HUD() {}
HUD::~HUD() {
    if (vao_) glDeleteVertexArrays(1, &vao_);
    if (vbo_) glDeleteBuffers(1, &vbo_);
}

bool HUD::init() {
    if (!shader_.compile(UI_VERT, UI_FRAG)) return false;
    glGenVertexArrays(1, &vao_);
    glGenBuffers(1, &vbo_);
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(UIVertex), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, sizeof(UIVertex), (void*)(2 * sizeof(float)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 4, GL_FLOAT, GL_FALSE, sizeof(UIVertex), (void*)(4 * sizeof(float)));
    glBindVertexArray(0);
    return true;
}

void HUD::quad(float x, float y, float w, float h, const glm::vec4& c) {
    UIVertex v[6] = {
        {x, y, -1, -1, c.r, c.g, c.b, c.a},
        {x + w, y, -1, -1, c.r, c.g, c.b, c.a},
        {x + w, y + h, -1, -1, c.r, c.g, c.b, c.a},
        {x, y, -1, -1, c.r, c.g, c.b, c.a},
        {x + w, y + h, -1, -1, c.r, c.g, c.b, c.a},
        {x, y + h, -1, -1, c.r, c.g, c.b, c.a},
    };
    for (auto& vert : v) verts_.push_back(vert);
}

void HUD::texQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1,
                  const glm::vec4& c) {
    UIVertex v[6] = {
        {x, y, u0, v0, c.r, c.g, c.b, c.a},
        {x + w, y, u1, v0, c.r, c.g, c.b, c.a},
        {x + w, y + h, u1, v1, c.r, c.g, c.b, c.a},
        {x, y, u0, v0, c.r, c.g, c.b, c.a},
        {x + w, y + h, u1, v1, c.r, c.g, c.b, c.a},
        {x, y + h, u0, v1, c.r, c.g, c.b, c.a},
    };
    for (auto& vert : v) verts_.push_back(vert);
}

void HUD::flush(int screenW, int screenH, bool textured) {
    if (verts_.empty()) return;
    glm::mat4 proj = glm::ortho(0.0f, (float)screenW, (float)screenH, 0.0f, -1.0f, 1.0f);
    shader_.use();
    shader_.set("uProj", proj);
    shader_.set("uTex", 0);
    shader_.set("uTextured", textured ? 1 : 0);
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, verts_.size() * sizeof(UIVertex), verts_.data(), GL_DYNAMIC_DRAW);
    glDrawArrays(GL_TRIANGLES, 0, (int)verts_.size());
    glBindVertexArray(0);
    verts_.clear();
}

void HUD::overlay(int screenW, int screenH, const glm::vec4& color) {
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    quad(0, 0, (float)screenW, (float)screenH, color);
    flush(screenW, screenH, false);
}

void HUD::render(int screenW, int screenH, const Player& player, const TextureAtlas& atlas) {
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float cx = screenW * 0.5f;
    const float cy = screenH * 0.5f;

    // --- Crosshair (white cross) ---
    glm::vec4 white(1, 1, 1, 0.85f);
    quad(cx - 10, cy - 1.5f, 20, 3, white);
    quad(cx - 1.5f, cy - 10, 3, 20, white);
    flush(screenW, screenH, false);

    // --- Hotbar background ---
    const float slot = 50.0f;
    const float pad = 4.0f;
    const float barW = Player::HOTBAR_SIZE * slot;
    const float barX = cx - barW * 0.5f;
    const float barY = screenH - slot - 12.0f;

    quad(barX - pad, barY - pad, barW + 2 * pad, slot + 2 * pad, glm::vec4(0, 0, 0, 0.45f));
    for (int i = 0; i < Player::HOTBAR_SIZE; ++i) {
        glm::vec4 c = (i == player.selectedSlot) ? glm::vec4(1, 1, 1, 0.25f)
                                                 : glm::vec4(1, 1, 1, 0.08f);
        quad(barX + i * slot + 2, barY + 2, slot - 4, slot - 4, c);
    }
    // Selected slot border.
    {
        float sx = barX + player.selectedSlot * slot;
        glm::vec4 b(1, 1, 1, 0.9f);
        quad(sx - 2, barY - 2, slot + 4, 3, b);
        quad(sx - 2, barY + slot - 1, slot + 4, 3, b);
        quad(sx - 2, barY - 2, 3, slot + 4, b);
        quad(sx + slot - 1, barY - 2, 3, slot + 4, b);
    }
    flush(screenW, screenH, false);

    // --- Hotbar item icons (textured from atlas) ---
    atlas.bind(0);
    const float inv = 1.0f / ATLAS_PX;
    const float tileSize = float(TILE_PX) * inv;
    for (int i = 0; i < Player::HOTBAR_SIZE; ++i) {
        BlockId id = player.hotbar[i];
        if (id == BLOCK_AIR) continue;
        uint16_t tile = Blocks::get(id).texSide;
        int col = tile % ATLAS_TILES, row = tile / ATLAS_TILES;
        float u0 = col * tileSize, v0 = row * tileSize;
        float iconPad = 8.0f;
        texQuad(barX + i * slot + iconPad, barY + iconPad,
                slot - 2 * iconPad, slot - 2 * iconPad,
                u0, v0, u0 + tileSize, v0 + tileSize, glm::vec4(1));
    }
    flush(screenW, screenH, true);

    // --- Health & hunger (only meaningful in Survival, shown always) ---
    if (player.mode == GameMode::Survival) {
        const float iconSize = 18.0f;
        float hx = barX;
        float hy = barY - 26.0f;
        int fullHearts = (int)(player.health + 0.5f);
        for (int i = 0; i < 10; ++i) {
            // background slot
            quad(hx + i * (iconSize + 1), hy, iconSize, iconSize, glm::vec4(0.15f, 0.15f, 0.15f, 0.6f));
            float hp = player.health - i * 2.0f;
            if (hp > 0) {
                float fill = hp >= 2.0f ? 1.0f : 0.5f;
                quad(hx + i * (iconSize + 1), hy, iconSize * fill, iconSize,
                     glm::vec4(0.85f, 0.1f, 0.15f, 1.0f));
            }
        }
        // Hunger on the right.
        float gx = barX + barW - 10 * (iconSize + 1);
        float gy = hy - 22.0f;
        for (int i = 0; i < 10; ++i) {
            quad(gx + i * (iconSize + 1), gy, iconSize, iconSize, glm::vec4(0.15f, 0.15f, 0.15f, 0.6f));
            float hg = player.hunger - i * 2.0f;
            if (hg > 0) {
                float fill = hg >= 2.0f ? 1.0f : 0.5f;
                quad(gx + i * (iconSize + 1), gy, iconSize * fill, iconSize,
                     glm::vec4(0.6f, 0.4f, 0.15f, 1.0f));
            }
        }
        flush(screenW, screenH, false);
    }

    glEnable(GL_DEPTH_TEST);
}

} // namespace mc
