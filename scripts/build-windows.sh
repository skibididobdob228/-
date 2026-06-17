#!/usr/bin/env bash
#
# Cross-compiles a self-contained Windows x64 build of MiniCraft from Linux
# using MinGW-w64, then packages it into MiniCraft-Windows-x64.zip.
#
# GLFW is linked from its prebuilt MinGW static lib and GLEW is compiled from
# source into the executable, so the resulting .exe needs no bundled DLLs
# (only standard Windows system libraries).
#
# Requirements (Debian/Ubuntu):
#   sudo apt-get install -y g++-mingw-w64-x86-64 mingw-w64-tools \
#                           libglm-dev curl unzip zip
#
# Usage:  scripts/build-windows.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS="$ROOT/build-win/deps"
OUT="$ROOT/build-win"
STAGE="$OUT/MiniCraft-Windows"

CXX=x86_64-w64-mingw32-g++-posix
CC=x86_64-w64-mingw32-gcc-posix
command -v "$CXX" >/dev/null || { echo "MinGW-w64 (posix) not found"; exit 1; }

GLFW_VER=3.4
GLEW_VER=2.2.0

mkdir -p "$DEPS"

# --- Fetch GLFW prebuilt (MinGW static lib) ---
if [ ! -d "$DEPS/glfw-$GLFW_VER.bin.WIN64" ]; then
    echo ">> downloading GLFW $GLFW_VER"
    curl -sSL -o "$DEPS/glfw.zip" \
        "https://github.com/glfw/glfw/releases/download/$GLFW_VER/glfw-$GLFW_VER.bin.WIN64.zip"
    unzip -q -o "$DEPS/glfw.zip" -d "$DEPS"
fi
GLFW="$DEPS/glfw-$GLFW_VER.bin.WIN64"

# --- Fetch GLEW source ---
if [ ! -d "$DEPS/glew-$GLEW_VER" ]; then
    echo ">> downloading GLEW $GLEW_VER"
    curl -sSL -o "$DEPS/glew.zip" \
        "https://github.com/nigels-com/glew/releases/download/glew-$GLEW_VER/glew-$GLEW_VER.zip"
    unzip -q -o "$DEPS/glew.zip" -d "$DEPS"
fi
GLEW="$DEPS/glew-$GLEW_VER"

# --- Stage GLM headers in isolation (avoid pulling host /usr/include) ---
GLM="$DEPS/glminc"
if [ ! -d "$GLM/glm" ]; then
    if [ -d /usr/include/glm ]; then
        mkdir -p "$GLM"; cp -r /usr/include/glm "$GLM/glm"
    else
        echo "GLM headers not found at /usr/include/glm (install libglm-dev)"; exit 1
    fi
fi

# --- Compile ---
echo ">> compiling glew.c"
"$CC" -O2 -DGLEW_STATIC -c "$GLEW/src/glew.c" -I"$GLEW/include" -o "$OUT/glew.o"

echo ">> compiling MiniCraft"
mapfile -t SRCS < <(find "$ROOT/src" -name '*.cpp')
"$CXX" -std=c++17 -O2 -DGLEW_STATIC \
    -I"$ROOT/src" -I"$GLM" -I"$GLFW/include" -I"$GLEW/include" \
    "${SRCS[@]}" "$OUT/glew.o" \
    -L"$GLFW/lib-mingw-w64" -lglfw3 -lopengl32 -lgdi32 -lwinmm \
    -static -static-libgcc -static-libstdc++ \
    -o "$OUT/minicraft.exe"

# --- Package ---
echo ">> packaging"
rm -rf "$STAGE"
mkdir -p "$STAGE/assets/shaders"
cp "$OUT/minicraft.exe" "$STAGE/"
cp "$ROOT"/assets/shaders/*.vert "$ROOT"/assets/shaders/*.frag "$STAGE/assets/shaders/"
cp "$ROOT/dist/windows-README.txt" "$STAGE/README.txt"

ZIP="$ROOT/MiniCraft-Windows-x64.zip"
rm -f "$ZIP"
( cd "$OUT" && zip -r -q "$ZIP" "MiniCraft-Windows" )

echo ">> done: $ZIP"
unzip -l "$ZIP"
