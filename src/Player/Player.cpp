#include "Player/Player.h"
#include "World/World.h"
#include "Render/Camera.h"
#include <GLFW/glfw3.h>
#include <glm/gtc/matrix_transform.hpp>
#include <algorithm>
#include <cmath>

namespace mc {

namespace {
constexpr float GRAVITY = 28.0f;
constexpr float JUMP_VELOCITY = 8.6f;
constexpr float WALK_SPEED = 4.6f;
constexpr float SPRINT_SPEED = 6.2f;
constexpr float FLY_SPEED = 12.0f;
constexpr float WATER_DRAG = 0.6f;
}

Player::Player() {
    BlockId defaults[HOTBAR_SIZE] = {
        BLOCK_GRASS, BLOCK_DIRT, BLOCK_STONE, BLOCK_COBBLE, BLOCK_PLANKS,
        BLOCK_LOG, BLOCK_GLASS, BLOCK_GLOWSTONE, BLOCK_BRICK
    };
    for (int i = 0; i < HOTBAR_SIZE; ++i) hotbar[i] = defaults[i];
}

void Player::scrollHotbar(int dir) {
    selectedSlot = (selectedSlot - dir) % HOTBAR_SIZE;
    if (selectedSlot < 0) selectedSlot += HOTBAR_SIZE;
}

void Player::toggleMode() {
    mode = (mode == GameMode::Survival) ? GameMode::Creative
         : (mode == GameMode::Creative) ? GameMode::Spectator
                                        : GameMode::Survival;
    if (mode == GameMode::Spectator) flying = true;
    if (mode == GameMode::Survival) flying = false;
}

bool Player::collides(World& world, const glm::vec3& pos) const {
    if (mode == GameMode::Spectator) return false;
    glm::vec3 h = halfExtents();
    glm::vec3 mn = pos - glm::vec3(h.x, 0.0f, h.z);
    glm::vec3 mx = pos + glm::vec3(h.x, h.y * 2.0f, h.z);
    int x0 = (int)std::floor(mn.x), x1 = (int)std::floor(mx.x - 1e-4f);
    int y0 = (int)std::floor(mn.y), y1 = (int)std::floor(mx.y - 1e-4f);
    int z0 = (int)std::floor(mn.z), z1 = (int)std::floor(mx.z - 1e-4f);
    for (int y = y0; y <= y1; ++y)
        for (int z = z0; z <= z1; ++z)
            for (int x = x0; x <= x1; ++x) {
                BlockId b = world.getBlock(x, y, z);
                if (Blocks::isSolid(b)) return true;
            }
    return false;
}

void Player::resolveCollisions(World& world, glm::vec3& pos, glm::vec3& vel, float dt) {
    // Move axis by axis so we can slide along walls.
    glm::vec3 delta = vel * dt;

    pos.x += delta.x;
    if (collides(world, pos)) { pos.x -= delta.x; vel.x = 0; }

    pos.z += delta.z;
    if (collides(world, pos)) { pos.z -= delta.z; vel.z = 0; }

    pos.y += delta.y;
    if (collides(world, pos)) {
        pos.y -= delta.y;
        if (vel.y < 0) onGround = true;
        vel.y = 0;
    } else {
        onGround = false;
    }
}

void Player::applyMovement(World& world, const Camera& camera, const InputState& in, float dt) {
    // Determine if standing in water.
    glm::vec3 eye = position + glm::vec3(0, eyeHeight(), 0);
    inWater = world.getBlock((int)std::floor(position.x),
                             (int)std::floor(position.y + 0.5f),
                             (int)std::floor(position.z)) == BLOCK_WATER;

    glm::vec3 fwd = camera.forwardXZ();
    glm::vec3 rightDir = glm::normalize(glm::cross(fwd, glm::vec3(0, 1, 0)));
    glm::vec3 wish(0);
    if (in.forward) wish += fwd;
    if (in.back)    wish -= fwd;
    if (in.right)   wish += rightDir;
    if (in.left)    wish -= rightDir;
    if (glm::length(wish) > 0.0001f) wish = glm::normalize(wish);

    float speed = in.sprint ? SPRINT_SPEED : WALK_SPEED;

    if (flying) {
        speed = FLY_SPEED * (in.sprint ? 1.8f : 1.0f);
        velocity.x = wish.x * speed;
        velocity.z = wish.z * speed;
        velocity.y = 0;
        if (in.jump)  velocity.y += speed;
        if (in.sneak) velocity.y -= speed;
        resolveCollisions(world, position, velocity, dt);
        return;
    }

    // Horizontal acceleration toward wish velocity.
    glm::vec3 targetH = wish * speed;
    float accel = onGround ? 18.0f : 6.0f;
    velocity.x += (targetH.x - velocity.x) * std::min(1.0f, accel * dt);
    velocity.z += (targetH.z - velocity.z) * std::min(1.0f, accel * dt);

    // Gravity / water.
    if (inWater) {
        velocity.y -= GRAVITY * 0.3f * dt;
        velocity *= (1.0f - WATER_DRAG * dt);
        if (in.jump) velocity.y = 4.0f; // swim up
        velocity.y = std::max(velocity.y, -4.0f);
    } else {
        velocity.y -= GRAVITY * dt;
        if (in.jump && onGround) { velocity.y = JUMP_VELOCITY; onGround = false; }
    }
    velocity.y = std::max(velocity.y, -60.0f);

    resolveCollisions(world, position, velocity, dt);
}

void Player::updateStats(World& world, float dt) {
    if (mode != GameMode::Survival) { health = maxHealth; return; }

    // Fall damage.
    if (onGround && !wasOnGround_) {
        float fall = fallStartY_ - position.y;
        if (fall > 3.0f && !inWater) health -= (fall - 3.0f) * 1.0f;
    }
    if (!onGround && wasOnGround_) fallStartY_ = position.y;
    if (!onGround && position.y > fallStartY_) fallStartY_ = position.y;
    wasOnGround_ = onGround;

    // Environmental damage.
    int fx = (int)std::floor(position.x);
    int fy = (int)std::floor(position.y + 0.5f);
    int fz = (int)std::floor(position.z);
    BlockId feet = world.getBlock(fx, fy, fz);
    if (feet == BLOCK_CACTUS) health -= 2.0f * dt;

    // Suffocation if head is inside a solid block.
    BlockId head = world.getBlock(fx, (int)std::floor(position.y + eyeHeight()), fz);
    if (Blocks::isOpaque(head)) health -= 1.0f * dt;

    // Slow natural regen while fed.
    if (hunger > 6.0f && health < maxHealth) {
        regenTimer_ += dt;
        if (regenTimer_ > 2.0f) { health += 1.0f; regenTimer_ = 0.0f; }
    }
    hunger = std::max(0.0f, hunger - dt * 0.02f);
    if (hunger <= 0.0f) health -= dt * 0.5f;

    health = std::clamp(health, 0.0f, maxHealth);
    if (health <= 0.0f) dead = true;
}

void Player::update(World& world, Camera& camera, const InputState& in, float dt) {
    camera.addMouseDelta(in.mouseDX, in.mouseDY, mouseSensitivity);

    // Double-tap jump toggles flight (Creative only).
    if (in.jumpPressed && mode == GameMode::Creative) {
        double now = glfwGetTime();
        if (now - lastJumpTime_ < 0.30) flying = !flying;
        lastJumpTime_ = now;
    }

    if (!dead) {
        applyMovement(world, camera, in, dt);
        updateStats(world, dt);
    }

    camera.position = position + glm::vec3(0, eyeHeight(), 0);
}

void Player::respawn(World& world) {
    health = maxHealth;
    hunger = 20.0f;
    dead = false;
    velocity = glm::vec3(0);
    int h = world.findSpawnHeight(0, 0);
    position = glm::vec3(0.5f, (float)h, 0.5f);
}

} // namespace mc
