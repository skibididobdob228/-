#pragma once
#include <glm/glm.hpp>
#include <array>

namespace mc {

// View/projection provider plus a frustum for culling. The camera itself is
// passive: the player controller writes position/yaw/pitch each frame.
class Camera {
public:
    glm::vec3 position{0.0f, 80.0f, 0.0f};
    float yaw = -90.0f;   // degrees
    float pitch = 0.0f;   // degrees
    float fov = 70.0f;
    float aspect = 16.0f / 9.0f;
    float nearPlane = 0.05f;
    float farPlane = 1000.0f;

    glm::vec3 front() const;
    glm::vec3 right() const;
    glm::vec3 up() const;

    // Horizontal-only forward (for walking direction).
    glm::vec3 forwardXZ() const;

    glm::mat4 view() const;
    glm::mat4 projection() const;

    void addMouseDelta(float dx, float dy, float sensitivity);

    // Frustum planes derived from view*proj for chunk culling.
    void updateFrustum();
    bool aabbVisible(const glm::vec3& min, const glm::vec3& max) const;

private:
    std::array<glm::vec4, 6> planes_{}; // a,b,c,d
};

} // namespace mc
