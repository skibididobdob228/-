package com.minicraft.ui;

import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * A monospaced bitmap font baked into a GL texture using AWT at startup
 * (printable ASCII 32..126 laid out in a 16x6 grid).
 */
public class Font {
    public static final int FIRST = 32, LAST = 126;
    public static final int COLS = 16, ROWS = 6;
    public final int cell = 16;       // pixels per glyph cell
    public final int glyphW = 8;      // visible advance width
    private int texture;
    private int texW, texH;

    public void bake() {
        texW = COLS * cell;
        texH = ROWS * cell;
        BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 14));
        g.setColor(Color.WHITE);
        var fm = g.getFontMetrics();
        for (int c = FIRST; c <= LAST; c++) {
            int i = c - FIRST;
            int col = i % COLS, row = i / COLS;
            String s = String.valueOf((char) c);
            int x = col * cell + 2;
            int y = row * cell + fm.getAscent();
            g.drawString(s, x, y);
        }
        g.dispose();

        ByteBuffer buf = BufferUtils.createByteBuffer(texW * texH * 4);
        for (int y = 0; y < texH; y++)
            for (int x = 0; x < texW; x++) {
                int argb = img.getRGB(x, y);
                buf.put((byte) ((argb >> 16) & 0xFF));
                buf.put((byte) ((argb >> 8) & 0xFF));
                buf.put((byte) (argb & 0xFF));
                buf.put((byte) ((argb >> 24) & 0xFF));
            }
        buf.flip();

        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texW, texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int texture() { return texture; }

    public float[] uv(char c) {
        if (c < FIRST || c > LAST) c = '?';
        int i = c - FIRST;
        int col = i % COLS, row = i / COLS;
        float u0 = (float) col / COLS, v0 = (float) row / ROWS;
        float u1 = u0 + 1f / COLS, v1 = v0 + 1f / ROWS;
        return new float[]{u0, v0, u1, v1};
    }

    public float width(String text, float scale) {
        return text.length() * glyphW * scale;
    }
}
