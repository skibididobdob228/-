package com.minicraft.ui;

import com.minicraft.player.Player;
import com.minicraft.render.Shader;
import com.minicraft.render.TextureAtlas;
import com.minicraft.world.Blocks;
import com.minicraft.world.Inventory;
import com.minicraft.world.ItemStack;
import com.minicraft.world.Items;
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

/** 2D overlay toolkit: panels, atlas icons and text, with automatic batching. */
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
    private int sw, sh;
    private int pendingTex = 0;      // 0 = solid
    private Matrix4f proj = new Matrix4f();
    public TextureAtlas atlas;
    public Font font;

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

    public void begin(int sw, int sh) {
        this.sw = sw; this.sh = sh;
        proj = new Matrix4f().ortho(0, sw, sh, 0, -1, 1);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        verts.clear();
        pendingTex = 0;
    }

    public void end() {
        flush();
        glEnable(GL_DEPTH_TEST);
    }

    private void use(int tex) {
        if (!verts.isEmpty() && tex != pendingTex) flush();
        pendingTex = tex;
    }

    private void v(float x, float y, float u, float w, float r, float g, float b, float a) {
        verts.add(x); verts.add(y); verts.add(u); verts.add(w);
        verts.add(r); verts.add(g); verts.add(b); verts.add(a);
    }

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        use(0);
        v(x, y, 0, 0, r, g, b, a); v(x + w, y, 0, 0, r, g, b, a); v(x + w, y + h, 0, 0, r, g, b, a);
        v(x, y, 0, 0, r, g, b, a); v(x + w, y + h, 0, 0, r, g, b, a); v(x, y + h, 0, 0, r, g, b, a);
    }

    /** Bevelled panel (Minecraft-ish grey GUI box). */
    public void panel(float x, float y, float w, float h) {
        rect(x - 2, y - 2, w + 4, h + 4, 0.10f, 0.10f, 0.12f, 0.95f);
        rect(x, y, w, h, 0.78f, 0.78f, 0.80f, 1f);
        rect(x, y, w, 2, 1f, 1f, 1f, 1f);
        rect(x, y, 2, h, 1f, 1f, 1f, 1f);
        rect(x, y + h - 2, w, 2, 0.45f, 0.45f, 0.48f, 1f);
        rect(x + w - 2, y, 2, h, 0.45f, 0.45f, 0.48f, 1f);
    }

    public void slot(float x, float y, float s) {
        rect(x, y, s, s, 0.55f, 0.55f, 0.58f, 1f);
        rect(x + 1, y + 1, s - 2, s - 2, 0.32f, 0.32f, 0.35f, 1f);
    }

    private void texQuad(int tex, float x, float y, float w, float h,
                         float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        use(tex);
        v(x, y, u0, v0, r, g, b, a); v(x + w, y, u1, v0, r, g, b, a); v(x + w, y + h, u1, v1, r, g, b, a);
        v(x, y, u0, v0, r, g, b, a); v(x + w, y + h, u1, v1, r, g, b, a); v(x, y + h, u0, v1, r, g, b, a);
    }

    public void icon(int tile, float x, float y, float size) {
        float ts = (float) TILE_PX / ATLAS_PX;
        int col = tile % ATLAS_TILES, row = tile / ATLAS_TILES;
        float u0 = col * ts, vv0 = row * ts;
        texQuad(atlas.id(), x, y, size, size, u0, vv0, u0 + ts, vv0 + ts, 1, 1, 1, 1);
    }

    public void item(ItemStack st, float x, float y, float size) {
        if (st.isEmpty()) return;
        icon(Items.icon(st.item), x, y, size);
        if (st.count > 1) text(String.valueOf(st.count), x + size - 2, y + size - 9, 1f, 1, 1, 1, 1, true);
    }

    public void text(String s, float x, float y, float scale, float r, float g, float b, float a) {
        text(s, x, y, scale, r, g, b, a, false);
    }

    public void text(String s, float x, float y, float scale, float r, float g, float b, float a, boolean rightAlign) {
        float gw = font.glyphW * scale;
        float gh = font.cell * scale;
        float startX = rightAlign ? x - font.width(s, scale) : x;
        // drop shadow
        drawGlyphs(s, startX + 1, y + 1, gw, gh, scale, 0.1f, 0.1f, 0.1f, a);
        drawGlyphs(s, startX, y, gw, gh, scale, r, g, b, a);
    }

    private void drawGlyphs(String s, float x, float y, float gw, float gh, float scale,
                            float r, float g, float b, float a) {
        for (int i = 0; i < s.length(); i++) {
            float[] uv = font.uv(s.charAt(i));
            texQuad(font.texture(), x + i * gw, y, font.cell * scale, gh, uv[0], uv[1], uv[2], uv[3], r, g, b, a);
        }
    }

    public void textCentered(String s, float cx, float y, float scale, float r, float g, float b, float a) {
        text(s, cx - font.width(s, scale) / 2f, y, scale, r, g, b, a);
    }

    private void flush() {
        if (verts.isEmpty()) return;
        shader.use();
        shader.set("uProj", proj);
        shader.set("uTex", 0);
        shader.set("uTextured", pendingTex != 0 ? 1 : 0);
        if (pendingTex != 0) { glActiveTextureSafe(); glBindTexture(GL_TEXTURE_2D, pendingTex); }
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

    private void glActiveTextureSafe() {
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
    }

    // --- In-game HUD (crosshair, hotbar, stats) ---

    public void renderGame(int sw, int sh, Player player) {
        begin(sw, sh);
        float cx = sw * 0.5f, cy = sh * 0.5f;
        rect(cx - 10, cy - 1.5f, 20, 3, 1, 1, 1, 0.85f);
        rect(cx - 1.5f, cy - 10, 3, 20, 1, 1, 1, 0.85f);

        Inventory inv = player.inventory;
        float slot = 50, pad = 4;
        float barW = Inventory.HOTBAR * slot;
        float barX = cx - barW * 0.5f;
        float barY = sh - slot - 12;
        rect(barX - pad, barY - pad, barW + 2 * pad, slot + 2 * pad, 0, 0, 0, 0.45f);
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            float a = i == inv.selected ? 0.28f : 0.10f;
            rect(barX + i * slot + 2, barY + 2, slot - 4, slot - 4, 1, 1, 1, a);
        }
        float sx = barX + inv.selected * slot;
        rect(sx - 2, barY - 2, slot + 4, 3, 1, 1, 1, 0.95f);
        rect(sx - 2, barY + slot - 1, slot + 4, 3, 1, 1, 1, 0.95f);
        rect(sx - 2, barY - 2, 3, slot + 4, 1, 1, 1, 0.95f);
        rect(sx + slot - 1, barY - 2, 3, slot + 4, 1, 1, 1, 0.95f);
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            ItemStack st = inv.slots[i];
            if (!st.isEmpty()) item(st, barX + i * slot + 8, barY + 8, slot - 16);
        }

        if (player.mode == Player.Mode.SURVIVAL) {
            float icon = 18, hx = barX, hy = barY - 26;
            for (int i = 0; i < 10; i++) {
                rect(hx + i * (icon + 1), hy, icon, icon, 0.15f, 0.15f, 0.15f, 0.6f);
                float hp = player.health - i * 2f;
                if (hp > 0) rect(hx + i * (icon + 1), hy, icon * (hp >= 2 ? 1 : 0.5f), icon, 0.85f, 0.1f, 0.15f, 1f);
            }
            float gx = barX + barW - 10 * (icon + 1), gy = hy - 22;
            for (int i = 0; i < 10; i++) {
                rect(gx + i * (icon + 1), gy, icon, icon, 0.15f, 0.15f, 0.15f, 0.6f);
                float hg = player.hunger - i * 2f;
                if (hg > 0) rect(gx + i * (icon + 1), gy, icon * (hg >= 2 ? 1 : 0.5f), icon, 0.6f, 0.4f, 0.15f, 1f);
            }
        }
        // Held item name above the hotbar.
        ItemStack sel = inv.selectedStack();
        if (!sel.isEmpty()) textCentered(Items.name(sel.item).replace('_', ' '), cx, barY - 44, 1.4f, 1, 1, 1, 1);
        end();
    }

    public void overlay(int sw, int sh, float r, float g, float b, float a) {
        begin(sw, sh);
        rect(0, 0, sw, sh, r, g, b, a);
        end();
    }

    public int sw() { return sw; }
    public int sh() { return sh; }
}
