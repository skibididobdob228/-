package com.minicraft.audio;

import com.minicraft.world.Blocks;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Procedurally-synthesised sound effects (dig, step, place, hurt, …) played
 * through OpenAL. All audio is generated in code — no sample files are used,
 * so nothing is copied from Minecraft. Silently disables itself when no audio
 * device is available (e.g. headless servers).
 */
public class Sound {
    private static final int RATE = 44100;
    private long device, context;
    private boolean enabled;
    private final Random rng = new Random();

    private int[] srcPool;
    private int nextSrc;

    // Category buffers (a few variants each for variety).
    private int[] digStone, digWood, digDirt, digSand, digGlass, digWool;
    private int[] stepDirt, stepStone, stepWood, stepSand;
    private int place, breakB, jump, hurt, splash, click, levelUp;

    public void init() {
        try {
            device = alcOpenDevice((java.nio.ByteBuffer) null);
            if (device == 0L) { System.out.println("[INFO] No audio device; sound disabled."); return; }
            ALCCapabilities caps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (java.nio.IntBuffer) null);
            if (context == 0L) { return; }
            alcMakeContextCurrent(context);
            AL.createCapabilities(caps);

            srcPool = new int[24];
            for (int i = 0; i < srcPool.length; i++) srcPool[i] = alGenSources();

            digStone = new int[]{ dig(0.18f, 0.06f, 0.55f), dig(0.16f, 0.05f, 0.6f) };
            digWood  = new int[]{ dig(0.16f, 0.12f, 0.5f), dig(0.15f, 0.14f, 0.55f) };
            digDirt  = new int[]{ dig(0.13f, 0.30f, 0.4f), dig(0.12f, 0.34f, 0.42f) };
            digSand  = new int[]{ dig(0.14f, 0.55f, 0.35f), dig(0.13f, 0.6f, 0.33f) };
            digGlass = new int[]{ tone(1400, 0.12f, 0.5f, true), tone(1800, 0.10f, 0.45f, true) };
            digWool  = new int[]{ dig(0.12f, 0.18f, 0.3f) };

            stepDirt  = new int[]{ dig(0.07f, 0.32f, 0.22f), dig(0.06f, 0.36f, 0.20f) };
            stepStone = new int[]{ dig(0.06f, 0.07f, 0.22f), dig(0.07f, 0.06f, 0.24f) };
            stepWood  = new int[]{ dig(0.06f, 0.13f, 0.22f), dig(0.07f, 0.14f, 0.20f) };
            stepSand  = new int[]{ dig(0.07f, 0.55f, 0.18f) };

            place  = dig(0.12f, 0.2f, 0.45f);
            breakB = dig(0.22f, 0.25f, 0.6f);
            jump   = dig(0.05f, 0.4f, 0.15f);
            hurt   = sweep(420, 180, 0.22f, 0.5f);
            splash = dig(0.25f, 0.7f, 0.4f);
            click  = tone(900, 0.04f, 0.3f, false);
            levelUp = sweep(500, 1000, 0.3f, 0.4f);

            enabled = true;
            System.out.println("[INFO] Audio initialised (procedural).");
        } catch (Throwable t) {
            System.out.println("[INFO] Audio unavailable: " + t.getMessage());
            enabled = false;
        }
    }

    // --- Synthesis ---

    private int buffer(short[] data) {
        int b = alGenBuffers();
        ShortBuffer sb = BufferUtils.createShortBuffer(data.length);
        sb.put(data).flip();
        alBufferData(b, AL_FORMAT_MONO16, sb, RATE);
        return b;
    }

    /** Filtered noise burst. lowpass: 0=very muffled, 1=bright. */
    private int dig(float dur, float lowpass, float amp) {
        int n = (int) (dur * RATE);
        short[] d = new short[n];
        float y = 0, a = 0.02f + lowpass * 0.85f;
        for (int i = 0; i < n; i++) {
            float white = rng.nextFloat() * 2 - 1;
            y += a * (white - y);
            float env = (float) Math.pow(1.0 - i / (double) n, 1.8); // decay
            float atk = Math.min(1f, i / (RATE * 0.004f));
            d[i] = (short) (y * env * atk * amp * 32000);
        }
        return buffer(d);
    }

    /** Tone (sine), optional bright noise overlay (for clinks/clicks). */
    private int tone(float freq, float dur, float amp, boolean noisy) {
        int n = (int) (dur * RATE);
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            float s = (float) Math.sin(2 * Math.PI * freq * t);
            if (noisy) s = 0.6f * s + 0.4f * (rng.nextFloat() * 2 - 1);
            float env = (float) Math.pow(1.0 - i / (double) n, 1.5);
            d[i] = (short) (s * env * amp * 30000);
        }
        return buffer(d);
    }

    /** Frequency sweep (for hurt / level-up cues). */
    private int sweep(float f0, float f1, float dur, float amp) {
        int n = (int) (dur * RATE);
        short[] d = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float f = f0 + (f1 - f0) * (i / (float) n);
            phase += 2 * Math.PI * f / RATE;
            float s = (float) Math.sin(phase);
            float env = (float) Math.pow(1.0 - i / (double) n, 1.2);
            d[i] = (short) (s * env * amp * 30000);
        }
        return buffer(d);
    }

    // --- Playback ---

    private void play(int buf, float gain, float pitch) {
        if (!enabled || buf == 0) return;
        int src = srcPool[nextSrc];
        nextSrc = (nextSrc + 1) % srcPool.length;
        if (alGetSourcei(src, AL_SOURCE_STATE) == AL_PLAYING) alSourceStop(src);
        alSourcei(src, AL_BUFFER, buf);
        alSourcef(src, AL_GAIN, gain);
        alSourcef(src, AL_PITCH, pitch);
        alSourcePlay(src);
    }

    private int pick(int[] arr) { return arr[rng.nextInt(arr.length)]; }
    private float jitter() { return 0.9f + rng.nextFloat() * 0.2f; }

    private int[] digFor(int block) {
        return switch (category(block)) {
            case 1 -> digStone; case 2 -> digWood; case 3 -> digSand;
            case 4 -> digGlass; case 5 -> digWool; default -> digDirt;
        };
    }
    private int[] stepFor(int block) {
        return switch (category(block)) {
            case 1 -> stepStone; case 2 -> stepWood; case 3 -> stepSand; default -> stepDirt;
        };
    }

    /** 0 dirt/grass, 1 stone, 2 wood, 3 sand, 4 glass, 5 wool. */
    private int category(int b) {
        switch (b) {
            case Blocks.STONE: case Blocks.COBBLE: case Blocks.BEDROCK: case Blocks.GRANITE:
            case Blocks.DIORITE: case Blocks.ANDESITE: case Blocks.STONE_BRICKS: case Blocks.MOSSY_COBBLE:
            case Blocks.SANDSTONE: case Blocks.OBSIDIAN: case Blocks.NETHERRACK: case Blocks.NETHER_BRICK:
            case Blocks.QUARTZ: case Blocks.END_STONE: case Blocks.BRICK: case Blocks.FURNACE:
            case Blocks.COAL_ORE: case Blocks.IRON_ORE: case Blocks.GOLD_ORE: case Blocks.DIAMOND_ORE:
            case Blocks.REDSTONE_ORE: case Blocks.LAPIS_ORE: case Blocks.EMERALD_ORE: case Blocks.MAGMA:
            case Blocks.GLOWSTONE: return 1;
            case Blocks.LOG: case Blocks.SPRUCE_LOG: case Blocks.BIRCH_LOG: case Blocks.PLANKS:
            case Blocks.SPRUCE_PLANKS: case Blocks.BIRCH_PLANKS: case Blocks.CRAFTING_TABLE:
            case Blocks.CHEST: case Blocks.BOOKSHELF: return 2;
            case Blocks.SAND: case Blocks.GRAVEL: case Blocks.SOUL_SAND: return 3;
            case Blocks.GLASS: case Blocks.ICE: return 4;
            case Blocks.WOOL_WHITE: case Blocks.WOOL_RED: case Blocks.WOOL_BLUE: case Blocks.WOOL_GREEN:
            case Blocks.WOOL_YELLOW: case Blocks.WOOL_BLACK: case Blocks.LEAVES: return 5;
            default: return 0;
        }
    }

    public void playDig(int block) { play(pick(digFor(block)), 0.5f, jitter()); }
    public void playStep(int block) { play(pick(stepFor(block)), 0.28f, jitter()); }
    public void playPlace(int block) { play(place, 0.5f, jitter()); }
    public void playBreak(int block) { play(breakB, 0.55f, jitter()); }
    public void playJump() { play(jump, 0.25f, jitter()); }
    public void playHurt() { play(hurt, 0.5f, jitter()); }
    public void playSplash() { play(splash, 0.4f, jitter()); }
    public void playClick() { play(click, 0.4f, 1f); }
    public void playLevelUp() { play(levelUp, 0.5f, 1f); }

    public void shutdown() {
        if (!enabled) return;
        if (context != 0) alcDestroyContext(context);
        if (device != 0) alcCloseDevice(device);
    }
}
