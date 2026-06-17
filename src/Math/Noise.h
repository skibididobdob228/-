#pragma once
#include <cstdint>
#include <array>

namespace mc {

// Classic Perlin noise (2D + 3D) plus fractal Brownian motion helpers.
// Seeded permutation table so worlds are reproducible.
class Noise {
public:
    explicit Noise(uint32_t seed = 1337);

    // Single-octave Perlin, output in roughly [-1, 1].
    float perlin2(float x, float y) const;
    float perlin3(float x, float y, float z) const;

    // Fractal sum of octaves. `lacunarity` scales frequency, `gain` scales amplitude.
    float fbm2(float x, float y, int octaves, float lacunarity = 2.0f, float gain = 0.5f) const;
    float fbm3(float x, float y, float z, int octaves, float lacunarity = 2.0f, float gain = 0.5f) const;

private:
    std::array<int, 512> p_;
    static float fade(float t);
    static float lerp(float a, float b, float t);
    static float grad(int hash, float x, float y, float z);
};

} // namespace mc
