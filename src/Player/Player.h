#pragma once
#include "Player/Input.h"
#include "World/Block.h"
#include <glm/glm.hpp>

namespace mc {

class World;
class Camera;

enum class GameMode { Survival, Creative, Spectator };

class Player {
public:
    glm::vec3 position{0, 80, 0};  // feet centre
    glm::vec3 velocity{0};
    bool onGround = false;
    bool flying = false;
    bool inWater = false;

    GameMode mode = GameMode::Creative;

    // Stats.
    float health = 20.0f;
    float maxHealth = 20.0f;
    float hunger = 20.0f;
    bool dead = false;

    float mouseSensitivity = 0.12f;

    // Hotbar: block ids selectable for placement.
    static constexpr int HOTBAR_SIZE = 9;
    BlockId hotbar[HOTBAR_SIZE];
    int selectedSlot = 0;

    Player();

    void update(World& world, Camera& camera, const InputState& in, float dt);
    void respawn(World& world);

    BlockId selectedBlock() const { return hotbar[selectedSlot]; }
    void scrollHotbar(int dir);

    void toggleMode();

    // Bounding box half extents.
    glm::vec3 halfExtents() const { return glm::vec3(0.3f, 0.9f, 0.3f); }
    float eyeHeight() const { return 1.62f; }

private:
    float fallStartY_ = 0.0f;
    bool wasOnGround_ = true;
    double lastJumpTime_ = -10.0;
    float regenTimer_ = 0.0f;

    void applyMovement(World& world, const Camera& camera, const InputState& in, float dt);
    bool collides(World& world, const glm::vec3& pos) const;
    void resolveCollisions(World& world, glm::vec3& pos, glm::vec3& vel, float dt);
    void updateStats(World& world, float dt);
};

} // namespace mc
