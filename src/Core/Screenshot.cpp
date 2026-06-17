#include "Core/Screenshot.h"
#include <GL/glew.h>
#include <fstream>
#include <cstring>

namespace mc {

namespace {
uint32_t crcTable[256];
bool crcReady = false;
void initCrc() {
    for (uint32_t n = 0; n < 256; ++n) {
        uint32_t c = n;
        for (int k = 0; k < 8; ++k) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
        crcTable[n] = c;
    }
    crcReady = true;
}
uint32_t crc32(const uint8_t* data, size_t len, uint32_t crc = 0xFFFFFFFFu) {
    if (!crcReady) initCrc();
    for (size_t i = 0; i < len; ++i) crc = crcTable[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return crc;
}
uint32_t adler32(const uint8_t* data, size_t len) {
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < len; ++i) { a = (a + data[i]) % 65521; b = (b + a) % 65521; }
    return (b << 16) | a;
}
void put32(std::vector<uint8_t>& v, uint32_t x) {
    v.push_back((x >> 24) & 0xFF); v.push_back((x >> 16) & 0xFF);
    v.push_back((x >> 8) & 0xFF);  v.push_back(x & 0xFF);
}
void chunk(std::vector<uint8_t>& out, const char* type, const std::vector<uint8_t>& data) {
    put32(out, (uint32_t)data.size());
    std::vector<uint8_t> td(type, type + 4);
    td.insert(td.end(), data.begin(), data.end());
    out.insert(out.end(), td.begin(), td.end());
    put32(out, crc32(td.data(), td.size()) ^ 0xFFFFFFFFu);
}
} // namespace

bool writePNG(const std::string& path, int width, int height, const std::vector<uint8_t>& rgb) {
    if ((int)rgb.size() < width * height * 3) return false;

    // Raw image data with per-scanline filter byte 0.
    std::vector<uint8_t> raw;
    raw.reserve((size_t)height * (width * 3 + 1));
    for (int y = 0; y < height; ++y) {
        raw.push_back(0);
        const uint8_t* row = &rgb[(size_t)y * width * 3];
        raw.insert(raw.end(), row, row + width * 3);
    }

    // zlib stream with stored (uncompressed) deflate blocks.
    std::vector<uint8_t> zlib;
    zlib.push_back(0x78); zlib.push_back(0x01);
    size_t pos = 0;
    while (pos < raw.size()) {
        size_t blk = std::min<size_t>(65535, raw.size() - pos);
        bool last = (pos + blk >= raw.size());
        zlib.push_back(last ? 1 : 0);
        zlib.push_back(blk & 0xFF); zlib.push_back((blk >> 8) & 0xFF);
        uint16_t nlen = ~(uint16_t)blk;
        zlib.push_back(nlen & 0xFF); zlib.push_back((nlen >> 8) & 0xFF);
        zlib.insert(zlib.end(), raw.begin() + pos, raw.begin() + pos + blk);
        pos += blk;
    }
    uint32_t ad = adler32(raw.data(), raw.size());
    put32(zlib, ad);

    std::vector<uint8_t> out;
    const uint8_t sig[8] = {137, 80, 78, 71, 13, 10, 26, 10};
    out.insert(out.end(), sig, sig + 8);

    std::vector<uint8_t> ihdr;
    put32(ihdr, width); put32(ihdr, height);
    ihdr.push_back(8); ihdr.push_back(2); // 8-bit, RGB
    ihdr.push_back(0); ihdr.push_back(0); ihdr.push_back(0);
    chunk(out, "IHDR", ihdr);
    chunk(out, "IDAT", zlib);
    chunk(out, "IEND", {});

    std::ofstream f(path, std::ios::binary);
    if (!f) return false;
    f.write((const char*)out.data(), out.size());
    return true;
}

bool captureScreenshot(const std::string& path, int width, int height) {
    std::vector<uint8_t> buf((size_t)width * height * 3);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buf.data());
    // Flip vertically (GL origin is bottom-left).
    std::vector<uint8_t> flipped((size_t)width * height * 3);
    for (int y = 0; y < height; ++y)
        std::memcpy(&flipped[(size_t)y * width * 3],
                    &buf[(size_t)(height - 1 - y) * width * 3], width * 3);
    return writePNG(path, width, height, flipped);
}

} // namespace mc
