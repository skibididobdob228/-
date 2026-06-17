#pragma once

namespace mc {

// Per-frame input snapshot filled by the Game from GLFW state.
struct InputState {
    bool forward = false, back = false, left = false, right = false;
    bool jump = false;      // space held
    bool sneak = false;     // shift held
    bool sprint = false;    // ctrl held
    float mouseDX = 0.0f, mouseDY = 0.0f;
    bool jumpPressed = false; // edge: space pressed this frame (for fly toggle)
};

} // namespace mc
