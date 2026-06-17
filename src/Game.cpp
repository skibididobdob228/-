#include "Game.h"
#include "World/ChunkMesher.h"
#include "Core/Logger.h"
#include "Core/Screenshot.h"
#include <GL/glew.h>
#include <GLFW/glfw3.h>
#include <glm/gtc/matrix_transform.hpp>
#include <thread>
#include <chrono>
#include <cmath>
#include <cstdio>

namespace mc {

Game::Game(int width, int height)
    : window_(width, height, "MiniCraft") {
    Blocks::init();

    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    atlas_ = std::make_unique<TextureAtlas>();

    if (!chunkShader_.loadFromFiles("shaders/chunk.vert", "shaders/chunk.frag"))
        LOG_ERROR("Failed to load chunk shader");
    if (!lineShader_.loadFromFiles("shaders/line.vert", "shaders/line.frag"))
        LOG_ERROR("Failed to load line shader");
    if (!hud_.init())
        LOG_ERROR("Failed to init HUD");

    uint32_t seed = 1337;
    world_ = std::make_unique<World>(seed);
    if (world_->load(savePath_)) {
        LOG_INFO("Resumed saved world");
    }

    // Selection wireframe box (unit cube edges).
    static const float E[] = {
        0,0,0, 1,0,0,  1,0,0, 1,1,0,  1,1,0, 0,1,0,  0,1,0, 0,0,0,
        0,0,1, 1,0,1,  1,0,1, 1,1,1,  1,1,1, 0,1,1,  0,1,1, 0,0,1,
        0,0,0, 0,0,1,  1,0,0, 1,0,1,  1,1,0, 1,1,1,  0,1,0, 0,1,1,
    };
    glGenVertexArrays(1, &selVao_);
    glGenBuffers(1, &selVbo_);
    glBindVertexArray(selVao_);
    glBindBuffer(GL_ARRAY_BUFFER, selVbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(E), E, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void*)0);
    glBindVertexArray(0);

    setupCallbacks();

    // Spawn on the surface and warm up nearby chunks before dropping the player.
    int surf = world_->findSpawnHeight(0, 0);
    player_.position = glm::vec3(0.5f, (float)surf + 1.0f, 0.5f);
    camera_.position = player_.position + glm::vec3(0, player_.eyeHeight(), 0);
    camera_.aspect = window_.aspect();
    for (int i = 0; i < 400; ++i) {
        world_->update(camera_.position, renderDistance_);
        if (world_->getBlock(0, surf - 3, 0) != BLOCK_AIR) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    LOG_INFO("Spawned at y=%d", surf);
}

Game::~Game() {
    world_->save(savePath_);
    if (selVao_) glDeleteVertexArrays(1, &selVao_);
    if (selVbo_) glDeleteBuffers(1, &selVbo_);
}

void Game::setupCallbacks() {
    window_.onResize = [this](int w, int h) {
        camera_.aspect = h > 0 ? float(w) / float(h) : 1.0f;
    };
    window_.onScroll = [this](double, double yo) {
        if (!paused_) player_.scrollHotbar((int)yo);
    };
    window_.onKey = [this](int key, int action, int) {
        if (action != GLFW_PRESS) return;
        switch (key) {
            case GLFW_KEY_ESCAPE:
                paused_ = !paused_;
                window_.setCursorCaptured(!paused_);
                firstMouse_ = true;
                break;
            case GLFW_KEY_F3: debug_ = !debug_; break;
            case GLFW_KEY_F11: window_.toggleFullscreen(); break;
            case GLFW_KEY_F5: thirdPerson_ = !thirdPerson_; break;
            case GLFW_KEY_G: player_.toggleMode(); break;
            case GLFW_KEY_R: if (player_.dead) player_.respawn(*world_); break;
            case GLFW_KEY_F2: {
                char name[64];
                std::snprintf(name, sizeof(name), "screenshot_%d.png", (int)glfwGetTime());
                if (captureScreenshot(name, window_.width(), window_.height()))
                    LOG_INFO("Saved %s", name);
                break;
            }
            default: break;
        }
        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9)
            player_.selectedSlot = key - GLFW_KEY_1;
    };
}

void Game::gatherInput(InputState& in, float) {
    GLFWwindow* w = window_.handle();
    in.forward = glfwGetKey(w, GLFW_KEY_W) == GLFW_PRESS;
    in.back    = glfwGetKey(w, GLFW_KEY_S) == GLFW_PRESS;
    in.left    = glfwGetKey(w, GLFW_KEY_A) == GLFW_PRESS;
    in.right   = glfwGetKey(w, GLFW_KEY_D) == GLFW_PRESS;
    in.jump    = glfwGetKey(w, GLFW_KEY_SPACE) == GLFW_PRESS;
    in.sneak   = glfwGetKey(w, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
    in.sprint  = glfwGetKey(w, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
    in.jumpPressed = in.jump && !jumpPrev_;
    jumpPrev_ = in.jump;
}

void Game::handleInteraction(float dt) {
    if (paused_ || player_.dead || player_.mode == GameMode::Spectator) {
        breakProgress_ = 0;
        lmbPrev_ = rmbPrev_ = false;
        return;
    }
    GLFWwindow* w = window_.handle();
    bool lmb = glfwGetMouseButton(w, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
    bool rmb = glfwGetMouseButton(w, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

    lastHit_ = world_->raycast(camera_.position, camera_.front(), 6.0f);

    // --- Breaking ---
    if (lmb && lastHit_.hit) {
        BlockId target = world_->getBlock(lastHit_.block.x, lastHit_.block.y, lastHit_.block.z);
        float hardness = Blocks::get(target).hardness;
        if (hardness < 0) {
            // unbreakable
        } else if (player_.mode == GameMode::Creative) {
            if (!lmbPrev_)
                world_->setBlock(lastHit_.block.x, lastHit_.block.y, lastHit_.block.z, BLOCK_AIR);
        } else {
            if (breakingPos_ != lastHit_.block) { breakingPos_ = lastHit_.block; breakProgress_ = 0; }
            breakProgress_ += dt / std::max(hardness, 0.05f);
            if (breakProgress_ >= 1.0f) {
                world_->setBlock(lastHit_.block.x, lastHit_.block.y, lastHit_.block.z, BLOCK_AIR);
                breakProgress_ = 0;
            }
        }
    } else {
        breakProgress_ = 0;
    }

    // --- Placing (edge-triggered) ---
    if (rmb && !rmbPrev_ && lastHit_.hit) {
        glm::ivec3 p = lastHit_.previous;
        BlockId id = player_.selectedBlock();
        if (id != BLOCK_AIR && world_->getBlock(p.x, p.y, p.z) == BLOCK_AIR) {
            // Don't place inside the player's body.
            glm::vec3 h = player_.halfExtents();
            glm::vec3 mn = player_.position - glm::vec3(h.x, 0, h.z);
            glm::vec3 mx = player_.position + glm::vec3(h.x, h.y * 2, h.z);
            bool overlap = !(p.x + 1 <= mn.x || p.x >= mx.x ||
                             p.y + 1 <= mn.y || p.y >= mx.y ||
                             p.z + 1 <= mn.z || p.z >= mx.z);
            if (!overlap || !Blocks::isSolid(id))
                world_->setBlock(p.x, p.y, p.z, id);
        }
    }

    lmbPrev_ = lmb;
    rmbPrev_ = rmb;
}

void Game::fixedUpdate(float dt) {
    if (paused_) return;
    InputState in;
    gatherInput(in, dt);
    in.mouseDX = in.mouseDY = 0; // mouse-look applied once per frame elsewhere
    player_.update(*world_, camera_, in, dt);
}

void Game::dayNight(glm::vec3& sky, glm::vec3& sunDir, float& dayLight) const {
    float t = (float)(std::fmod(worldTime_, dayLength_) / dayLength_);
    float angle = t * 6.2831853f;
    float sunHeight = std::sin(angle);
    sunDir = glm::normalize(glm::vec3(std::cos(angle), sunHeight, 0.35f));
    dayLight = glm::clamp((sunHeight + 0.15f) / 0.4f, 0.0f, 1.0f);
    glm::vec3 daySky(0.47f, 0.66f, 0.98f);
    glm::vec3 nightSky(0.02f, 0.03f, 0.07f);
    sky = glm::mix(nightSky, daySky, dayLight);
    // Sunset/sunrise warmth near the horizon.
    float horizon = glm::clamp(1.0f - std::abs(sunHeight) * 3.0f, 0.0f, 1.0f) * dayLight;
    sky = glm::mix(sky, glm::vec3(0.95f, 0.55f, 0.30f), horizon * 0.5f);
}

void Game::renderSelection(const glm::mat4& vp) {
    if (!lastHit_.hit) return;
    glm::vec3 p = glm::vec3(lastHit_.block);
    glm::mat4 model = glm::translate(glm::mat4(1.0f), p - glm::vec3(0.002f));
    model = glm::scale(model, glm::vec3(1.004f));
    lineShader_.use();
    lineShader_.set("uMVP", vp * model);
    float prog = breakProgress_;
    lineShader_.set("uColor", glm::vec4(0.0f, 0.0f, 0.0f, 0.55f + prog * 0.4f));
    glLineWidth(2.0f);
    glBindVertexArray(selVao_);
    glDrawArrays(GL_LINES, 0, 24);
    glBindVertexArray(0);
}

void Game::render(float) {
    glm::vec3 sky, sunDir;
    float dayLight;
    dayNight(sky, sunDir, dayLight);

    glClearColor(sky.r, sky.g, sky.b, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Third-person: pull the camera back along the view ray.
    glm::vec3 eye = player_.position + glm::vec3(0, player_.eyeHeight(), 0);
    camera_.position = eye;
    if (thirdPerson_) {
        RaycastHit back = world_->raycast(eye, -camera_.front(), 4.0f);
        float dist = back.hit ? glm::distance(eye, glm::vec3(back.block)) - 0.3f : 4.0f;
        camera_.position = eye - camera_.front() * glm::clamp(dist, 0.5f, 4.0f);
    }

    camera_.aspect = window_.aspect();
    camera_.farPlane = (renderDistance_ + 2) * 16.0f;
    glm::mat4 view = camera_.view();
    glm::mat4 proj = camera_.projection();
    glm::mat4 vp = proj * view;

    float fogStart = (renderDistance_ - 2) * 16.0f;
    float fogEnd = (renderDistance_ + 0.5f) * 16.0f;

    chunkShader_.use();
    chunkShader_.set("uView", view);
    chunkShader_.set("uProj", proj);
    chunkShader_.set("uAtlas", 0);
    chunkShader_.set("uSunDir", sunDir);
    chunkShader_.set("uDayLight", dayLight);
    chunkShader_.set("uFogColor", sky);
    chunkShader_.set("uFogStart", fogStart);
    chunkShader_.set("uFogEnd", fogEnd);
    chunkShader_.set("uCameraPos", camera_.position);
    atlas_->bind(0);

    // Opaque pass (cutout for plants handled by alpha discard).
    chunkShader_.set("uAlphaCutout", 1);
    glDisable(GL_BLEND);
    world_->renderOpaque(chunkShader_, camera_);

    // Selection outline before transparent water for clarity.
    renderSelection(vp);

    // Transparent pass: water/glass/leaves/plants, blended & sorted.
    chunkShader_.use();
    chunkShader_.set("uAlphaCutout", 0);
    glEnable(GL_BLEND);
    glDepthMask(GL_FALSE);
    world_->renderTransparent(chunkShader_, camera_);
    glDepthMask(GL_TRUE);

    // HUD.
    hud_.render(window_.width(), window_.height(), player_, *atlas_);

    if (player_.dead)
        hud_.overlay(window_.width(), window_.height(), glm::vec4(0.5f, 0.0f, 0.0f, 0.45f));
    else if (paused_)
        hud_.overlay(window_.width(), window_.height(), glm::vec4(0.0f, 0.0f, 0.0f, 0.45f));
}

void Game::updateTitle(float fps) {
    const char* modeName = player_.mode == GameMode::Survival ? "Survival"
                         : player_.mode == GameMode::Creative ? "Creative" : "Spectator";
    char buf[256];
    if (player_.dead) {
        std::snprintf(buf, sizeof(buf), "MiniCraft -- YOU DIED -- press R to respawn");
    } else if (debug_) {
        std::snprintf(buf, sizeof(buf),
            "MiniCraft | FPS %.0f | XYZ %.1f %.1f %.1f | %s%s | chunks %zu drawn %d",
            fps, player_.position.x, player_.position.y, player_.position.z,
            modeName, player_.flying ? " (fly)" : "",
            world_->loadedChunks(), world_->lastDrawn());
    } else {
        std::snprintf(buf, sizeof(buf), "MiniCraft | %s%s | FPS %.0f%s",
            modeName, player_.flying ? " (fly)" : "", fps,
            paused_ ? " | PAUSED" : "");
    }
    glfwSetWindowTitle(window_.handle(), buf);
}

void Game::run() {
    const double dt = 1.0 / 60.0;
    double prev = glfwGetTime();
    double acc = 0.0;
    double fpsTimer = 0.0;
    int frames = 0;
    float fps = 0.0f;

    while (!window_.shouldClose()) {
        double now = glfwGetTime();
        double frame = now - prev;
        prev = now;
        if (frame > 0.25) frame = 0.25;
        acc += frame;

        window_.pollEvents();

        // Mouse look once per frame.
        double mx, my;
        glfwGetCursorPos(window_.handle(), &mx, &my);
        if (firstMouse_) { lastX_ = mx; lastY_ = my; firstMouse_ = false; }
        double dx = mx - lastX_, dy = my - lastY_;
        lastX_ = mx; lastY_ = my;
        if (!paused_ && !player_.dead)
            camera_.addMouseDelta((float)dx, (float)dy, player_.mouseSensitivity);

        // Fixed-step physics.
        int steps = 0;
        while (acc >= dt && steps < 5) { fixedUpdate((float)dt); acc -= dt; ++steps; }
        if (acc > dt) acc = 0; // avoid spiral of death

        if (!paused_) {
            worldTime_ += frame;
            handleInteraction((float)frame);
            world_->update(camera_.position, renderDistance_);
        }

        render((float)(acc / dt));

        // Headless verification: auto-capture after warm-up, then exit.
        if (const char* shot = std::getenv("MINICRAFT_SCREENSHOT")) {
            static int shotFrame = 0;
            if (++shotFrame == 90) {
                captureScreenshot(shot, window_.width(), window_.height());
                LOG_INFO("Wrote screenshot %s", shot);
                window_.setShouldClose(true);
            }
        }

        window_.swapBuffers();

        // Periodic autosave.
        saveTimer_ += frame;
        if (saveTimer_ > 30.0) { world_->save(savePath_); saveTimer_ = 0.0; }

        // FPS counter -> window title.
        frames++; fpsTimer += frame;
        if (fpsTimer >= 0.5) { fps = frames / (float)fpsTimer; frames = 0; fpsTimer = 0; updateTitle(fps); }
    }
}

} // namespace mc
