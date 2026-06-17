package com.minicraft.player;

/** Per-frame input snapshot filled by the Game from GLFW. */
public class InputState {
    public boolean forward, back, left, right;
    public boolean jump, sneak, sprint;
    public boolean jumpPressed;   // edge for fly toggle
}
