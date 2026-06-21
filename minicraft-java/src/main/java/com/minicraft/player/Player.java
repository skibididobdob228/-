package com.minicraft.player;

import com.minicraft.render.Camera;
import com.minicraft.world.Blocks;
import com.minicraft.world.Inventory;
import com.minicraft.world.ItemStack;
import com.minicraft.world.Items;
import com.minicraft.world.World;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Player physics, collision, stats, and game modes. */
public class Player {
    public enum Mode { SURVIVAL, CREATIVE, SPECTATOR }

    public final Vector3f position = new Vector3f(0, 80, 0);
    public final Vector3f velocity = new Vector3f();
    public boolean onGround, flying, inWater, inLava;
    public Mode mode = Mode.CREATIVE;

    public float health = 20, maxHealth = 20, hunger = 20;
    public boolean dead;
    public float mouseSensitivity = 0.12f;

    public final Inventory inventory = new Inventory();

    private static final float GRAVITY = 28f, JUMP = 8.6f, WALK = 4.6f, SPRINT = 6.2f, FLY = 12f;

    private float fallStartY;
    private boolean wasOnGround = true;
    private double lastJumpTime = -10;
    private float regenTimer;

    /** The block id the selected hotbar item would place, or AIR if not placeable. */
    public int selectedBlock() {
        ItemStack s = inventory.selectedStack();
        if (s.isEmpty()) return Blocks.AIR;
        return Items.isBlock(s.item) ? Items.blockId(s.item) : Blocks.AIR;
    }

    public void scrollHotbar(int dir) { inventory.scroll(dir); }

    public void toggleMode() {
        mode = switch (mode) {
            case SURVIVAL -> Mode.CREATIVE;
            case CREATIVE -> Mode.SPECTATOR;
            case SPECTATOR -> Mode.SURVIVAL;
        };
        if (mode == Mode.SPECTATOR) flying = true;
        if (mode == Mode.SURVIVAL) flying = false;
    }

    public float halfX() { return 0.3f; }
    public float halfZ() { return 0.3f; }
    public float height() { return 1.8f; }
    public float eyeHeight() { return 1.62f; }

    private boolean collides(World w, Vector3f p) {
        if (mode == Mode.SPECTATOR) return false;
        float hx = halfX(), hz = halfZ();
        int x0 = (int) Math.floor(p.x - hx), x1 = (int) Math.floor(p.x + hx - 1e-4f);
        int y0 = (int) Math.floor(p.y), y1 = (int) Math.floor(p.y + height() - 1e-4f);
        int z0 = (int) Math.floor(p.z - hz), z1 = (int) Math.floor(p.z + hz - 1e-4f);
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++)
                    if (Blocks.isSolid(w.getBlock(x, y, z))) return true;
        return false;
    }

    private void resolve(World w, float dt) {
        float dx = velocity.x * dt, dy = velocity.y * dt, dz = velocity.z * dt;
        position.x += dx;
        if (collides(w, position)) { position.x -= dx; velocity.x = 0; }
        position.z += dz;
        if (collides(w, position)) { position.z -= dz; velocity.z = 0; }
        position.y += dy;
        if (collides(w, position)) {
            position.y -= dy;
            if (velocity.y < 0) onGround = true;
            velocity.y = 0;
        } else onGround = false;
    }

    public void update(World w, Camera cam, InputState in, float dt) {
        if (in.jumpPressed && mode == Mode.CREATIVE) {
            double now = GLFW.glfwGetTime();
            if (now - lastJumpTime < 0.30) flying = !flying;
            lastJumpTime = now;
        }
        if (!dead) {
            move(w, cam, in, dt);
            updateStats(w, dt);
        }
        cam.position.set(position.x, position.y + eyeHeight(), position.z);
    }

    private void move(World w, Camera cam, InputState in, float dt) {
        int fx = (int) Math.floor(position.x), fz = (int) Math.floor(position.z);
        int fy = (int) Math.floor(position.y + 0.5f);
        inWater = w.getBlock(fx, fy, fz) == Blocks.WATER;
        inLava = w.getBlock(fx, fy, fz) == Blocks.LAVA;

        Vector3f fwd = cam.forwardXZ();
        Vector3f rightDir = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f wish = new Vector3f();
        if (in.forward) wish.add(fwd);
        if (in.back) wish.sub(fwd);
        if (in.right) wish.add(rightDir);
        if (in.left) wish.sub(rightDir);
        if (wish.lengthSquared() > 1e-6f) wish.normalize();

        float speed = in.sprint ? SPRINT : WALK;
        if (flying) {
            speed = FLY * (in.sprint ? 1.8f : 1f);
            velocity.set(wish.x * speed, 0, wish.z * speed);
            if (in.jump) velocity.y += speed;
            if (in.sneak) velocity.y -= speed;
            resolve(w, dt);
            return;
        }

        float fluid = (inWater || inLava) ? 0.5f : 1f;
        float targetX = wish.x * speed * fluid, targetZ = wish.z * speed * fluid;
        float accel = onGround ? 18f : 6f;
        velocity.x += (targetX - velocity.x) * Math.min(1f, accel * dt);
        velocity.z += (targetZ - velocity.z) * Math.min(1f, accel * dt);

        if (inWater || inLava) {
            velocity.y -= GRAVITY * 0.3f * dt;
            velocity.mul(1f - 0.6f * dt);
            if (in.jump) velocity.y = 4f;
            if (velocity.y < -4f) velocity.y = -4f;
        } else {
            velocity.y -= GRAVITY * dt;
            if (in.jump && onGround) { velocity.y = JUMP; onGround = false; }
        }
        if (velocity.y < -60f) velocity.y = -60f;
        resolve(w, dt);
    }

    private void updateStats(World w, float dt) {
        if (mode != Mode.SURVIVAL) { health = maxHealth; return; }
        if (onGround && !wasOnGround) {
            float fall = fallStartY - position.y;
            if (fall > 3f && !inWater) health -= (fall - 3f);
        }
        if (!onGround && wasOnGround) fallStartY = position.y;
        if (!onGround && position.y > fallStartY) fallStartY = position.y;
        wasOnGround = onGround;

        int fx = (int) Math.floor(position.x), fz = (int) Math.floor(position.z);
        int fy = (int) Math.floor(position.y + 0.5f);
        int feet = w.getBlock(fx, fy, fz);
        if (feet == Blocks.CACTUS) health -= 2f * dt;
        if (inLava) health -= 4f * dt;
        int magmaBelow = w.getBlock(fx, (int) Math.floor(position.y - 0.1f), fz);
        if (magmaBelow == Blocks.MAGMA && onGround) health -= 1f * dt;

        int head = w.getBlock(fx, (int) Math.floor(position.y + eyeHeight()), fz);
        if (Blocks.isOpaque(head)) health -= 1f * dt;

        if (hunger > 6 && health < maxHealth) {
            regenTimer += dt;
            if (regenTimer > 2f) { health += 1; regenTimer = 0; }
        }
        hunger = Math.max(0, hunger - dt * 0.02f);
        if (hunger <= 0) health -= dt * 0.5f;
        health = Math.max(0, Math.min(maxHealth, health));
        if (health <= 0) dead = true;
    }

    public void respawn(World w) {
        health = maxHealth; hunger = 20; dead = false; velocity.set(0);
        int h = w.spawnHeight(0, 0);
        position.set(0.5f, h, 0.5f);
    }
}
