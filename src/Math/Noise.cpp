#include "Math/Noise.h"
#include <numeric>
#include <random>
#include <algorithm>
#include <cmath>

namespace mc {

Noise::Noise(uint32_t seed) {
    std::array<int, 256> perm;
    std::iota(perm.begin(), perm.end(), 0);
    std::mt19937 rng(seed);
    std::shuffle(perm.begin(), perm.end(), rng);
    for (int i = 0; i < 512; ++i) p_[i] = perm[i & 255];
}

float Noise::fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
float Noise::lerp(float a, float b, float t) { return a + t * (b - a); }

float Noise::grad(int hash, float x, float y, float z) {
    int h = hash & 15;
    float u = h < 8 ? x : y;
    float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
}

float Noise::perlin3(float x, float y, float z) const {
    int X = (int)std::floor(x) & 255;
    int Y = (int)std::floor(y) & 255;
    int Z = (int)std::floor(z) & 255;
    x -= std::floor(x);
    y -= std::floor(y);
    z -= std::floor(z);
    float u = fade(x), v = fade(y), w = fade(z);

    int A  = p_[X] + Y,   AA = p_[A] + Z,   AB = p_[A + 1] + Z;
    int B  = p_[X + 1] + Y, BA = p_[B] + Z, BB = p_[B + 1] + Z;

    float res = lerp(
        lerp(lerp(grad(p_[AA], x, y, z),     grad(p_[BA], x - 1, y, z), u),
             lerp(grad(p_[AB], x, y - 1, z), grad(p_[BB], x - 1, y - 1, z), u), v),
        lerp(lerp(grad(p_[AA + 1], x, y, z - 1),     grad(p_[BA + 1], x - 1, y, z - 1), u),
             lerp(grad(p_[AB + 1], x, y - 1, z - 1), grad(p_[BB + 1], x - 1, y - 1, z - 1), u), v),
        w);
    return res;
}

float Noise::perlin2(float x, float y) const {
    return perlin3(x, y, 0.0f);
}

float Noise::fbm2(float x, float y, int octaves, float lacunarity, float gain) const {
    float sum = 0.0f, amp = 1.0f, freq = 1.0f, norm = 0.0f;
    for (int i = 0; i < octaves; ++i) {
        sum += amp * perlin2(x * freq, y * freq);
        norm += amp;
        amp *= gain;
        freq *= lacunarity;
    }
    return norm > 0 ? sum / norm : 0.0f;
}

float Noise::fbm3(float x, float y, float z, int octaves, float lacunarity, float gain) const {
    float sum = 0.0f, amp = 1.0f, freq = 1.0f, norm = 0.0f;
    for (int i = 0; i < octaves; ++i) {
        sum += amp * perlin3(x * freq, y * freq, z * freq);
        norm += amp;
        amp *= gain;
        freq *= lacunarity;
    }
    return norm > 0 ? sum / norm : 0.0f;
}

} // namespace mc
