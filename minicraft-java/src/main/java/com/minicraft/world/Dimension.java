package com.minicraft.world;

import org.joml.Vector3f;

/** The three dimensions, each with its own look and lighting rules. */
public enum Dimension {
    OVERWORLD(true,  0,  new Vector3f(0.47f, 0.66f, 0.98f), 0.06f),
    NETHER   (false, 1,  new Vector3f(0.22f, 0.05f, 0.05f), 0.20f),
    END      (false, 2,  new Vector3f(0.03f, 0.02f, 0.06f), 0.32f);

    public final boolean hasSky;       // true -> day/night sky-light cycle
    public final int seedOffset;       // makes each dimension's noise distinct
    public final Vector3f baseFog;     // fog/sky colour
    public final float ambient;        // minimum light level (glow of the dimension)

    Dimension(boolean hasSky, int seedOffset, Vector3f baseFog, float ambient) {
        this.hasSky = hasSky;
        this.seedOffset = seedOffset;
        this.baseFog = baseFog;
        this.ambient = ambient;
    }
}
