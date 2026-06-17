package com.minicraft.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/** Compiles/links a vertex+fragment program and caches uniform locations. */
public class Shader {
    private int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public void compile(String vertexSrc, String fragmentSrc) {
        int vs = stage(GL_VERTEX_SHADER, vertexSrc);
        int fs = stage(GL_FRAGMENT_SHADER, fragmentSrc);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0)
            throw new RuntimeException("Link error: " + glGetProgramInfoLog(program));
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    public void loadFromResources(String vertPath, String fragPath) {
        compile(readResource(vertPath), readResource(fragPath));
    }

    public static String readResource(String path) {
        try (InputStream in = Shader.class.getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int stage(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0)
            throw new RuntimeException("Compile error: " + glGetShaderInfoLog(s));
        return s;
    }

    public void use() { glUseProgram(program); }

    private int loc(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void set(String n, int v) { glUniform1i(loc(n), v); }
    public void set(String n, float v) { glUniform1f(loc(n), v); }
    public void set(String n, Vector3f v) { glUniform3f(loc(n), v.x, v.y, v.z); }
    public void set(String n, Vector4f v) { glUniform4f(loc(n), v.x, v.y, v.z, v.w); }

    public void set(String n, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(loc(n), false, m.get(stack.mallocFloat(16)));
        }
    }
}
