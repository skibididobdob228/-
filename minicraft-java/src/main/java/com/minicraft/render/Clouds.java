package com.minicraft.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_REPEAT;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** A large scrolling, blocky cloud layer drawn high above the world. */
public class Clouds {
    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        uniform mat4 uModel, uView, uProj;
        uniform vec2 uOffset;
        out vec2 vUV; out vec3 vWorld;
        void main(){
            vec4 w = uModel * vec4(aPos,1.0);
            vWorld = w.xyz;
            vUV = w.xz * 0.0125 + uOffset;
            gl_Position = uProj * uView * w;
        }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV; in vec3 vWorld; out vec4 FragColor;
        uniform sampler2D uTex; uniform vec3 uFog; uniform vec3 uCam; uniform float uFar;
        void main(){
            vec4 c = texture(uTex, vUV);
            if(c.a < 0.1) discard;
            float d = length(vWorld.xz - uCam.xz);
            float fade = clamp(1.0 - d/uFar, 0.0, 1.0);
            vec3 col = mix(uFog, c.rgb, fade);
            FragColor = vec4(col, c.a * (0.55 + 0.35*fade));
        }
        """;

    private final Shader shader = new Shader();
    private int vao, vbo, tex;
    private final float size = 3000f;

    public void init() {
        shader.compile(VERT, FRAG);
        float s = size;
        float[] quad = { -s,0,-s,  s,0,-s,  s,0,s,  -s,0,-s,  s,0,s,  -s,0,s };
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(quad.length);
        fb.put(quad).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glBindVertexArray(0);
        bakeTexture();
    }

    private void bakeTexture() {
        int N = 64;
        byte[] px = new byte[N * N * 4];
        java.util.Random rng = new java.util.Random(99);
        float[] field = new float[N * N];
        // Coarse value noise -> smooth puffy clouds.
        int C = 8;
        float[] coarse = new float[(C + 1) * (C + 1)];
        for (int i = 0; i < coarse.length; i++) coarse[i] = rng.nextFloat();
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float fx = x / (float) N * C, fy = y / (float) N * C;
                int x0 = (int) fx, y0 = (int) fy;
                float tx = fx - x0, ty = fy - y0;
                float a = coarse[y0 * (C + 1) + x0], b = coarse[y0 * (C + 1) + x0 + 1];
                float c = coarse[(y0 + 1) * (C + 1) + x0], d = coarse[(y0 + 1) * (C + 1) + x0 + 1];
                float top = a + (b - a) * tx, bot = c + (d - c) * tx;
                field[y * N + x] = top + (bot - top) * ty;
            }
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                boolean cloud = field[y * N + x] > 0.62f;
                int i = (y * N + x) * 4;
                int shade = 235 + (int) ((field[y * N + x] - 0.62f) * 40);
                if (shade > 255) shade = 255;
                px[i] = (byte) shade; px[i + 1] = (byte) shade; px[i + 2] = (byte) Math.min(255, shade + 5);
                px[i + 3] = (byte) (cloud ? 255 : 0);
            }
        ByteBuffer buf = BufferUtils.createByteBuffer(px.length);
        buf.put(px).flip();
        tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, N, N, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void render(Matrix4f view, Matrix4f proj, Vector3f cam, float y, float time, Vector3f fog, float far) {
        shader.use();
        shader.set("uModel", new Matrix4f().translate(cam.x, y, cam.z));
        shader.set("uView", view);
        shader.set("uProj", proj);
        shader.set("uOffset", new org.joml.Vector2f(time * 0.003f, 0f));
        shader.set("uTex", 0);
        shader.set("uFog", fog);
        shader.set("uCam", cam);
        shader.set("uFar", far);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glEnable(GL_BLEND);
        glDepthMask(false);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glDepthMask(true);
    }
}
