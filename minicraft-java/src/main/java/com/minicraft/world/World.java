package com.minicraft.world;

import com.minicraft.render.Camera;
import com.minicraft.render.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.minicraft.world.Chunk.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Holds and streams the chunks of a single dimension. */
public class World {
    public static final class Hit {
        public boolean hit;
        public int bx, by, bz;     // block struck
        public int px, py, pz;     // empty cell before it (for placement)
        public int nx, ny, nz;     // face normal
    }

    private final long seed;
    private final Dimension dim;
    private final TerrainGenerator gen;
    private final ExecutorService pool;

    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final Map<Long, byte[]> editedStore = new HashMap<>();

    private int lastDrawn;

    public World(long seed, Dimension dim, ExecutorService pool) {
        this.seed = seed;
        this.dim = dim;
        this.gen = new TerrainGenerator(seed, dim);
        this.pool = pool;
    }

    public Dimension dimension() { return dim; }
    public int lastDrawn() { return lastDrawn; }
    public int loadedChunks() { return chunks.size(); }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }
    private static int floorDiv(int a, int b) { return Math.floorDiv(a, b); }
    private static int floorMod(int a, int b) { return Math.floorMod(a, b); }

    private Chunk chunk(int cx, int cz) { return chunks.get(key(cx, cz)); }

    public int spawnHeight(int wx, int wz) { return gen.spawnHeight(wx, wz); }

    public int getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= SY) return Blocks.AIR;
        Chunk c = chunk(floorDiv(wx, SX), floorDiv(wz, SZ));
        if (c == null || c.state.get().ordinal() < State.GENERATED.ordinal()) return Blocks.AIR;
        return c.get(floorMod(wx, SX), wy, floorMod(wz, SZ));
    }

    public boolean setBlock(int wx, int wy, int wz, int id) {
        if (wy < 0 || wy >= SY) return false;
        int cx = floorDiv(wx, SX), cz = floorDiv(wz, SZ);
        Chunk c = chunk(cx, cz);
        if (c == null || c.state.get().ordinal() < State.GENERATED.ordinal()) return false;
        int lx = floorMod(wx, SX), lz = floorMod(wz, SZ);
        c.set(lx, wy, lz, id);
        editedStore.put(key(cx, cz), c.blocks.clone());
        c.dirty.set(true);
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == SX - 1) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == SZ - 1) markDirty(cx, cz + 1);
        return true;
    }

    private void markDirty(int cx, int cz) {
        Chunk c = chunk(cx, cz);
        if (c != null) c.dirty.set(true);
    }

    public Hit raycast(Vector3f origin, Vector3f dir, float maxDist) {
        Hit r = new Hit();
        Vector3f d = new Vector3f(dir).normalize();
        int px = (int) Math.floor(origin.x), py = (int) Math.floor(origin.y), pz = (int) Math.floor(origin.z);
        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        float tMaxX = boundary(origin.x, d.x, sx), tMaxY = boundary(origin.y, d.y, sy), tMaxZ = boundary(origin.z, d.z, sz);
        float tDeltaX = d.x == 0 ? 1e30f : Math.abs(1 / d.x);
        float tDeltaY = d.y == 0 ? 1e30f : Math.abs(1 / d.y);
        float tDeltaZ = d.z == 0 ? 1e30f : Math.abs(1 / d.z);
        int nX = 0, nY = 0, nZ = 0;
        float dist = 0;
        while (dist <= maxDist) {
            int b = getBlock(px, py, pz);
            if (b != Blocks.AIR && Blocks.get(b).render != Blocks.Render.LIQUID && b != Blocks.PORTAL) {
                r.hit = true; r.bx = px; r.by = py; r.bz = pz;
                r.nx = nX; r.ny = nY; r.nz = nZ;
                r.px = px + nX; r.py = py + nY; r.pz = pz + nZ;
                return r;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { px += sx; dist = tMaxX; tMaxX += tDeltaX; nX = -sx; nY = 0; nZ = 0; }
            else if (tMaxY < tMaxZ) { py += sy; dist = tMaxY; tMaxY += tDeltaY; nX = 0; nY = -sy; nZ = 0; }
            else { pz += sz; dist = tMaxZ; tMaxZ += tDeltaZ; nX = 0; nY = 0; nZ = -sz; }
        }
        return r;
    }

    private static float boundary(float origin, float d, int step) {
        if (d == 0) return 1e30f;
        int p = (int) Math.floor(origin);
        float vb = p + (step > 0 ? 1 : 0);
        return (vb - origin) / d;
    }

    public void update(float centerX, float centerZ, int renderDistance) {
        int pcx = floorDiv((int) Math.floor(centerX), SX);
        int pcz = floorDiv((int) Math.floor(centerZ), SZ);

        // 1) Create + start generation for nearby chunks.
        int genBudget = 4;
        outer:
        for (int dz = -renderDistance; dz <= renderDistance; dz++)
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                if (dx * dx + dz * dz > (renderDistance + 1) * (renderDistance + 1)) continue;
                int cx = pcx + dx, cz = pcz + dz;
                Chunk c = chunks.computeIfAbsent(key(cx, cz), k -> new Chunk(cx, cz));
                if (c.state.get() == State.EMPTY && genBudget > 0) {
                    c.state.set(State.GENERATING);
                    long k = key(cx, cz);
                    pool.submit(() -> {
                        byte[] edited = editedStore.get(k);
                        if (edited != null) System.arraycopy(edited, 0, c.blocks, 0, VOLUME);
                        else gen.generate(c);
                        c.state.set(State.GENERATED);
                        c.dirty.set(true);
                    });
                    if (--genBudget <= 0) break outer;
                }
            }

        // 2) Queue meshing for generated/dirty chunks.
        int meshBudget = 6;
        for (Chunk c : chunks.values()) {
            if (meshBudget <= 0) break;
            State s = c.state.get();
            if ((s == State.GENERATED || s == State.READY) && c.dirty.get()) {
                c.dirty.set(false);
                c.state.set(State.MESHING);
                ChunkMesher.Neighbors nb = new ChunkMesher.Neighbors();
                nb.west = chunk(c.cx - 1, c.cz); nb.east = chunk(c.cx + 1, c.cz);
                nb.north = chunk(c.cx, c.cz - 1); nb.south = chunk(c.cx, c.cz + 1);
                pool.submit(() -> {
                    c.computeLight(dim.hasSky);
                    ChunkMesher.build(c, nb);
                    c.meshReady.set(true);
                    c.state.set(State.READY);
                });
                meshBudget--;
            }
        }

        // 3) Upload finished meshes (bounded).
        int uploadBudget = 4;
        for (Chunk c : chunks.values()) {
            if (uploadBudget <= 0) break;
            if (c.meshReady.get() && c.state.get() == State.READY) {
                uploadMesh(c);
                c.meshReady.set(false);
                uploadBudget--;
                if (!c.firstMeshed) {
                    c.firstMeshed = true;
                    markDirty(c.cx - 1, c.cz); markDirty(c.cx + 1, c.cz);
                    markDirty(c.cx, c.cz - 1); markDirty(c.cx, c.cz + 1);
                }
            }
        }

        // 4) Unload far chunks (keep edited data).
        int unloadDist = renderDistance + 3;
        List<Long> remove = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (Math.abs(c.cx - pcx) > unloadDist || Math.abs(c.cz - pcz) > unloadDist) {
                State s = c.state.get();
                if (s == State.GENERATING || s == State.MESHING || c.meshReady.get()) continue;
                remove.add(key(c.cx, c.cz));
            }
        }
        for (long k : remove) {
            Chunk c = chunks.remove(k);
            if (c != null) deleteGpu(c);
        }
    }

    private void uploadMesh(Chunk c) {
        c.opaqueCount = uploadBuffer(c.meshOpaque, c, true);
        c.transpCount = uploadBuffer(c.meshTransparent, c, false);
        c.uploaded = true;
        c.meshOpaque = null;
        c.meshTransparent = null;
    }

    private int uploadBuffer(float[] data, Chunk c, boolean opaque) {
        int vao = opaque ? c.vaoOpaque : c.vaoTransp;
        int vbo = opaque ? c.vboOpaque : c.vboTransp;
        if (vao == 0) { vao = glGenVertexArrays(); if (opaque) c.vaoOpaque = vao; else c.vaoTransp = vao; }
        if (vbo == 0) { vbo = glGenBuffers(); if (opaque) c.vboOpaque = vbo; else c.vboTransp = vbo; }
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(Math.max(1, data.length));
        if (data.length > 0) fb.put(data);
        fb.flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        int stride = 11 * Float.BYTES;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(3); glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glBindVertexArray(0);
        return data.length / 11;
    }

    private void deleteGpu(Chunk c) {
        if (c.vaoOpaque != 0) glDeleteVertexArrays(c.vaoOpaque);
        if (c.vboOpaque != 0) glDeleteBuffers(c.vboOpaque);
        if (c.vaoTransp != 0) glDeleteVertexArrays(c.vaoTransp);
        if (c.vboTransp != 0) glDeleteBuffers(c.vboTransp);
    }

    public void renderOpaque(Shader shader, Camera cam) {
        cam.updateFrustum();
        lastDrawn = 0;
        for (Chunk c : chunks.values()) {
            if (!c.uploaded || c.opaqueCount == 0) continue;
            float ox = c.cx * SX, oz = c.cz * SZ;
            if (!cam.aabbVisible(ox, 0, oz, ox + SX, SY, oz + SZ)) continue;
            shader.set("uModel", new Matrix4f().translate(ox, 0, oz));
            glBindVertexArray(c.vaoOpaque);
            glDrawArrays(GL_TRIANGLES, 0, c.opaqueCount);
            lastDrawn++;
        }
        glBindVertexArray(0);
    }

    public void renderTransparent(Shader shader, Camera cam) {
        List<Chunk> list = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (!c.uploaded || c.transpCount == 0) continue;
            float ox = c.cx * SX, oz = c.cz * SZ;
            if (!cam.aabbVisible(ox, 0, oz, ox + SX, SY, oz + SZ)) continue;
            list.add(c);
        }
        list.sort((a, b) -> Float.compare(distSq(b, cam), distSq(a, cam)));
        for (Chunk c : list) {
            shader.set("uModel", new Matrix4f().translate(c.cx * SX, 0, c.cz * SZ));
            glBindVertexArray(c.vaoTransp);
            glDrawArrays(GL_TRIANGLES, 0, c.transpCount);
        }
        glBindVertexArray(0);
    }

    private static float distSq(Chunk c, Camera cam) {
        float dx = c.cx * SX + SX / 2f - cam.position.x;
        float dz = c.cz * SZ + SZ / 2f - cam.position.z;
        return dx * dx + dz * dz;
    }

    public void writeEdits(DataOutputStream out) throws IOException {
        out.writeInt(editedStore.size());
        for (Map.Entry<Long, byte[]> e : editedStore.entrySet()) {
            out.writeLong(e.getKey());
            byte[] b = e.getValue();
            out.writeInt(b.length);
            // RLE
            int i = 0;
            while (i < b.length) {
                byte id = b[i];
                int run = 1;
                while (i + run < b.length && b[i + run] == id && run < 65535) run++;
                out.writeByte(id);
                out.writeShort(run);
                i += run;
            }
        }
    }

    public void readEdits(DataInputStream in) throws IOException {
        int n = in.readInt();
        editedStore.clear();
        for (int e = 0; e < n; e++) {
            long k = in.readLong();
            int len = in.readInt();
            byte[] b = new byte[len];
            int i = 0;
            while (i < len) {
                byte id = in.readByte();
                int run = in.readShort() & 0xFFFF;
                for (int r = 0; r < run && i < len; r++) b[i++] = id;
            }
            editedStore.put(k, b);
        }
    }

    public void shutdownGpu() {
        for (Chunk c : chunks.values()) deleteGpu(c);
    }
}
