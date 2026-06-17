package com.minicraft.world;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One column of voxels plus its sky/block light and GPU mesh handles. */
public class Chunk {
    public static final int SX = 16, SZ = 16, SY = 128;
    public static final int SEA_LEVEL = 48;
    public static final int VOLUME = SX * SY * SZ;

    public enum State { EMPTY, GENERATING, GENERATED, MESHING, READY }

    public final int cx, cz;
    public final byte[] blocks = new byte[VOLUME];
    public final byte[] skyLight = new byte[VOLUME];
    public final byte[] blockLight = new byte[VOLUME];

    public final AtomicReference<State> state = new AtomicReference<>(State.EMPTY);
    public final AtomicBoolean dirty = new AtomicBoolean(false);
    public final AtomicBoolean meshReady = new AtomicBoolean(false);

    // CPU mesh produced by a worker, consumed by the main thread.
    public volatile float[] meshOpaque, meshTransparent;

    // GPU handles (main thread only).
    public int vaoOpaque, vboOpaque, vaoTransp, vboTransp;
    public int opaqueCount, transpCount;
    public boolean uploaded;
    public boolean firstMeshed;

    public Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public static int idx(int x, int y, int z) { return (y * SZ + z) * SX + x; }
    public static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ;
    }

    public int get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return Blocks.AIR;
        return blocks[idx(x, y, z)] & 0xFF;
    }
    public void set(int x, int y, int z, int id) {
        if (inBounds(x, y, z)) blocks[idx(x, y, z)] = (byte) id;
    }
    public int sky(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 15;
        return skyLight[idx(x, y, z)] & 0xFF;
    }
    public int block(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return blockLight[idx(x, y, z)] & 0xFF;
    }

    /** Sky + block light flood fill using only this chunk's voxels. */
    public void computeLight(boolean hasSky) {
        java.util.Arrays.fill(skyLight, (byte) 0);
        java.util.Arrays.fill(blockLight, (byte) 0);
        ArrayDeque<Integer> q = new ArrayDeque<>();

        if (hasSky) {
            for (int z = 0; z < SZ; z++)
                for (int x = 0; x < SX; x++) {
                    int light = 15;
                    for (int y = SY - 1; y >= 0; y--) {
                        int b = blocks[idx(x, y, z)] & 0xFF;
                        if (Blocks.isOpaque(b)) light = 0;
                        else if (b != Blocks.AIR && light > 2) light -= 2;
                        skyLight[idx(x, y, z)] = (byte) light;
                        if (light == 15) q.add(idx(x, y, z));
                    }
                }
            spread(skyLight, q);
        }

        q.clear();
        for (int i = 0; i < VOLUME; i++) {
            int e = Blocks.get(blocks[i] & 0xFF).light;
            if (e > 0) { blockLight[i] = (byte) e; q.add(i); }
        }
        spread(blockLight, q);
    }

    private static final int[][] DIRS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    private void spread(byte[] light, ArrayDeque<Integer> q) {
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % SX, z = (i / SX) % SZ, y = i / (SX * SZ);
            int cur = light[i] & 0xFF;
            if (cur <= 1) continue;
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!inBounds(nx, ny, nz)) continue;
                int ni = idx(nx, ny, nz);
                if (Blocks.isOpaque(blocks[ni] & 0xFF)) continue;
                int want = cur - 1;
                if ((light[ni] & 0xFF) < want) { light[ni] = (byte) want; q.add(ni); }
            }
        }
    }
}
