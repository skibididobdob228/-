#pragma once
#include <string>
#include <cstdint>
#include <vector>

namespace mc {

// Writes an RGB image as a PNG (uncompressed/stored deflate, no extra deps).
bool writePNG(const std::string& path, int width, int height, const std::vector<uint8_t>& rgb);

// Grabs the current GL framebuffer and saves it as a PNG (flips vertically).
bool captureScreenshot(const std::string& path, int width, int height);

} // namespace mc
