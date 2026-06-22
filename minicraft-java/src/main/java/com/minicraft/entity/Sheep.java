package com.minicraft.entity;

import com.minicraft.player.Player;
import com.minicraft.world.Blocks;
import com.minicraft.world.World;

import java.util.Random;

/** Passive mob: wanders randomly, drops wool. */
public class Sheep extends Entity {
    private static final Random RNG = new Random();
    private float wanderTimer;
    private float dirX, dirZ;

    public Sheep() { super(0.9f, 1.3f, 8f); }

    @Override
    public void update(World w, Player p, float dt, boolean night) {
        wanderTimer -= dt;
        if (wanderTimer <= 0) {
            wanderTimer = 2f + RNG.nextFloat() * 3f;
            if (RNG.nextFloat() < 0.4f) { dirX = 0; dirZ = 0; }       // pause
            else {
                double a = RNG.nextDouble() * Math.PI * 2;
                dirX = (float) Math.cos(a); dirZ = (float) Math.sin(a);
                yaw = (float) Math.toDegrees(a);
            }
        }
        float speed = 1.8f;
        velocity.x = dirX * speed;
        velocity.z = dirZ * speed;
        boolean blocked = physics(w, dt);
        if (blocked && onGround) velocity.y = 7f; // hop over obstacles
    }

    @Override
    public Box[] parts() {
        float wool = hurtFlash > 0 ? 0.6f : 0.0f;
        float r = 0.92f + wool, g = 0.92f - wool * 0.3f, b = 0.88f - wool * 0.3f;
        return new Box[]{
            new Box(0, 0.75f, 0, 0.9f, 0.8f, 1.25f, r, g, b),          // wool body
            new Box(0, 0.78f, 0.78f, 0.5f, 0.5f, 0.45f, 0.85f, 0.78f, 0.70f), // head
            new Box(-0.28f, 0.22f, 0.42f, 0.18f, 0.45f, 0.18f, 0.3f, 0.3f, 0.3f),
            new Box(0.28f, 0.22f, 0.42f, 0.18f, 0.45f, 0.18f, 0.3f, 0.3f, 0.3f),
            new Box(-0.28f, 0.22f, -0.42f, 0.18f, 0.45f, 0.18f, 0.3f, 0.3f, 0.3f),
            new Box(0.28f, 0.22f, -0.42f, 0.18f, 0.45f, 0.18f, 0.3f, 0.3f, 0.3f),
        };
    }

    @Override
    public int[] drops() { return new int[]{Blocks.WOOL_WHITE}; }
}
