#include "Game.h"
#include "Core/Logger.h"
#include <cstdlib>
#include <exception>

int main(int argc, char** argv) {
    int width = 1280, height = 720;
    if (argc >= 3) {
        width = std::atoi(argv[1]);
        height = std::atoi(argv[2]);
        if (width < 320) width = 320;
        if (height < 240) height = 240;
    }
    try {
        mc::Game game(width, height);
        game.run();
    } catch (const std::exception& e) {
        LOG_ERROR("Fatal: %s", e.what());
        return 1;
    }
    return 0;
}
