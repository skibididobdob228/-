package com.minicraft.math;

import java.util.Random;

/** Classic Perlin noise (2D/3D) with fractal Brownian motion helpers. */
public final class Noise {
    private final int[] p = new int[512];

    public Noise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float a, float b, float t) { return a + t * (b - a); }

    private static float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public float perlin3(float x, float y, float z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;
        x -= (float) Math.floor(x);
        y -= (float) Math.floor(y);
        z -= (float) Math.floor(z);
        float u = fade(x), v = fade(y), w = fade(z);
        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z;
        int B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
        return lerp(
            lerp(lerp(grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z), u),
                 lerp(grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z), u), v),
            lerp(lerp(grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1), u),
                 lerp(grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1), u), v),
            w);
    }

    public float perlin2(float x, float y) { return perlin3(x, y, 0f); }

    public float fbm2(float x, float y, int octaves) { return fbm2(x, y, octaves, 2f, 0.5f); }

    public float fbm2(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * perlin2(x * freq, y * freq);
            norm += amp; amp *= gain; freq *= lacunarity;
        }
        return norm > 0 ? sum / norm : 0;
    }

    public float fbm3(float x, float y, float z, int octaves) {
        float sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * perlin3(x * freq, y * freq, z * freq);
            norm += amp; amp *= 0.5f; freq *= 2f;
        }
        return norm > 0 ? sum / norm : 0;
    }
}
