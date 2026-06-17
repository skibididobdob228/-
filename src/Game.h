#pragma once
#include "Core/Window.h"
#include "Render/Camera.h"
#include "Render/Shader.h"
#include "Render/TextureAtlas.h"
#include "World/World.h"
#include "Player/Player.h"
#include "Player/Input.h"
#include "UI/HUD.h"
#include <memory>
#include <glm/glm.hpp>

namespace mc {

// Top-level application: owns subsystems, runs the fixed-timestep loop,
// translates input into player/world actions, and draws everything.
class Game {
public:
    Game(int width, int height);
    ~Game();

    void run();

private:
    void setupCallbacks();
    void gatherInput(InputState& in, float dt);
    void fixedUpdate(float dt);
    void render(float alpha);
    void handleInteraction(float dt);
    void renderSelection(const glm::mat4& vp);
    void renderSky();
    void dayNight(glm::vec3& sky, glm::vec3& sunDir, float& dayLight) const;
    void updateTitle(float fps);

    Window window_;
    Camera camera_;
    std::unique_ptr<TextureAtlas> atlas_;
    Shader chunkShader_;
    Shader lineShader_;
    std::unique_ptr<World> world_;
    Player player_;
    HUD hud_;

    int renderDistance_ = 8;
    double worldTime_ = 120.0;   // seconds into the day cycle
    double dayLength_ = 600.0;

    bool paused_ = false;
    bool debug_ = false;
    bool thirdPerson_ = false;
    RaycastHit lastHit_;
    bool firstMouse_ = true;
    double lastX_ = 0, lastY_ = 0;

    // Interaction state.
    bool lmbDown_ = false, rmbDown_ = false;
    bool lmbPrev_ = false, rmbPrev_ = false;
    bool jumpPrev_ = false;
    glm::ivec3 breakingPos_{0};
    float breakProgress_ = 0.0f;

    unsigned selVao_ = 0, selVbo_ = 0;
    std::string savePath_ = "world.sav";
    double saveTimer_ = 0.0;
};

} // namespace mc
