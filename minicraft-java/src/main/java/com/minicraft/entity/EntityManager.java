package com.minicraft.entity;

import com.minicraft.audio.Sound;
import com.minicraft.player.Player;
import com.minicraft.render.Camera;
import com.minicraft.world.Blocks;
import com.minicraft.world.Chunk;
import com.minicraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spawns, updates, despawns and renders mobs; handles player melee combat. */
public class EntityManager {
    private final List<Entity> entities = new ArrayList<>();
    private final Random rng = new Random();
    private float spawnTimer;
    public Sound sound;

    public List<Entity> entities() { return entities; }
    public void clear() { entities.clear(); }

    public void update(World w, Player p, float dt, boolean night, boolean overworld) {
        for (Entity e : entities) {
            e.update(w, p, dt, night);
            if (e instanceof Zombie z && z.attacked && p.mode == Player.Mode.SURVIVAL && !p.dead) {
                p.health = Math.max(0, p.health - 3f);
                if (p.health <= 0) p.dead = true;
                // The hurt sound is triggered by the Game loop's health-drop check.
            }
        }
        // Remove dead / far entities.
        entities.removeIf(e -> {
            if (e.dead) return true;
            return e.position.distance(p.position) > 90f;
        });

        // Spawning (only in the Overworld).
        spawnTimer -= dt;
        if (overworld && spawnTimer <= 0) {
            spawnTimer = 1.5f;
            long sheep = entities.stream().filter(e -> e instanceof Sheep).count();
            long zombies = entities.stream().filter(e -> e instanceof Zombie).count();
            if (!night && sheep < 6) trySpawn(w, p, new Sheep());
            if (night && zombies < 8) trySpawn(w, p, new Zombie());
        }
    }

    private void trySpawn(World w, Player p, Entity e) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double r = 20 + rng.nextDouble() * 24;
            int x = (int) (p.position.x + Math.cos(ang) * r);
            int z = (int) (p.position.z + Math.sin(ang) * r);
            for (int y = Chunk.SY - 2; y > 4; y--) {
                if (Blocks.isSolid(w.getBlock(x, y, z))
                        && w.getBlock(x, y + 1, z) == Blocks.AIR
                        && w.getBlock(x, y + 2, z) == Blocks.AIR) {
                    int top = w.getBlock(x, y, z);
                    if (top == Blocks.WATER || top == Blocks.LAVA) break;
                    e.position.set(x + 0.5f, y + 1, z + 0.5f);
                    entities.add(e);
                    return;
                }
            }
        }
    }

    /** Player melee: hit the nearest mob in the look direction. Returns true if it connected. */
    public boolean attack(Player p, Camera cam, float reach, float damage) {
        Vector3f eye = new Vector3f(cam.position);
        Vector3f dir = cam.front();
        Entity best = null;
        float bestDist = reach;
        for (Entity e : entities) {
            Vector3f center = new Vector3f(e.position).add(0, e.height * 0.5f, 0);
            Vector3f to = new Vector3f(center).sub(eye);
            float d = to.length();
            if (d > reach) continue;
            to.normalize();
            if (to.dot(dir) > 0.93f && d < bestDist) { best = e; bestDist = d; }
        }
        if (best == null) return false;
        best.hurt(damage, p.position);
        if (sound != null) sound.playHurt();
        if (best.dead) {
            for (int drop : best.drops()) p.inventory.add(drop, 1);
        }
        return true;
    }

    public void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(entities.size());
        for (Entity e : entities) {
            out.writeByte(e instanceof Zombie ? 1 : 0);
            out.writeFloat(e.position.x); out.writeFloat(e.position.y); out.writeFloat(e.position.z);
            out.writeFloat(e.health);
        }
    }

    public void readSave(java.io.DataInputStream in) throws java.io.IOException {
        entities.clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            int type = in.readByte();
            Entity e = type == 1 ? new Zombie() : new Sheep();
            e.position.set(in.readFloat(), in.readFloat(), in.readFloat());
            e.health = in.readFloat();
            entities.add(e);
        }
    }
}
