package com.minicraft;

public class Main {
    public static void main(String[] args) {
        int width = 1280, height = 720;
        if (args.length >= 2) {
            try {
                width = Math.max(320, Integer.parseInt(args[0]));
                height = Math.max(240, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {}
        }
        new Game(width, height).run();
    }
}
