#include "Render/Camera.h"
#include <glm/gtc/matrix_transform.hpp>
#include <algorithm>
#include <cmath>

namespace mc {

static constexpr float DEG2RAD = 3.14159265358979f / 180.0f;

glm::vec3 Camera::front() const {
    float cy = std::cos(yaw * DEG2RAD), sy = std::sin(yaw * DEG2RAD);
    float cp = std::cos(pitch * DEG2RAD), sp = std::sin(pitch * DEG2RAD);
    return glm::normalize(glm::vec3(cy * cp, sp, sy * cp));
}

glm::vec3 Camera::forwardXZ() const {
    float cy = std::cos(yaw * DEG2RAD), sy = std::sin(yaw * DEG2RAD);
    return glm::normalize(glm::vec3(cy, 0.0f, sy));
}

glm::vec3 Camera::right() const {
    return glm::normalize(glm::cross(front(), glm::vec3(0, 1, 0)));
}

glm::vec3 Camera::up() const {
    return glm::normalize(glm::cross(right(), front()));
}

glm::mat4 Camera::view() const {
    return glm::lookAt(position, position + front(), glm::vec3(0, 1, 0));
}

glm::mat4 Camera::projection() const {
    return glm::perspective(glm::radians(fov), aspect, nearPlane, farPlane);
}

void Camera::addMouseDelta(float dx, float dy, float sensitivity) {
    yaw += dx * sensitivity;
    pitch -= dy * sensitivity;
    pitch = std::clamp(pitch, -89.5f, 89.5f);
}

void Camera::updateFrustum() {
    glm::mat4 m = projection() * view();
    // Gribb/Hartmann plane extraction.
    for (int i = 0; i < 3; ++i) {
        planes_[i * 2 + 0] = glm::vec4(m[0][3] + m[0][i], m[1][3] + m[1][i],
                                       m[2][3] + m[2][i], m[3][3] + m[3][i]);
        planes_[i * 2 + 1] = glm::vec4(m[0][3] - m[0][i], m[1][3] - m[1][i],
                                       m[2][3] - m[2][i], m[3][3] - m[3][i]);
    }
    for (auto& p : planes_) {
        float len = glm::length(glm::vec3(p));
        if (len > 0) p /= len;
    }
}

bool Camera::aabbVisible(const glm::vec3& mn, const glm::vec3& mx) const {
    for (const auto& p : planes_) {
        glm::vec3 n(p);
        // Pick the positive vertex (farthest along plane normal).
        glm::vec3 pv(n.x >= 0 ? mx.x : mn.x,
                     n.y >= 0 ? mx.y : mn.y,
                     n.z >= 0 ? mx.z : mn.z);
        if (glm::dot(n, pv) + p.w < 0) return false;
    }
    return true;
}

} // namespace mc
