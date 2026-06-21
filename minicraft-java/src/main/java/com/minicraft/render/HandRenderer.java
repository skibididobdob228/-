package com.minicraft.render;

import com.minicraft.world.Blocks;
import com.minicraft.world.Items;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Draws the selected item in first person with a swing/bob animation. */
public class HandRenderer {
    private int vao, vbo, count;
    private int builtItem = -2;
    private boolean builtAsCube;

    // Cube faces (pos corners) like the mesher, simplified with per-face shade.
    private static final int[][] FACE = {
        {  1, 0, 0, 1,0,0, 0,0,1, 0,1,0 },
        { -1, 0, 0, 0,0,1, 0,0,-1,0,1,0 },
        {  0, 1, 0, 0,1,1, 1,0,0, 0,0,-1},
        {  0,-1, 0, 0,0,0, 1,0,0, 0,0,1 },
        {  0, 0, 1, 1,0,1, -1,0,0,0,1,0 },
        {  0, 0,-1, 0,0,0, 1,0,0, 0,1,0 },
    };
    private static final float[] SHADE = {0.8f, 0.8f, 1f, 0.6f, 0.7f, 0.7f};

    public void init() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
    }

    private void uv(int tile, float s, float t, float[] out) {
        int col = tile % Tiles.ATLAS_TILES, row = tile / Tiles.ATLAS_TILES;
        float inv = 1f / Tiles.ATLAS_PX, inset = 0.5f * inv, ts = Tiles.TILE_PX * inv;
        out[0] = col * ts + inset + s * (ts - 2 * inset);
        out[1] = row * ts + inset + t * (ts - 2 * inset);
    }

    private void build(int item) {
        boolean isBlock = Items.isBlock(item);
        FloatBuffer fb;
        if (isBlock) {
            int b = Items.blockId(item);
            Blocks.Def def = Blocks.get(b);
            float[] data = new float[36 * 11];
            int p = 0;
            float[] t0 = new float[2], t1 = new float[2];
            for (int f = 0; f < 6; f++) {
                int[] fc = FACE[f];
                int tile = f == 2 ? def.texTop : f == 3 ? def.texBottom : def.texSide;
                float sh = SHADE[f];
                float[][] cs = {{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}};
                for (float[] c : cs) {
                    int cu = (int) c[0], cv = (int) c[1];
                    float px = fc[3] + cu * fc[6] + cv * fc[9];
                    float py = fc[4] + cu * fc[7] + cv * fc[10];
                    float pz = fc[5] + cu * fc[8] + cv * fc[11];
                    uv(tile, cu, cv == 1 ? 0f : 1f, t0);
                    data[p++] = px - 0.5f; data[p++] = py - 0.5f; data[p++] = pz - 0.5f;
                    data[p++] = t0[0]; data[p++] = t0[1];
                    data[p++] = fc[0]; data[p++] = fc[1]; data[p++] = fc[2];
                    data[p++] = sh; data[p++] = 1f; data[p++] = 0f;
                }
            }
            fb = BufferUtils.createFloatBuffer(data.length);
            fb.put(data).flip();
            count = 36;
        } else {
            // Flat quad showing the item icon.
            int tile = Items.icon(item);
            float[] t0 = new float[2], t1 = new float[2];
            uv(tile, 0, 1, t0); uv(tile, 1, 0, t1);
            float[] d = {
                -0.5f,-0.5f,0, t0[0],t0[1], 0,0,1, 1,1,0,
                 0.5f,-0.5f,0, t1[0],t0[1], 0,0,1, 1,1,0,
                 0.5f, 0.5f,0, t1[0],t1[1], 0,0,1, 1,1,0,
                -0.5f,-0.5f,0, t0[0],t0[1], 0,0,1, 1,1,0,
                 0.5f, 0.5f,0, t1[0],t1[1], 0,0,1, 1,1,0,
                -0.5f, 0.5f,0, t0[0],t1[1], 0,0,1, 1,1,0,
            };
            fb = BufferUtils.createFloatBuffer(d.length);
            fb.put(d).flip();
            count = 6;
        }
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        int stride = 11 * Float.BYTES;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(3); glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glBindVertexArray(0);
        builtItem = item; builtAsCube = isBlock;
    }

    /**
     * @param shader the chunk shader (already provides atlas sampling + lighting)
     * @param swing  0..1 attack/use swing progress
     * @param bob    walking bob phase
     */
    public void render(Shader shader, TextureAtlas atlas, int item, float aspect, float swing, float bob) {
        if (item < 0) return;
        if (item != builtItem) build(item);

        glClear(GL_DEPTH_BUFFER_BIT);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70), aspect, 0.01f, 10f);
        Matrix4f view = new Matrix4f(); // camera at origin looking -Z

        float swingAng = (float) Math.sin(swing * Math.PI);
        float bobX = (float) Math.cos(bob) * 0.02f;
        float bobY = (float) Math.abs(Math.sin(bob)) * 0.02f;

        Matrix4f model = new Matrix4f()
                .translate(0.58f + bobX, -0.46f + bobY - swingAng * 0.25f, -0.85f)
                .rotateY((float) Math.toRadians(-25 + swingAng * 20))
                .rotateX((float) Math.toRadians(8 + swingAng * 35))
                .rotateZ((float) Math.toRadians(swingAng * 10));
        if (builtAsCube) model.scale(0.5f);
        else model.scale(0.55f).rotateY((float) Math.toRadians(-20));

        shader.use();
        shader.set("uView", view);
        shader.set("uProj", proj);
        shader.set("uModel", model);
        shader.set("uDayLight", 1f);
        shader.set("uAmbient", 0.85f);
        shader.set("uFogStart", 100f);
        shader.set("uFogEnd", 200f);
        shader.set("uAlphaCutout", 1);
        shader.set("uCameraPos", new org.joml.Vector3f(0, 0, 0));
        atlas.bind(0);
        glDisable(GL_BLEND);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, count);
        glBindVertexArray(0);
    }
}
