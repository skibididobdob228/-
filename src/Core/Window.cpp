#include "Core/Window.h"
#include "Core/Logger.h"

#include <GL/glew.h>
#include <GLFW/glfw3.h>
#include <cstdlib>

namespace mc {

static void glfwErrorCallback(int code, const char* desc) {
    LOG_ERROR("GLFW error %d: %s", code, desc);
}

Window::Window(int width, int height, const std::string& title)
    : width_(width), height_(height) {
    glfwSetErrorCallback(glfwErrorCallback);
    if (!glfwInit()) {
        LOG_ERROR("Failed to initialise GLFW");
        std::exit(1);
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
    glfwWindowHint(GLFW_SAMPLES, 4); // 4x MSAA

    window_ = glfwCreateWindow(width_, height_, title.c_str(), nullptr, nullptr);
    if (!window_) {
        LOG_ERROR("Failed to create window");
        glfwTerminate();
        std::exit(1);
    }
    glfwMakeContextCurrent(window_);
    glfwSwapInterval(1); // vsync

    glewExperimental = GL_TRUE;
    GLenum err = glewInit();
    if (err != GLEW_OK) {
        LOG_ERROR("Failed to init GLEW: %s", glewGetErrorString(err));
        std::exit(1);
    }
    // GLEW can leave a benign GL_INVALID_ENUM on core profiles.
    glGetError();

    LOG_INFO("OpenGL %s | %s", glGetString(GL_VERSION), glGetString(GL_RENDERER));

    glfwSetWindowUserPointer(window_, this);
    glfwSetFramebufferSizeCallback(window_, [](GLFWwindow* w, int width, int height) {
        auto* self = static_cast<Window*>(glfwGetWindowUserPointer(w));
        self->width_ = width;
        self->height_ = height;
        glViewport(0, 0, width, height);
        if (self->onResize) self->onResize(width, height);
    });
    glfwSetKeyCallback(window_, [](GLFWwindow* w, int key, int, int action, int mods) {
        auto* self = static_cast<Window*>(glfwGetWindowUserPointer(w));
        if (self->onKey) self->onKey(key, action, mods);
    });
    glfwSetScrollCallback(window_, [](GLFWwindow* w, double xo, double yo) {
        auto* self = static_cast<Window*>(glfwGetWindowUserPointer(w));
        if (self->onScroll) self->onScroll(xo, yo);
    });
    glfwSetMouseButtonCallback(window_, [](GLFWwindow* w, int b, int action, int mods) {
        auto* self = static_cast<Window*>(glfwGetWindowUserPointer(w));
        if (self->onMouseButton) self->onMouseButton(b, action, mods);
    });

    setCursorCaptured(true);
    glViewport(0, 0, width_, height_);
}

Window::~Window() {
    if (window_) glfwDestroyWindow(window_);
    glfwTerminate();
}

bool Window::shouldClose() const { return glfwWindowShouldClose(window_); }
void Window::setShouldClose(bool v) { glfwSetWindowShouldClose(window_, v ? GLFW_TRUE : GLFW_FALSE); }
void Window::swapBuffers() { glfwSwapBuffers(window_); }
void Window::pollEvents() { glfwPollEvents(); }

void Window::setCursorCaptured(bool captured) {
    cursorCaptured_ = captured;
    glfwSetInputMode(window_, GLFW_CURSOR,
                     captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    if (captured && glfwRawMouseMotionSupported())
        glfwSetInputMode(window_, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
}

void Window::toggleFullscreen() {
    fullscreen_ = !fullscreen_;
    if (fullscreen_) {
        glfwGetWindowPos(window_, &windowedX_, &windowedY_);
        glfwGetWindowSize(window_, &windowedW_, &windowedH_);
        GLFWmonitor* mon = glfwGetPrimaryMonitor();
        const GLFWvidmode* mode = glfwGetVideoMode(mon);
        glfwSetWindowMonitor(window_, mon, 0, 0, mode->width, mode->height, mode->refreshRate);
    } else {
        glfwSetWindowMonitor(window_, nullptr, windowedX_, windowedY_,
                             windowedW_, windowedH_, 0);
    }
    glfwSwapInterval(1);
}

} // namespace mc
