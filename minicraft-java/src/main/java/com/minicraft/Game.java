package com.minicraft;

import com.minicraft.assets.AssetLoader;
import com.minicraft.audio.Sound;
import com.minicraft.core.Window;
import com.minicraft.entity.EntityManager;
import com.minicraft.entity.Entity;
import com.minicraft.player.InputState;
import com.minicraft.player.Player;
import com.minicraft.render.*;
import com.minicraft.ui.ChestScreen;
import com.minicraft.ui.Font;
import com.minicraft.ui.FurnaceScreen;
import com.minicraft.ui.HUD;
import com.minicraft.ui.InventoryScreen;
import com.minicraft.world.*;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Game {
    private enum State { MENU, PLAYING, PAUSED, INVENTORY, FURNACE, CHEST }

    private final Window window;
    private final Camera camera = new Camera();
    private final TextureAtlas atlas;
    private final Shader chunkShader = new Shader();
    private final Shader lineShader = new Shader();
    private final HUD hud = new HUD();
    private final Font font = new Font();
    private final Clouds clouds = new Clouds();
    private final HandRenderer hand = new HandRenderer();
    private final InventoryScreen invScreen = new InventoryScreen();
    private final FurnaceScreen furnaceScreen = new FurnaceScreen();
    private final ChestScreen chestScreen = new ChestScreen();
    private final Map<Long, Furnace> furnaces = new HashMap<>();
    private final Map<Long, Container> chests = new HashMap<>();
    private Furnace openFurnace;
    private Container openChest;
    private final Sound sound = new Sound();
    private final EntityManager entityManager = new EntityManager();
    private final EntityRenderer entityRenderer = new EntityRenderer();
    private final Player player = new Player();
    private final ExecutorService pool;

    private boolean prevOnGround = true;
    private float prevHealth = 20f, stepTimer;

    private final World[] worlds = new World[3];
    private World world;
    private Dimension dim = Dimension.OVERWORLD;
    private final long seed = 1337;

    private State state = State.MENU;
    private int renderDistance = 8;
    private double worldTime = 120, dayLength = 600;

    private boolean debug, thirdPerson, firstMouse = true;
    private double lastX, lastY, mouseX, mouseY;
    private boolean lmbPrev, rmbPrev, jumpPrev, escPrev, ePrev;
    private World.Hit lastHit = new World.Hit();
    private int breakingX, breakingY, breakingZ;
    private float breakProgress;

    private int selVao, selVbo;
    private float portalTimer, swingTime, bobPhase;
    private final String savePath = "world.sav";
    private double saveTimer;

    private boolean hasSave;
    private float[] savedPos;
    private int savedDim;

    public Game(int width, int height) {
        window = new Window(width, height, "MiniCraft (Java)");
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        atlas = new TextureAtlas();
        AssetLoader.tryLoadOfficial(atlas);
        atlas.upload();

        chunkShader.loadFromResources("/shaders/chunk.vert", "/shaders/chunk.frag");
        lineShader.loadFromResources("/shaders/line.vert", "/shaders/line.frag");
        hud.init(); hud.atlas = atlas; hud.font = font;
        font.bake();
        clouds.init();
        hand.init();
        entityRenderer.init();
        sound.init();
        entityManager.sound = sound;

        int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        pool = Executors.newFixedThreadPool(cores, r -> { Thread t = new Thread(r, "chunk-worker"); t.setDaemon(true); return t; });

        worlds[0] = new World(seed, Dimension.OVERWORLD, pool);
        worlds[1] = new World(seed, Dimension.NETHER, pool);
        worlds[2] = new World(seed, Dimension.END, pool);
        world = worlds[0];

        load();
        setupCallbacks();
        setupSelectionBox();

        window.setCursorCaptured(false);
        String autoStart = System.getenv("MINICRAFT_START_DIM");
        if (autoStart != null) {
            Player.Mode m = "SURVIVAL".equalsIgnoreCase(System.getenv("MINICRAFT_MODE"))
                    ? Player.Mode.SURVIVAL : Player.Mode.CREATIVE;
            startWorld(m, false);
        }
    }

    private void startWorld(Player.Mode mode, boolean useSave) {
        player.mode = mode;
        player.dead = false; player.health = 20; player.hunger = 20;
        if (!useSave) {
            for (World w : worlds) w.clearEdits();
            furnaces.clear(); chests.clear(); entityManager.clear();
            dim = Dimension.OVERWORLD; world = worlds[0];
            for (int i = 0; i < player.inventory.slots.length; i++) player.inventory.slots[i].clear();
            if (mode == Player.Mode.CREATIVE) player.inventory.giveCreativeHotbar();
            else player.inventory.giveStarterKit();
        } else {
            dim = Dimension.values()[savedDim]; world = worlds[savedDim];
        }
        int surf = world.spawnHeight(0, 0);
        if (useSave && savedPos != null) player.position.set(savedPos[0], savedPos[1], savedPos[2]);
        else player.position.set(0.5f, surf + 1, 0.5f);
        camera.position.set(player.position.x, player.position.y + player.eyeHeight(), player.position.z);
        camera.aspect = window.aspect();
        for (int i = 0; i < 400; i++) {
            world.update(camera.position.x, camera.position.z, renderDistance);
            if (world.getBlock((int) player.position.x, surf - 3, (int) player.position.z) != Blocks.AIR) break;
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }
        state = State.PLAYING;
        window.setCursorCaptured(true);
        firstMouse = true;
        String autoStart = System.getenv("MINICRAFT_START_DIM");
        if (autoStart != null) { try { travelTo(Dimension.valueOf(autoStart.toUpperCase()), 0, 0); } catch (Exception ignored) {} }
        if (System.getenv("MINICRAFT_OPEN_INV") != null) openInventory();
        if (System.getenv("MINICRAFT_OPEN_FURNACE") != null) {
            openFurnaceAt(0, 64, 0);
            openFurnace.input.set(Blocks.IRON_ORE, 5);
            openFurnace.fuel.set(Items.COAL, 3);
            openFurnace.output.set(Items.IRON_INGOT, 2);
        }
        String slotEnv = System.getenv("MINICRAFT_SLOT");
        if (slotEnv != null) try { player.inventory.selected = Integer.parseInt(slotEnv); } catch (Exception ignored) {}
        if (System.getenv("MINICRAFT_SPAWN_MOBS") != null) spawnDebugMobs();
        if (System.getenv("MINICRAFT_OPEN_CHEST") != null) {
            openChestAt(0, 64, 0);
            openChest.slots[0].set(Blocks.DIAMOND_ORE, 12);
            openChest.slots[1].set(Items.IRON_INGOT, 30);
            openChest.slots[2].set(Blocks.LOG, 64);
            openChest.slots[10].set(Items.DIAMOND, 5);
        }
    }

    private void spawnDebugMobs() {
        int sx = (int) player.position.x, sz = (int) player.position.z;
        int sy = (int) player.position.y;
        com.minicraft.entity.Sheep a = new com.minicraft.entity.Sheep(); a.position.set(sx + 3.5f, sy + 1, sz + 2.5f);
        com.minicraft.entity.Sheep b = new com.minicraft.entity.Sheep(); b.position.set(sx + 5.5f, sy + 1, sz + 4.5f);
        com.minicraft.entity.Zombie z = new com.minicraft.entity.Zombie(); z.position.set(sx + 4.5f, sy + 1, sz - 3.5f);
        entityManager.entities().add(a); entityManager.entities().add(b); entityManager.entities().add(z);
    }

    private void setupSelectionBox() {
        float[] e = {
            0,0,0, 1,0,0,  1,0,0, 1,1,0,  1,1,0, 0,1,0,  0,1,0, 0,0,0,
            0,0,1, 1,0,1,  1,0,1, 1,1,1,  1,1,1, 0,1,1,  0,1,1, 0,0,1,
            0,0,0, 0,0,1,  1,0,0, 1,0,1,  1,1,0, 1,1,1,  0,1,0, 0,1,1,
        };
        selVao = glGenVertexArrays(); selVbo = glGenBuffers();
        glBindVertexArray(selVao); glBindBuffer(GL_ARRAY_BUFFER, selVbo);
        java.nio.FloatBuffer fb = BufferUtils.createFloatBuffer(e.length); fb.put(e).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glBindVertexArray(0);
    }

    private void setupCallbacks() {
        window.onResize = (w, h) -> camera.aspect = h > 0 ? (float) w / h : 1f;
        window.onScroll = (dx, dy) -> { if (state == State.PLAYING) player.scrollHotbar((int) dy); };
        window.onKey = (key, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) { onEscape(); return; }
            if (state != State.PLAYING) {
                if (state == State.INVENTORY && key == GLFW_KEY_E) closeInventory();
                if (state == State.FURNACE && key == GLFW_KEY_E) closeFurnace();
                if (state == State.CHEST && key == GLFW_KEY_E) closeChest();
                return;
            }
            switch (key) {
                case GLFW_KEY_E -> openInventory();
                case GLFW_KEY_F3 -> debug = !debug;
                case GLFW_KEY_F11 -> window.toggleFullscreen();
                case GLFW_KEY_F5 -> thirdPerson = !thirdPerson;
                case GLFW_KEY_G -> player.toggleMode();
                case GLFW_KEY_R -> { if (player.dead) respawn(); }
                case GLFW_KEY_F2 -> screenshot("screenshot_" + System.currentTimeMillis() + ".png");
                case GLFW_KEY_F -> igniteNetherPortal();
                case GLFW_KEY_O -> travelTo(Dimension.OVERWORLD, player.position.x, player.position.z);
                case GLFW_KEY_N -> travelTo(Dimension.NETHER, player.position.x / 8f, player.position.z / 8f);
                case GLFW_KEY_M -> travelTo(Dimension.END, 0, 0);
                default -> {}
            }
            if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) player.inventory.selected = key - GLFW_KEY_1;
        };
    }

    private void onEscape() {
        switch (state) {
            case PLAYING -> { state = State.PAUSED; window.setCursorCaptured(false); }
            case PAUSED -> { state = State.PLAYING; window.setCursorCaptured(true); firstMouse = true; }
            case INVENTORY -> closeInventory();
            case FURNACE -> closeFurnace();
            case CHEST -> closeChest();
            case MENU -> {}
        }
    }

    private long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) ^ ((long) (y & 0xFFF) << 30) ^ (z & 0x3FFFFFFF);
    }
    private void openChestAt(int x, int y, int z) {
        openChest = chests.computeIfAbsent(blockKey(x, y, z), k -> new Container());
        state = State.CHEST;
        window.setCursorCaptured(false);
    }
    private void closeChest() {
        if (!player.inventory.cursor.isEmpty()) {
            player.inventory.add(player.inventory.cursor.item, player.inventory.cursor.count);
            player.inventory.cursor.clear();
        }
        openChest = null;
        state = State.PLAYING;
        window.setCursorCaptured(true);
        firstMouse = true;
    }

    private void openFurnaceAt(int x, int y, int z) {
        openFurnace = furnaces.computeIfAbsent(blockKey(x, y, z), k -> new Furnace());
        state = State.FURNACE;
        window.setCursorCaptured(false);
    }
    private void closeFurnace() {
        if (!player.inventory.cursor.isEmpty()) {
            player.inventory.add(player.inventory.cursor.item, player.inventory.cursor.count);
            player.inventory.cursor.clear();
        }
        openFurnace = null;
        state = State.PLAYING;
        window.setCursorCaptured(true);
        firstMouse = true;
    }

    private void openInventory() {
        state = State.INVENTORY;
        window.setCursorCaptured(false);
        invScreen.refreshCraft(player.inventory);
    }
    private void closeInventory() {
        invScreen.returnCraftItems(player.inventory);
        state = State.PLAYING;
        window.setCursorCaptured(true);
        firstMouse = true;
    }
    private void respawn() {
        player.respawn(world);
        if (player.mode == Player.Mode.SURVIVAL) {
            for (ItemStack s : player.inventory.slots) s.clear();
            player.inventory.giveStarterKit();
        }
    }

    private void gatherInput(InputState in) {
        in.forward = window.keyDown(GLFW_KEY_W);
        in.back = window.keyDown(GLFW_KEY_S);
        in.left = window.keyDown(GLFW_KEY_A);
        in.right = window.keyDown(GLFW_KEY_D);
        in.jump = window.keyDown(GLFW_KEY_SPACE);
        in.sneak = window.keyDown(GLFW_KEY_LEFT_SHIFT);
        in.sprint = window.keyDown(GLFW_KEY_LEFT_CONTROL);
        in.jumpPressed = in.jump && !jumpPrev;
        jumpPrev = in.jump;
    }

    // --- Dimensions & portals (unchanged core logic) ---

    private void travelTo(Dimension target, float x, float z) {
        if (target == dim) return;
        dim = target; world = worlds[target.ordinal()];
        entityManager.clear(); // mobs belong to the dimension they were in
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int i = 0; i < 300; i++) {
            world.update(x, z, renderDistance);
            if (columnReady(ix, iz)) break;
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }
        int y = safeY(ix, iz);
        player.position.set(ix + 0.5f, y, iz + 0.5f);
        player.velocity.set(0); firstMouse = true; portalTimer = 0;
        System.out.println("[INFO] Entered " + target + " at " + ix + "," + y + "," + iz);
    }

    private boolean columnReady(int x, int z) {
        for (int y = Chunk.SY - 1; y >= 0; y--) if (Blocks.isSolid(world.getBlock(x, y, z))) return true;
        return false;
    }
    private int safeY(int x, int z) {
        int top = dim == Dimension.NETHER ? 110 : Chunk.SY - 2;
        for (int y = top; y >= 1; y--) {
            int here = world.getBlock(x, y, z);
            if (Blocks.isSolid(here) && here != Blocks.LAVA
                    && world.getBlock(x, y + 1, z) == Blocks.AIR && world.getBlock(x, y + 2, z) == Blocks.AIR)
                return y + 1;
        }
        return Chunk.SEA_LEVEL + 2;
    }
    private void igniteNetherPortal() {
        if (!lastHit.hit || world.getBlock(lastHit.bx, lastHit.by, lastHit.bz) != Blocks.OBSIDIAN) return;
        if (fillPortal(lastHit.bx, lastHit.by, lastHit.bz, true)) return;
        fillPortal(lastHit.bx, lastHit.by, lastHit.bz, false);
    }
    private boolean fillPortal(int ox, int oy, int oz, boolean alongX) {
        for (int sy = oy; sy <= oy + 1; sy++)
            for (int s = -1; s <= 1; s++) {
                int ix = alongX ? ox + s : ox, iz = alongX ? oz : oz + s, iy = sy + 1;
                if (world.getBlock(ix, iy, iz) != Blocks.AIR) continue;
                List<int[]> interior = floodInterior(ix, iy, iz, alongX);
                if (interior != null) { for (int[] p : interior) world.setBlock(p[0], p[1], p[2], Blocks.PORTAL); return true; }
            }
        return false;
    }
    private List<int[]> floodInterior(int sx, int sy, int sz, boolean alongX) {
        List<int[]> cells = new ArrayList<>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        q.add(new int[]{sx, sy, sz});
        int[][] dirs = alongX ? new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}} : new int[][]{{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            long k = ((long) c[0] << 40) ^ ((long) (c[1] & 0xFFFFF) << 20) ^ (c[2] & 0xFFFFF);
            if (!seen.add(k)) continue;
            int b = world.getBlock(c[0], c[1], c[2]);
            if (b == Blocks.OBSIDIAN) continue;
            if (b != Blocks.AIR) return null;
            cells.add(c);
            if (cells.size() > 30) return null;
            for (int[] d : dirs) q.add(new int[]{c[0] + d[0], c[1] + d[1], c[2] + d[2]});
        }
        return cells.isEmpty() ? null : cells;
    }
    private void buildPortalAt(World w, int x, int y, int z) {
        for (int dx = 0; dx < 4; dx++)
            for (int dy = 0; dy < 5; dy++) {
                boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                w.setBlock(x + dx, y + dy, z, frame ? Blocks.OBSIDIAN : Blocks.PORTAL);
            }
    }
    private void checkPortalTravel(float dt) {
        int fx = (int) Math.floor(player.position.x), fy = (int) Math.floor(player.position.y + 0.5f), fz = (int) Math.floor(player.position.z);
        int b = world.getBlock(fx, fy, fz);
        if (b == Blocks.PORTAL) {
            portalTimer += dt;
            if (portalTimer > 1.0f && dim != Dimension.NETHER) {
                travelTo(Dimension.NETHER, player.position.x / 8f, player.position.z / 8f);
                buildPortalAt(world, (int) player.position.x, (int) player.position.y, (int) player.position.z + 1);
            } else if (portalTimer > 1.0f) travelTo(Dimension.OVERWORLD, player.position.x * 8f, player.position.z * 8f);
        } else if (b == Blocks.END_PORTAL) {
            portalTimer += dt;
            if (portalTimer > 0.6f) travelTo(dim == Dimension.END ? Dimension.OVERWORLD : Dimension.END, 0, 0);
        } else portalTimer = 0;
    }

    // --- Interaction ---

    private void handleInteraction(float dt) {
        if (player.dead || player.mode == Player.Mode.SPECTATOR) { breakProgress = 0; lmbPrev = rmbPrev = false; return; }
        boolean lmb = window.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        boolean rmb = window.mouseDown(GLFW_MOUSE_BUTTON_RIGHT);
        lastHit = world.raycast(camera.position, camera.front(), 6f);

        // Left click press: attack a mob in front if there is one.
        if (lmb && !lmbPrev) {
            startSwing();
            if (entityManager.attack(player, camera, 3.5f, attackDamage())) { lmbPrev = true; rmbPrev = rmb; return; }
        }

        if (lmb && lastHit.hit) {
            int target = world.getBlock(lastHit.bx, lastHit.by, lastHit.bz);
            float hardness = Blocks.get(target).hardness;
            if (hardness < 0) { /* unbreakable */ }
            else if (player.mode == Player.Mode.CREATIVE) {
                if (!lmbPrev) breakBlock(lastHit.bx, lastHit.by, lastHit.bz);
            } else {
                if (breakingX != lastHit.bx || breakingY != lastHit.by || breakingZ != lastHit.bz) {
                    breakingX = lastHit.bx; breakingY = lastHit.by; breakingZ = lastHit.bz; breakProgress = 0;
                    sound.playDig(target);
                }
                breakProgress += dt / Math.max(hardness, 0.05f);
                if (breakProgress >= 1f) { breakBlock(lastHit.bx, lastHit.by, lastHit.bz); breakProgress = 0; }
            }
        } else breakProgress = 0;

        if (rmb && !rmbPrev && lastHit.hit) {
            int hitBlock = world.getBlock(lastHit.bx, lastHit.by, lastHit.bz);
            if (hitBlock == Blocks.CRAFTING_TABLE) openInventory();
            else if (hitBlock == Blocks.FURNACE) openFurnaceAt(lastHit.bx, lastHit.by, lastHit.bz);
            else if (hitBlock == Blocks.CHEST) openChestAt(lastHit.bx, lastHit.by, lastHit.bz);
            else placeBlock();
            startSwing();
        }
        lmbPrev = lmb; rmbPrev = rmb;
    }

    private float attackDamage() {
        ItemStack s = player.inventory.selectedStack();
        if (s.isEmpty()) return 2f;
        Items.Def d = Items.get(s.item);
        if (d.tool == Items.Tool.SWORD) return 4f + d.tier * 1.5f;
        if (d.tool == Items.Tool.AXE) return 3f + d.tier;
        return 2f;
    }

    private void breakBlock(int x, int y, int z) {
        int b = world.getBlock(x, y, z);
        if (Blocks.get(b).hardness < 0) return;
        world.setBlock(x, y, z, Blocks.AIR);
        sound.playBreak(b);
        // Clean up / drop block-entity contents.
        long key = blockKey(x, y, z);
        furnaces.remove(key);
        Container chest = chests.remove(key);
        if (b == Blocks.GRASS || b == Blocks.SAND || b == Blocks.GRAVEL) sound.playDig(b);
        if (player.mode == Player.Mode.SURVIVAL) {
            int drop = Items.dropFor(b);
            if (drop >= 0) player.inventory.add(drop, 1);
            if (chest != null) for (ItemStack s : chest.slots) if (!s.isEmpty()) player.inventory.add(s.item, s.count);
        }
    }

    private void placeBlock() {
        int px = lastHit.px, py = lastHit.py, pz = lastHit.pz;
        int id = player.selectedBlock();
        if (id == Blocks.AIR || world.getBlock(px, py, pz) != Blocks.AIR) return;
        float hx = player.halfX(), hz = player.halfZ();
        float mnx = player.position.x - hx, mxx = player.position.x + hx;
        float mny = player.position.y, mxy = player.position.y + player.height();
        float mnz = player.position.z - hz, mxz = player.position.z + hz;
        boolean overlap = !(px + 1 <= mnx || px >= mxx || py + 1 <= mny || py >= mxy || pz + 1 <= mnz || pz >= mxz);
        if (overlap && Blocks.isSolid(id)) return;
        if (player.mode == Player.Mode.SURVIVAL && !player.inventory.consumeSelected()) return;
        world.setBlock(px, py, pz, id);
        sound.playPlace(id);
    }

    private void startSwing() { swingTime = 0.28f; }

    // --- UI click handling ---

    private void uiClick(boolean right) {
        if (state == State.INVENTORY) {
            invScreen.click(player.inventory, player.mode == Player.Mode.CREATIVE, window.width(), window.height(), mouseX, mouseY, right);
        } else if (state == State.FURNACE && openFurnace != null) {
            furnaceScreen.click(player.inventory, openFurnace, window.width(), window.height(), mouseX, mouseY, right);
        } else if (state == State.CHEST && openChest != null) {
            chestScreen.click(player.inventory, openChest, window.width(), window.height(), mouseX, mouseY, right);
        } else if (state == State.MENU || state == State.PAUSED) {
            if (right) return;
            int action = menuHit(mouseX, mouseY);
            handleMenuAction(action);
        }
    }

    private List<String> menuButtons() {
        List<String> b = new ArrayList<>();
        if (state == State.MENU) {
            b.add("New World - Survival");
            b.add("New World - Creative");
            if (hasSave) b.add("Continue");
            b.add("Quit");
        } else {
            b.add("Resume");
            b.add("Save & Quit to Title");
        }
        return b;
    }

    private float[] buttonRect(int i, int n) {
        float w = 360, h = 46, gap = 12;
        float totalH = n * h + (n - 1) * gap;
        float x = window.width() / 2f - w / 2f;
        float y = window.height() / 2f - totalH / 2f + i * (h + gap);
        return new float[]{x, y, w, h};
    }

    private int menuHit(double mx, double my) {
        List<String> b = menuButtons();
        for (int i = 0; i < b.size(); i++) {
            float[] r = buttonRect(i, b.size());
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return i;
        }
        return -1;
    }

    private void handleMenuAction(int i) {
        if (i < 0) return;
        List<String> b = menuButtons();
        String label = b.get(i);
        switch (label) {
            case "New World - Survival" -> startWorld(Player.Mode.SURVIVAL, false);
            case "New World - Creative" -> startWorld(Player.Mode.CREATIVE, false);
            case "Continue" -> startWorld(player.mode, true);
            case "Quit" -> window.setShouldClose(true);
            case "Resume" -> { state = State.PLAYING; window.setCursorCaptured(true); firstMouse = true; }
            case "Save & Quit to Title" -> { save(); state = State.MENU; window.setCursorCaptured(false); hasSave = true; }
        }
    }

    // --- Rendering ---

    private boolean isNight() {
        if (!dim.hasSky) return false;
        float t = (float) ((worldTime % dayLength) / dayLength);
        return Math.sin(t * 6.2831853f) < -0.05f;
    }

    private float dayNight(Vector3f outSky, Vector3f outSun) {
        float dayLight;
        if (dim.hasSky) {
            float t = (float) ((worldTime % dayLength) / dayLength);
            float angle = t * 6.2831853f;
            float sunH = (float) Math.sin(angle);
            outSun.set((float) Math.cos(angle), sunH, 0.35f).normalize();
            dayLight = Math.max(0, Math.min(1, (sunH + 0.15f) / 0.4f));
            Vector3f daySky = new Vector3f(0.47f, 0.66f, 0.98f), night = new Vector3f(0.02f, 0.03f, 0.07f);
            outSky.set(night).lerp(daySky, dayLight);
            float horizon = Math.max(0, Math.min(1, 1 - Math.abs(sunH) * 3)) * dayLight;
            outSky.lerp(new Vector3f(0.95f, 0.55f, 0.30f), horizon * 0.5f);
        } else { outSun.set(0, 1, 0.3f).normalize(); dayLight = 1f; outSky.set(dim.baseFog); }
        return dayLight;
    }

    private void renderWorld() {
        Vector3f sky = new Vector3f(), sun = new Vector3f();
        float dayLight = dayNight(sky, sun);
        glClearColor(sky.x, sky.y, sky.z, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        Vector3f eye = new Vector3f(player.position.x, player.position.y + player.eyeHeight(), player.position.z);
        camera.position.set(eye);
        if (thirdPerson) {
            Vector3f back = new Vector3f(camera.front()).negate();
            World.Hit h = world.raycast(eye, back, 4f);
            float dist = h.hit ? (float) Math.sqrt(Math.pow(h.bx - eye.x, 2) + Math.pow(h.by - eye.y, 2) + Math.pow(h.bz - eye.z, 2)) - 0.3f : 4f;
            camera.position.set(eye).add(new Vector3f(camera.front()).mul(-Math.max(0.5f, Math.min(4f, dist))));
        }
        camera.aspect = window.aspect();
        camera.farPlane = (renderDistance + 2) * 16f;
        Matrix4f view = camera.view(), proj = camera.projection();
        Matrix4f vp = new Matrix4f(proj).mul(view);
        float fogStart = (renderDistance - 2) * 16f, fogEnd = (renderDistance + 0.5f) * 16f;

        chunkShader.use();
        chunkShader.set("uView", view); chunkShader.set("uProj", proj); chunkShader.set("uAtlas", 0);
        chunkShader.set("uSunDir", sun); chunkShader.set("uDayLight", dayLight); chunkShader.set("uAmbient", dim.ambient);
        chunkShader.set("uFogColor", sky); chunkShader.set("uFogStart", fogStart); chunkShader.set("uFogEnd", fogEnd);
        chunkShader.set("uCameraPos", camera.position);
        atlas.bind(0);
        chunkShader.set("uAlphaCutout", 1);
        glDisable(GL_BLEND);
        world.renderOpaque(chunkShader, camera);

        // Mobs.
        entityRenderer.begin(view, proj, sun, dayLight, dim.ambient, sky, fogStart, fogEnd, camera.position);
        for (Entity e : entityManager.entities()) entityRenderer.render(e);
        entityRenderer.end();

        if (dim.hasSky) clouds.render(view, proj, camera.position, 112f, (float) worldTime, sky, 420f);

        renderSelection(vp);

        chunkShader.use();
        chunkShader.set("uAlphaCutout", 0);
        atlas.bind(0); // clouds rebound unit 0; restore the block atlas
        glEnable(GL_BLEND); glDepthMask(false);
        world.renderTransparent(chunkShader, camera);
        glDepthMask(true);

        // First-person hand / held item.
        if (!thirdPerson && !player.dead) {
            ItemStack sel = player.inventory.selectedStack();
            if (!sel.isEmpty()) {
                float swing = swingTime > 0 ? 1f - swingTime / 0.28f : 0f;
                hand.render(chunkShader, atlas, sel.item, camera.aspect, swing, bobPhase);
            }
        }
    }

    private void renderSelection(Matrix4f vp) {
        if (!lastHit.hit || state != State.PLAYING) return;
        Matrix4f model = new Matrix4f().translate(lastHit.bx - 0.002f, lastHit.by - 0.002f, lastHit.bz - 0.002f).scale(1.004f);
        lineShader.use();
        lineShader.set("uMVP", new Matrix4f(vp).mul(model));
        lineShader.set("uColor", new Vector4f(0, 0, 0, 0.55f + breakProgress * 0.4f));
        glLineWidth(2f);
        glBindVertexArray(selVao); glDrawArrays(GL_LINES, 0, 24); glBindVertexArray(0);
    }

    private void renderMenuScreen() {
        glClearColor(0.10f, 0.12f, 0.18f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        hud.begin(window.width(), window.height());
        // dim background bands
        for (int i = 0; i < window.height(); i += 4)
            hud.rect(0, i, window.width(), 2, 0.08f, 0.10f + (i / (float) window.height()) * 0.1f, 0.16f, 0.5f);
        float cx = window.width() / 2f;
        hud.textCentered("MiniCraft", cx, window.height() * 0.18f, 5f, 0.55f, 0.85f, 0.45f, 1f);
        hud.textCentered("a voxel sandbox", cx, window.height() * 0.18f + 60, 1.6f, 0.85f, 0.85f, 0.85f, 1f);
        drawButtons();
        hud.textCentered("WASD move  -  E inventory  -  G mode  -  O/N/M dimensions", cx, window.height() - 30, 1.2f, 0.8f, 0.8f, 0.8f, 1f);
        hud.end();
    }

    private void drawButtons() {
        List<String> b = menuButtons();
        for (int i = 0; i < b.size(); i++) {
            float[] r = buttonRect(i, b.size());
            boolean hover = mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3];
            hud.panel(r[0], r[1], r[2], r[3]);
            if (hover) hud.rect(r[0], r[1], r[2], r[3], 1, 1, 1, 0.12f);
            hud.textCentered(b.get(i), r[0] + r[2] / 2f, r[1] + 13, 1.7f, 0.12f, 0.12f, 0.12f, 1f);
        }
    }

    private void updateTitle(float fps) {
        String mode = switch (player.mode) { case SURVIVAL -> "Survival"; case CREATIVE -> "Creative"; case SPECTATOR -> "Spectator"; };
        String title;
        if (state == State.MENU) title = "MiniCraft";
        else if (player.dead) title = "MiniCraft -- YOU DIED -- press R";
        else if (debug) title = String.format("MiniCraft | %s | FPS %.0f | XYZ %.1f %.1f %.1f | %s | chunks %d", dim, fps, player.position.x, player.position.y, player.position.z, mode, world.loadedChunks());
        else title = String.format("MiniCraft | %s | %s | FPS %.0f", dim, mode, fps);
        window.setTitle(title);
    }

    public void run() {
        double dt = 1.0 / 60.0, prev = glfwGetTime(), acc = 0, fpsTimer = 0;
        int frames = 0, shotFrame = 0; float fps = 0;
        String shotEnv = System.getenv("MINICRAFT_SCREENSHOT");

        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double frame = Math.min(0.25, now - prev); prev = now; acc += frame;
            window.pollEvents();

            double[] m = window.cursorPos(); mouseX = m[0]; mouseY = m[1];

            // UI mouse-click edges.
            boolean lmb = window.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
            boolean rmb = window.mouseDown(GLFW_MOUSE_BUTTON_RIGHT);
            if (state != State.PLAYING) {
                if (lmb && !lmbPrev) uiClick(false);
                if (rmb && !rmbPrev) uiClick(true);
                lmbPrev = lmb; rmbPrev = rmb;
            }

            if (state == State.PLAYING) {
                if (firstMouse) { lastX = mouseX; lastY = mouseY; firstMouse = false; }
                float mdx = (float) (mouseX - lastX), mdy = (float) (mouseY - lastY);
                lastX = mouseX; lastY = mouseY;
                if (!player.dead) camera.addMouseDelta(mdx, mdy, player.mouseSensitivity);

                InputState in = new InputState(); gatherInput(in);
                int steps = 0;
                while (acc >= dt && steps < 5) { player.update(world, camera, in, (float) dt); in.jumpPressed = false; acc -= dt; steps++; }
                if (acc > dt) acc = 0;

                worldTime += frame;
                if (swingTime > 0) swingTime -= (float) frame;
                float hspeed = (float) Math.sqrt(player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z);
                if (player.onGround && hspeed > 0.5f) bobPhase += frame * 10f;
                handleInteraction((float) frame);
                checkPortalTravel((float) frame);
                world.update(camera.position.x, camera.position.z, renderDistance);
                entityManager.update(world, player, (float) frame, isNight(), dim == Dimension.OVERWORLD);

                // Footsteps, jump and hurt cues.
                stepTimer -= (float) frame;
                if (player.onGround && hspeed > 1.5f && stepTimer <= 0) {
                    int below = world.getBlock((int) Math.floor(player.position.x), (int) Math.floor(player.position.y - 0.1f), (int) Math.floor(player.position.z));
                    if (below != Blocks.AIR) sound.playStep(below);
                    stepTimer = 0.34f;
                }
                if (prevOnGround && !player.onGround && player.velocity.y > 0.1f) sound.playJump();
                prevOnGround = player.onGround;
                if (player.health < prevHealth - 0.4f) sound.playHurt();
                prevHealth = player.health;
            } else {
                acc = 0;
                // keep streaming so the frozen world stays loaded behind menus
                if (state != State.MENU) world.update(camera.position.x, camera.position.z, renderDistance);
            }

            // Furnaces smelt in the background while a world is active.
            if (state != State.MENU && state != State.PAUSED)
                for (Furnace fu : furnaces.values()) fu.tick((float) frame);

            // Render.
            if (state == State.MENU) {
                renderMenuScreen();
            } else {
                renderWorld();
                hud.renderGame(window.width(), window.height(), player);
                if (state == State.INVENTORY) invScreen.render(hud, window.width(), window.height(), player.inventory, player.mode == Player.Mode.CREATIVE, mouseX, mouseY);
                else if (state == State.FURNACE && openFurnace != null) furnaceScreen.render(hud, window.width(), window.height(), player.inventory, openFurnace, mouseX, mouseY);
                else if (state == State.CHEST && openChest != null) chestScreen.render(hud, window.width(), window.height(), player.inventory, openChest, mouseX, mouseY);
                else if (state == State.PAUSED) { hud.overlay(window.width(), window.height(), 0, 0, 0, 0.5f); hud.begin(window.width(), window.height()); hud.textCentered("Paused", window.width() / 2f, window.height() * 0.28f, 3f, 1, 1, 1, 1); drawButtons(); hud.end(); }
                if (player.dead) { hud.overlay(window.width(), window.height(), 0.5f, 0, 0, 0.45f); hud.begin(window.width(), window.height()); hud.textCentered("You Died!  Press R", window.width() / 2f, window.height() / 2f, 3f, 1, 0.9f, 0.9f, 1); hud.end(); }
            }

            if (shotEnv != null && ++shotFrame == 120) { screenshot(shotEnv); window.setShouldClose(true); }
            window.swapBuffers();

            saveTimer += frame;
            if (saveTimer > 30 && state == State.PLAYING) { save(); saveTimer = 0; }
            frames++; fpsTimer += frame;
            if (fpsTimer >= 0.5) { fps = (float) (frames / fpsTimer); frames = 0; fpsTimer = 0; updateTitle(fps); }
        }

        if (state != State.MENU) save();
        pool.shutdownNow();
        for (World w : worlds) w.shutdownGpu();
        sound.shutdown();
        window.destroy();
    }

    // --- Save / load ---

    private static void writeStack(DataOutputStream out, ItemStack s) throws IOException { out.writeInt(s.item); out.writeInt(s.count); }
    private static void readStack(DataInputStream in, ItemStack s) throws IOException { s.item = in.readInt(); s.count = in.readInt(); }

    private void save() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(savePath)))) {
            out.writeInt(0x4D435733); out.writeLong(seed); out.writeInt(dim.ordinal()); out.writeInt(player.mode.ordinal());
            out.writeFloat(player.position.x); out.writeFloat(player.position.y); out.writeFloat(player.position.z);
            for (ItemStack s : player.inventory.slots) writeStack(out, s);
            for (World w : worlds) w.writeEdits(out);

            out.writeInt(furnaces.size());
            for (Map.Entry<Long, Furnace> e : furnaces.entrySet()) {
                out.writeLong(e.getKey());
                Furnace f = e.getValue();
                writeStack(out, f.input); writeStack(out, f.fuel); writeStack(out, f.output);
            }
            out.writeInt(chests.size());
            for (Map.Entry<Long, Container> e : chests.entrySet()) {
                out.writeLong(e.getKey());
                for (ItemStack s : e.getValue().slots) writeStack(out, s);
            }
            entityManager.writeSave(out);
        } catch (IOException e) { System.out.println("[WARN] save failed: " + e.getMessage()); }
    }

    private void load() {
        File f = new File(savePath);
        if (!f.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != 0x4D435733) return;
            in.readLong();
            savedDim = Math.max(0, Math.min(2, in.readInt()));
            int modeOrd = in.readInt();
            player.mode = Player.Mode.values()[Math.max(0, Math.min(2, modeOrd))];
            savedPos = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            for (ItemStack s : player.inventory.slots) readStack(in, s);
            for (World w : worlds) w.readEdits(in);

            furnaces.clear();
            int nf = in.readInt();
            for (int i = 0; i < nf; i++) {
                long k = in.readLong();
                Furnace fu = new Furnace();
                readStack(in, fu.input); readStack(in, fu.fuel); readStack(in, fu.output);
                furnaces.put(k, fu);
            }
            chests.clear();
            int nc = in.readInt();
            for (int i = 0; i < nc; i++) {
                long k = in.readLong();
                Container c = new Container();
                for (ItemStack s : c.slots) readStack(in, s);
                chests.put(k, c);
            }
            entityManager.readSave(in);
            hasSave = true;
            System.out.println("[INFO] Save found (dim " + savedDim + ")");
        } catch (IOException e) { System.out.println("[WARN] load failed: " + e.getMessage()); }
    }

    private void screenshot(String path) {
        int w = window.width(), h = window.height();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf);
        ByteBuffer flipped = BufferUtils.createByteBuffer(w * h * 3);
        for (int y = 0; y < h; y++) { int src = (h - 1 - y) * w * 3; for (int x = 0; x < w * 3; x++) flipped.put(y * w * 3 + x, buf.get(src + x)); }
        org.lwjgl.stb.STBImageWrite.stbi_write_png(path, w, h, 3, flipped, w * 3);
        System.out.println("[INFO] Wrote screenshot " + path);
    }
}
