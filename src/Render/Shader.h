#pragma once
#include <string>
#include <glm/glm.hpp>

namespace mc {

// Compiles and links a vertex+fragment program and caches uniform lookups.
class Shader {
public:
    Shader() = default;
    ~Shader();

    // Build from GLSL source strings.
    bool compile(const std::string& vertexSrc, const std::string& fragmentSrc);
    // Build from files under the asset shaders directory.
    bool loadFromFiles(const std::string& vertPath, const std::string& fragPath);

    void use() const;
    unsigned id() const { return program_; }

    void set(const char* name, int v) const;
    void set(const char* name, float v) const;
    void set(const char* name, const glm::vec2& v) const;
    void set(const char* name, const glm::vec3& v) const;
    void set(const char* name, const glm::vec4& v) const;
    void set(const char* name, const glm::mat4& v) const;

private:
    static unsigned compileStage(unsigned type, const std::string& src);
    int location(const char* name) const;
    unsigned program_ = 0;
};

// Reads a file from the asset directory (MINICRAFT_ASSET_DIR or ./assets).
std::string readAssetFile(const std::string& relPath);

} // namespace mc
