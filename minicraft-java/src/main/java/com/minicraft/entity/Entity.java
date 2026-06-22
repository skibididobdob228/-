package com.minicraft.entity;

import com.minicraft.player.Player;
import com.minicraft.world.Blocks;
import com.minicraft.world.World;
import org.joml.Vector3f;

/** Base mob: AABB physics against the voxel world, health and facing. */
public abstract class Entity {
    public final Vector3f position = new Vector3f();   // feet centre
    public final Vector3f velocity = new Vector3f();
    public float yaw;
    public boolean onGround;
    public float health, maxHealth;
    public boolean dead;
    public float hurtFlash;     // brief red tint after taking damage
    public float width, height;

    protected Entity(float width, float height, float hp) {
        this.width = width; this.height = height; this.maxHealth = hp; this.health = hp;
    }

    public abstract void update(World w, Player p, float dt, boolean night);
    /** Provide the box parts (relative to feet, before yaw) for rendering. */
    public abstract Box[] parts();
    /** Items dropped on death (item ids). */
    public int[] drops() { return new int[0]; }

    public static final class Box {
        public float cx, cy, cz, sx, sy, sz, r, g, b;
        public Box(float cx, float cy, float cz, float sx, float sy, float sz, float r, float g, float b) {
            this.cx = cx; this.cy = cy; this.cz = cz; this.sx = sx; this.sy = sy; this.sz = sz;
            this.r = r; this.g = g; this.b = b;
        }
    }

    public void hurt(float dmg, Vector3f knockFrom) {
        health -= dmg;
        hurtFlash = 0.3f;
        if (knockFrom != null) {
            Vector3f k = new Vector3f(position).sub(knockFrom); k.y = 0;
            if (k.lengthSquared() > 1e-4f) k.normalize().mul(6f);
            velocity.x += k.x; velocity.z += k.z; velocity.y = 5f;
        }
        if (health <= 0) dead = true;
    }

    protected boolean collides(World w, float px, float py, float pz) {
        float hx = width / 2f, hz = width / 2f;
        int x0 = (int) Math.floor(px - hx), x1 = (int) Math.floor(px + hx - 1e-4f);
        int y0 = (int) Math.floor(py), y1 = (int) Math.floor(py + height - 1e-4f);
        int z0 = (int) Math.floor(pz - hz), z1 = (int) Math.floor(pz + hz - 1e-4f);
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++)
                    if (Blocks.isSolid(w.getBlock(x, y, z))) return true;
        return false;
    }

    /** Apply gravity + per-axis movement. Returns true if it hit a wall horizontally. */
    protected boolean physics(World w, float dt) {
        velocity.y -= 26f * dt;
        if (velocity.y < -55f) velocity.y = -55f;
        boolean blockedH = false;

        position.x += velocity.x * dt;
        if (collides(w, position.x, position.y, position.z)) { position.x -= velocity.x * dt; velocity.x = 0; blockedH = true; }
        position.z += velocity.z * dt;
        if (collides(w, position.x, position.y, position.z)) { position.z -= velocity.z * dt; velocity.z = 0; blockedH = true; }
        position.y += velocity.y * dt;
        if (collides(w, position.x, position.y, position.z)) {
            position.y -= velocity.y * dt;
            if (velocity.y < 0) onGround = true;
            velocity.y = 0;
        } else onGround = false;

        if (hurtFlash > 0) hurtFlash -= dt;
        return blockedH;
    }

    public float eyeY() { return height * 0.9f; }
}
