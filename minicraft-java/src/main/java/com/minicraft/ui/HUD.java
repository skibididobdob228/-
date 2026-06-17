package com.minicraft.ui;

import com.minicraft.player.Player;
import com.minicraft.render.Shader;
import com.minicraft.render.TextureAtlas;
import com.minicraft.world.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.minicraft.render.Tiles.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Immediate-mode 2D overlay: crosshair, hotbar, item icons, health & hunger. */
public class HUD {
    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in vec4 aColor;
        uniform mat4 uProj;
        out vec2 vUV; out vec4 vColor;
        void main(){ vUV=aUV; vColor=aColor; gl_Position=uProj*vec4(aPos,0.0,1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV; in vec4 vColor; out vec4 FragColor;
        uniform sampler2D uTex; uniform int uTextured;
        void main(){
            if(uTextured==1){ vec4 t=texture(uTex,vUV); if(t.a<0.05) discard; FragColor=t*vColor; }
            else FragColor=vColor;
        }
        """;

    private final Shader shader = new Shader();
    private int vao, vbo;
    private final List<Float> verts = new ArrayList<>();

    public void init() {
        shader.compile(VERT, FRAG);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glBindVertexArray(0);
    }

    private void v(float x, float y, float u, float w, float r, float g, float b, float a) {
        verts.add(x); verts.add(y); verts.add(u); verts.add(w);
        verts.add(r); verts.add(g); verts.add(b); verts.add(a);
    }

    private void quad(float x, float y, float w, float h, float r, float g, float b, float a) {
        v(x, y, -1, -1, r, g, b, a); v(x + w, y, -1, -1, r, g, b, a); v(x + w, y + h, -1, -1, r, g, b, a);
        v(x, y, -1, -1, r, g, b, a); v(x + w, y + h, -1, -1, r, g, b, a); v(x, y + h, -1, -1, r, g, b, a);
    }

    private void texQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        v(x, y, u0, v0, 1, 1, 1, 1); v(x + w, y, u1, v0, 1, 1, 1, 1); v(x + w, y + h, u1, v1, 1, 1, 1, 1);
        v(x, y, u0, v0, 1, 1, 1, 1); v(x + w, y + h, u1, v1, 1, 1, 1, 1); v(x, y + h, u0, v1, 1, 1, 1, 1);
    }

    private void flush(int sw, int sh, boolean textured) {
        if (verts.isEmpty()) return;
        Matrix4f proj = new Matrix4f().ortho(0, sw, sh, 0, -1, 1);
        shader.use();
        shader.set("uProj", proj);
        shader.set("uTex", 0);
        shader.set("uTextured", textured ? 1 : 0);
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.size());
        for (float f : verts) fb.put(f);
        fb.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, verts.size() / 8);
        glBindVertexArray(0);
        verts.clear();
    }

    public void overlay(int sw, int sh, float r, float g, float b, float a) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quad(0, 0, sw, sh, r, g, b, a);
        flush(sw, sh, false);
    }

    public void render(int sw, int sh, Player player, TextureAtlas atlas) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float cx = sw * 0.5f, cy = sh * 0.5f;
        quad(cx - 10, cy - 1.5f, 20, 3, 1, 1, 1, 0.85f);
        quad(cx - 1.5f, cy - 10, 3, 20, 1, 1, 1, 0.85f);
        flush(sw, sh, false);

        float slot = 50, pad = 4;
        float barW = Player.HOTBAR_SIZE * slot;
        float barX = cx - barW * 0.5f;
        float barY = sh - slot - 12;
        quad(barX - pad, barY - pad, barW + 2 * pad, slot + 2 * pad, 0, 0, 0, 0.45f);
        for (int i = 0; i < Player.HOTBAR_SIZE; i++) {
            float a = i == player.selectedSlot ? 0.25f : 0.08f;
            quad(barX + i * slot + 2, barY + 2, slot - 4, slot - 4, 1, 1, 1, a);
        }
        float sx = barX + player.selectedSlot * slot;
        quad(sx - 2, barY - 2, slot + 4, 3, 1, 1, 1, 0.9f);
        quad(sx - 2, barY + slot - 1, slot + 4, 3, 1, 1, 1, 0.9f);
        quad(sx - 2, barY - 2, 3, slot + 4, 1, 1, 1, 0.9f);
        quad(sx + slot - 1, barY - 2, 3, slot + 4, 1, 1, 1, 0.9f);
        flush(sw, sh, false);

        atlas.bind(0);
        float ts = (float) TILE_PX / ATLAS_PX;
        for (int i = 0; i < Player.HOTBAR_SIZE; i++) {
            int id = player.hotbar[i];
            if (id == Blocks.AIR) continue;
            int tile = Blocks.get(id).texSide;
            int col = tile % ATLAS_TILES, row = tile / ATLAS_TILES;
            float u0 = col * ts, v0 = row * ts;
            float ip = 8;
            texQuad(barX + i * slot + ip, barY + ip, slot - 2 * ip, slot - 2 * ip, u0, v0, u0 + ts, v0 + ts);
        }
        flush(sw, sh, true);

        if (player.mode == Player.Mode.SURVIVAL) {
            float icon = 18, hx = barX, hy = barY - 26;
            for (int i = 0; i < 10; i++) {
                quad(hx + i * (icon + 1), hy, icon, icon, 0.15f, 0.15f, 0.15f, 0.6f);
                float hp = player.health - i * 2f;
                if (hp > 0) {
                    float fill = hp >= 2f ? 1f : 0.5f;
                    quad(hx + i * (icon + 1), hy, icon * fill, icon, 0.85f, 0.1f, 0.15f, 1f);
                }
            }
            float gx = barX + barW - 10 * (icon + 1), gy = hy - 22;
            for (int i = 0; i < 10; i++) {
                quad(gx + i * (icon + 1), gy, icon, icon, 0.15f, 0.15f, 0.15f, 0.6f);
                float hg = player.hunger - i * 2f;
                if (hg > 0) {
                    float fill = hg >= 2f ? 1f : 0.5f;
                    quad(gx + i * (icon + 1), gy, icon * fill, icon, 0.6f, 0.4f, 0.15f, 1f);
                }
            }
            flush(sw, sh, false);
        }
        glEnable(GL_DEPTH_TEST);
    }
}
