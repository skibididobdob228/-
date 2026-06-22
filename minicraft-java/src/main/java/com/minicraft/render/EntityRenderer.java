package com.minicraft.render;

import com.minicraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Renders mobs as collections of flat-shaded coloured boxes. */
public class EntityRenderer {
    private final Shader shader = new Shader();
    private int vao, vbo;

    public void init() {
        shader.loadFromResources("/shaders/entity.vert", "/shaders/entity.frag");
        // Unit cube centred at origin: position + normal per vertex.
        float[] c = cube();
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(c.length);
        fb.put(c).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glBindVertexArray(0);
    }

    public void begin(Matrix4f view, Matrix4f proj, Vector3f sun, float dayLight, float ambient,
                      Vector3f fog, float fogStart, float fogEnd, Vector3f cam) {
        shader.use();
        shader.set("uView", view); shader.set("uProj", proj);
        shader.set("uSunDir", sun); shader.set("uDayLight", dayLight); shader.set("uAmbient", ambient);
        shader.set("uFogColor", fog); shader.set("uFogStart", fogStart); shader.set("uFogEnd", fogEnd);
        shader.set("uCameraPos", cam);
        glBindVertexArray(vao);
    }

    public void render(Entity e) {
        Matrix4f base = new Matrix4f().translate(e.position.x, e.position.y, e.position.z)
                .rotateY((float) Math.toRadians(-e.yaw + 90));
        for (Entity.Box b : e.parts()) {
            Matrix4f m = new Matrix4f(base).translate(b.cx, b.cy, b.cz).scale(b.sx, b.sy, b.sz);
            shader.set("uModel", m);
            shader.set("uColor", new Vector3f(b.r, b.g, b.b));
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }
    }

    public void end() { glBindVertexArray(0); }

    private static float[] cube() {
        // 6 faces * 2 tris * 3 verts, each: pos(3)+normal(3)
        float[][] faces = {
            // +X
            {0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f},
            // -X
            {-0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f},
            // +Y
            {-0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f},
            // -Y
            {-0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f},
            // +Z
            {0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,-0.5f,0.5f},
            // -Z
            {-0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f, -0.5f,-0.5f,-0.5f},
        };
        float[][] normals = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        float[] out = new float[36 * 6];
        int p = 0;
        for (int f = 0; f < 6; f++) {
            float[] face = faces[f]; float[] n = normals[f];
            for (int v = 0; v < 6; v++) {
                out[p++] = face[v * 3]; out[p++] = face[v * 3 + 1]; out[p++] = face[v * 3 + 2];
                out[p++] = n[0]; out[p++] = n[1]; out[p++] = n[2];
            }
        }
        return out;
    }
}
