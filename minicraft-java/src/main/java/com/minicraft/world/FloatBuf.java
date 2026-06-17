package com.minicraft.world;

import java.util.Arrays;

/** Minimal growable primitive-float buffer for building chunk meshes. */
public final class FloatBuf {
    private float[] data;
    private int size;

    public FloatBuf(int cap) { data = new float[Math.max(16, cap)]; }

    public void add(float v) {
        if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
        data[size++] = v;
    }

    public void vertex(float x, float y, float z, float u, float v,
                       float nx, float ny, float nz, float ao, float sky, float block) {
        add(x); add(y); add(z); add(u); add(v); add(nx); add(ny); add(nz); add(ao); add(sky); add(block);
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public float[] toArray() { return Arrays.copyOf(data, size); }
}
