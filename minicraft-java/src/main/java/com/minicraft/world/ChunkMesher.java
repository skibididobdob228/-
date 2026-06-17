package com.minicraft.world;

import com.minicraft.render.Tiles;

import static com.minicraft.world.Chunk.*;

/** Builds opaque + transparent meshes for a chunk (CPU-only; thread-safe). */
public final class ChunkMesher {

    public static final class Neighbors {
        public Chunk west, east, north, south;
    }

    // 6 faces: normal + base corner + in-plane du/dv unit steps.
    private static final int[][] FACE = {
        {  1, 0, 0, 1,0,0, 0,0,1, 0,1,0 },  // +X
        { -1, 0, 0, 0,0,1, 0,0,-1,0,1,0 },  // -X
        {  0, 1, 0, 0,1,1, 1,0,0, 0,0,-1},  // +Y
        {  0,-1, 0, 0,0,0, 1,0,0, 0,0,1 },  // -Y
        {  0, 0, 1, 1,0,1, -1,0,0,0,1,0 },  // +Z
        {  0, 0,-1, 0,0,0, 1,0,0, 0,1,0 },  // -Z
    };
    private static final float[] FACE_SHADE = {0.78f, 0.78f, 1.0f, 0.55f, 0.65f, 0.65f};
    private static final float[] AO_LUT = {0.45f, 0.62f, 0.80f, 1.0f};

    private final Chunk c;
    private final Neighbors nb;

    private ChunkMesher(Chunk c, Neighbors nb) { this.c = c; this.nb = nb; }

    public static void build(Chunk c, Neighbors nb) { new ChunkMesher(c, nb).run(); }

    private int blockAt(int x, int y, int z) {
        if (y < 0 || y >= SY) return Blocks.AIR;
        boolean xo = x < 0 || x >= SX, zo = z < 0 || z >= SZ;
        if (xo && zo) return Blocks.AIR;
        if (!xo && !zo) return c.get(x, y, z);
        if (xo) {
            Chunk n = x < 0 ? nb.west : nb.east;
            return n == null ? Blocks.AIR : n.get((x + SX) % SX, y, z);
        }
        Chunk n = z < 0 ? nb.north : nb.south;
        return n == null ? Blocks.AIR : n.get(x, y, (z + SZ) % SZ);
    }

    private boolean opaqueAt(int x, int y, int z) { return Blocks.isOpaque(blockAt(x, y, z)); }

    private int[] lightAt(int x, int y, int z) {
        if (y < 0) return new int[]{0, 0};
        if (y >= SY) return new int[]{15, 0};
        boolean xo = x < 0 || x >= SX, zo = z < 0 || z >= SZ;
        if (xo && zo) return new int[]{15, 0};
        Chunk n = c; int lx = x, lz = z;
        if (xo) { n = x < 0 ? nb.west : nb.east; lx = (x + SX) % SX; }
        else if (zo) { n = z < 0 ? nb.north : nb.south; lz = (z + SZ) % SZ; }
        if (n == null) return new int[]{15, 0};
        return new int[]{n.sky(lx, y, lz), n.block(lx, y, lz)};
    }

    private static boolean faceVisible(int self, int neighbour) {
        if (neighbour == Blocks.AIR) return true;
        if (Blocks.isOpaque(neighbour)) return false;
        return neighbour != self;
    }

    private static float aoLevel(boolean s1, boolean s2, boolean corner) {
        if (s1 && s2) return 0;
        int n = (s1 ? 1 : 0) + (s2 ? 1 : 0) + (corner ? 1 : 0);
        return 3 - n;
    }

    private static void atlasUV(int tile, float s, float t, float[] out) {
        int col = tile % Tiles.ATLAS_TILES, row = tile / Tiles.ATLAS_TILES;
        float inv = 1f / Tiles.ATLAS_PX;
        float inset = 0.5f * inv;
        float ts = Tiles.TILE_PX * inv;
        out[0] = col * ts + inset + s * (ts - 2 * inset);
        out[1] = row * ts + inset + t * (ts - 2 * inset);
    }

    private void run() {
        FloatBuf opaque = new FloatBuf(4096);
        FloatBuf transp = new FloatBuf(1024);
        float[] uv = new float[2], uv0 = new float[2], uv1 = new float[2];

        for (int y = 0; y < SY; y++)
            for (int z = 0; z < SZ; z++)
                for (int x = 0; x < SX; x++) {
                    int id = c.get(x, y, z);
                    if (id == Blocks.AIR) continue;
                    Blocks.Def def = Blocks.get(id);
                    boolean toTransp = def.render != Blocks.Render.SOLID;
                    FloatBuf out = toTransp ? transp : opaque;

                    if (def.render == Blocks.Render.CROSS) {
                        int[] l = lightAt(x, y, z);
                        float fsky = l[0] / 15f, fblk = l[1] / 15f;
                        atlasUV(def.texSide, 0, 1, uv0);
                        atlasUV(def.texSide, 1, 0, uv1);
                        float p = 0.146f;
                        emitCross(out, x + p, z + p, x + 1 - p, z + 1 - p, y, uv0, uv1, fsky, fblk);
                        emitCross(out, x + 1 - p, z + p, x + p, z + 1 - p, y, uv0, uv1, fsky, fblk);
                        continue;
                    }

                    for (int f = 0; f < 6; f++) {
                        int[] face = unpack(f);
                        int nbBlock = blockAt(x + face[0], y + face[1], z + face[2]);
                        if (!faceVisible(id, nbBlock)) continue;

                        int tile = f == 2 ? def.texTop : f == 3 ? def.texBottom : def.texSide;
                        float shade = FACE_SHADE[f];
                        boolean waterTop = def.render == Blocks.Render.LIQUID && f == 2;

                        float[] vx = new float[4 * 11];
                        float[] ao = new float[4];
                        for (int corner = 0; corner < 4; corner++) {
                            int cu = (corner == 1 || corner == 2) ? 1 : 0;
                            int cv = corner >= 2 ? 1 : 0;
                            float px = x + face[3] + cu * face[6] + cv * face[9];
                            float py = y + face[4] + cu * face[7] + cv * face[10];
                            float pz = z + face[5] + cu * face[8] + cv * face[11];
                            if (waterTop) py -= 0.12f;

                            int suX = cu != 0 ? face[6] : -face[6];
                            int suY = cu != 0 ? face[7] : -face[7];
                            int suZ = cu != 0 ? face[8] : -face[8];
                            int svX = cv != 0 ? face[9] : -face[9];
                            int svY = cv != 0 ? face[10] : -face[10];
                            int svZ = cv != 0 ? face[11] : -face[11];
                            int bx = x + face[0], by = y + face[1], bz = z + face[2];
                            boolean s1 = opaqueAt(bx + suX, by + suY, bz + suZ);
                            boolean s2 = opaqueAt(bx + svX, by + svY, bz + svZ);
                            boolean cc = opaqueAt(bx + suX + svX, by + suY + svY, bz + suZ + svZ);
                            ao[corner] = aoLevel(s1, s2, cc);

                            int accSky = 0, accBlk = 0, cnt = 0;
                            int[][] samples = {{0,0,0},{suX,suY,suZ},{svX,svY,svZ},{suX+svX,suY+svY,suZ+svZ}};
                            for (int[] s : samples) {
                                if (opaqueAt(bx + s[0], by + s[1], bz + s[2])) continue;
                                int[] ll = lightAt(bx + s[0], by + s[1], bz + s[2]);
                                accSky += ll[0]; accBlk += ll[1]; cnt++;
                            }
                            if (cnt == 0) { int[] ll = lightAt(bx, by, bz); accSky = ll[0]; accBlk = ll[1]; cnt = 1; }
                            float fsky = (accSky / (float) cnt) / 15f;
                            float fblk = (accBlk / (float) cnt) / 15f;

                            float sCoord = cu, tCoord = cv != 0 ? 0f : 1f;
                            atlasUV(tile, sCoord, tCoord, uv);
                            int o = corner * 11;
                            vx[o] = px; vx[o+1] = py; vx[o+2] = pz; vx[o+3] = uv[0]; vx[o+4] = uv[1];
                            vx[o+5] = face[0]; vx[o+6] = face[1]; vx[o+7] = face[2];
                            vx[o+8] = AO_LUT[(int) ao[corner]] * shade; vx[o+9] = fsky; vx[o+10] = fblk;
                        }

                        boolean flip = (ao[0] + ao[2]) < (ao[1] + ao[3]);
                        if (flip) emitTri(out, vx, 1, 2, 3, 1, 3, 0);
                        else emitTri(out, vx, 0, 1, 2, 0, 2, 3);
                    }
                }

        c.meshOpaque = opaque.toArray();
        c.meshTransparent = transp.toArray();
    }

    private int[] unpack(int f) { return FACE[f]; }

    private static void emitTri(FloatBuf out, float[] vx, int... order) {
        for (int idx : order) {
            int o = idx * 11;
            out.vertex(vx[o], vx[o+1], vx[o+2], vx[o+3], vx[o+4], vx[o+5], vx[o+6], vx[o+7], vx[o+8], vx[o+9], vx[o+10]);
        }
    }

    private static void emitCross(FloatBuf out, float ax, float az, float bx, float bz, int y,
                                  float[] uv0, float[] uv1, float fsky, float fblk) {
        float top = y + 1f;
        // a..b at bottom, up to top.
        out.vertex(ax, y, az, uv0[0], uv1[1], 0,1,0, 1f, fsky, fblk);
        out.vertex(bx, y, bz, uv1[0], uv1[1], 0,1,0, 1f, fsky, fblk);
        out.vertex(bx, top, bz, uv1[0], uv0[1], 0,1,0, 1f, fsky, fblk);
        out.vertex(ax, y, az, uv0[0], uv1[1], 0,1,0, 1f, fsky, fblk);
        out.vertex(bx, top, bz, uv1[0], uv0[1], 0,1,0, 1f, fsky, fblk);
        out.vertex(ax, top, az, uv0[0], uv0[1], 0,1,0, 1f, fsky, fblk);
    }
}
