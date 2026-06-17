package com.minicraft.core;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Owns the GLFW window + OpenGL context and exposes input callbacks. */
public class Window {
    private long handle;
    private int width, height;
    private boolean cursorCaptured = true;
    private boolean fullscreen = false;
    private int savedX, savedY, savedW, savedH;

    public interface KeyCallback { void onKey(int key, int action, int mods); }
    public interface ScrollCallback { void onScroll(double dx, double dy); }
    public interface ResizeCallback { void onResize(int w, int h); }

    public KeyCallback onKey;
    public ScrollCallback onScroll;
    public ResizeCallback onResize;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        GL.createCapabilities();
        System.out.println("[INFO] OpenGL " + glGetString(GL_VERSION) + " | " + glGetString(GL_RENDERER));

        glfwSetFramebufferSizeCallback(handle, (w, fw, fh) -> {
            this.width = fw; this.height = fh;
            glViewport(0, 0, fw, fh);
            if (onResize != null) onResize.onResize(fw, fh);
        });
        glfwSetKeyCallback(handle, (w, key, sc, action, mods) -> {
            if (onKey != null) onKey.onKey(key, action, mods);
        });
        glfwSetScrollCallback(handle, (w, dx, dy) -> {
            if (onScroll != null) onScroll.onScroll(dx, dy);
        });

        setCursorCaptured(true);
        glViewport(0, 0, width, height);
    }

    public long handle() { return handle; }
    public int width() { return width; }
    public int height() { return height; }
    public float aspect() { return height > 0 ? (float) width / height : 1f; }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void setShouldClose(boolean v) { glfwSetWindowShouldClose(handle, v); }
    public void swapBuffers() { glfwSwapBuffers(handle); }
    public void pollEvents() { glfwPollEvents(); }
    public void setTitle(String t) { glfwSetWindowTitle(handle, t); }
    public boolean keyDown(int key) { return glfwGetKey(handle, key) == GLFW_PRESS; }
    public boolean mouseDown(int button) { return glfwGetMouseButton(handle, button) == GLFW_PRESS; }

    public double[] cursorPos() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocDouble(1);
            var y = stack.mallocDouble(1);
            glfwGetCursorPos(handle, x, y);
            return new double[]{x.get(0), y.get(0)};
        }
    }

    public void setCursorCaptured(boolean captured) {
        this.cursorCaptured = captured;
        glfwSetInputMode(handle, GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        if (captured && glfwRawMouseMotionSupported())
            glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
    }

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
                glfwGetWindowPos(handle, x, y);
                savedX = x.get(0); savedY = y.get(0);
            }
            savedW = width; savedH = height;
            long mon = glfwGetPrimaryMonitor();
            var mode = glfwGetVideoMode(mon);
            glfwSetWindowMonitor(handle, mon, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(handle, NULL, savedX, savedY, savedW, savedH, 0);
        }
        glfwSwapInterval(1);
    }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
