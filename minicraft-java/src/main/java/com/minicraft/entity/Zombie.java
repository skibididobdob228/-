package com.minicraft.entity;

import com.minicraft.player.Player;
import com.minicraft.world.World;
import org.joml.Vector3f;

import java.util.Random;

/** Hostile mob: chases the player at night and attacks on contact. */
public class Zombie extends Entity {
    private static final Random RNG = new Random();
    private float attackCd;
    private float wanderTimer, dirX, dirZ;
    public boolean attacked; // set when it lands a hit (so Game can apply damage)

    public Zombie() { super(0.6f, 1.95f, 20f); }

    @Override
    public void update(World w, Player p, float dt, boolean night) {
        attacked = false;
        if (attackCd > 0) attackCd -= dt;

        Vector3f toPlayer = new Vector3f(p.position).sub(position);
        float dist = toPlayer.length();
        boolean chase = night && dist < 24f;

        if (chase) {
            toPlayer.y = 0;
            if (toPlayer.lengthSquared() > 1e-4f) toPlayer.normalize();
            velocity.x = toPlayer.x * 2.6f;
            velocity.z = toPlayer.z * 2.6f;
            yaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x));
            if (dist < 1.5f && attackCd <= 0) { attacked = true; attackCd = 1.0f; }
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderTimer = 2f + RNG.nextFloat() * 3f;
                double a = RNG.nextDouble() * Math.PI * 2;
                dirX = RNG.nextFloat() < 0.5f ? 0 : (float) Math.cos(a);
                dirZ = dirX == 0 ? 0 : (float) Math.sin(a);
            }
            velocity.x = dirX * 1.2f;
            velocity.z = dirZ * 1.2f;
        }
        boolean blocked = physics(w, dt);
        if (blocked && onGround) velocity.y = 7f;
    }

    @Override
    public Box[] parts() {
        float f = hurtFlash > 0 ? 0.5f : 0f;
        return new Box[]{
            new Box(-0.15f, 0.35f, 0, 0.25f, 0.7f, 0.25f, 0.25f + f, 0.25f, 0.45f), // legs
            new Box(0.15f, 0.35f, 0, 0.25f, 0.7f, 0.25f, 0.25f + f, 0.25f, 0.45f),
            new Box(0, 1.05f, 0, 0.55f, 0.7f, 0.3f, 0.2f + f, 0.45f, 0.45f),         // body
            new Box(-0.4f, 1.05f, 0, 0.22f, 0.65f, 0.22f, 0.3f + f, 0.55f, 0.45f),   // arms
            new Box(0.4f, 1.05f, 0, 0.22f, 0.65f, 0.22f, 0.3f + f, 0.55f, 0.45f),
            new Box(0, 1.68f, 0, 0.5f, 0.5f, 0.5f, 0.35f + f, 0.6f, 0.35f),          // head
        };
    }
}
