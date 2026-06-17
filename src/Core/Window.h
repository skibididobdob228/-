#pragma once
#include <string>
#include <functional>

struct GLFWwindow;

namespace mc {

// Owns the GLFW window + OpenGL context. Input is polled from here and also
// dispatched through callbacks for scroll / key events that are edge-triggered.
class Window {
public:
    Window(int width, int height, const std::string& title);
    ~Window();

    Window(const Window&) = delete;
    Window& operator=(const Window&) = delete;

    bool shouldClose() const;
    void swapBuffers();
    void pollEvents();
    void setShouldClose(bool v);

    GLFWwindow* handle() const { return window_; }
    int width() const { return width_; }
    int height() const { return height_; }
    float aspect() const { return height_ > 0 ? float(width_) / float(height_) : 1.0f; }

    void setCursorCaptured(bool captured);
    bool cursorCaptured() const { return cursorCaptured_; }
    void toggleFullscreen();

    // Edge-triggered callbacks.
    std::function<void(int /*key*/, int /*action*/, int /*mods*/)> onKey;
    std::function<void(double /*xoffset*/, double /*yoffset*/)> onScroll;
    std::function<void(int /*button*/, int /*action*/, int /*mods*/)> onMouseButton;
    std::function<void(int /*w*/, int /*h*/)> onResize;

private:
    GLFWwindow* window_ = nullptr;
    int width_;
    int height_;
    bool cursorCaptured_ = true;
    bool fullscreen_ = false;
    int windowedX_ = 0, windowedY_ = 0, windowedW_ = 0, windowedH_ = 0;
};

} // namespace mc
