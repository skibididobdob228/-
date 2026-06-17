#include "Render/Shader.h"
#include "Core/Logger.h"
#include <GL/glew.h>
#include <glm/gtc/type_ptr.hpp>
#include <fstream>
#include <sstream>
#include <vector>

namespace mc {

std::string readAssetFile(const std::string& relPath) {
    std::vector<std::string> bases;
#ifdef MINICRAFT_ASSET_DIR
    bases.push_back(MINICRAFT_ASSET_DIR "/");
#endif
    bases.push_back("assets/");
    bases.push_back("../assets/");
    bases.push_back("./");
    for (const auto& base : bases) {
        std::ifstream f(base + relPath, std::ios::binary);
        if (f) {
            std::stringstream ss;
            ss << f.rdbuf();
            return ss.str();
        }
    }
    LOG_ERROR("Asset not found: %s", relPath.c_str());
    return {};
}

Shader::~Shader() {
    if (program_) glDeleteProgram(program_);
}

unsigned Shader::compileStage(unsigned type, const std::string& src) {
    unsigned s = glCreateShader(type);
    const char* c = src.c_str();
    glShaderSource(s, 1, &c, nullptr);
    glCompileShader(s);
    int ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        LOG_ERROR("Shader compile error: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

bool Shader::compile(const std::string& vertexSrc, const std::string& fragmentSrc) {
    unsigned vs = compileStage(GL_VERTEX_SHADER, vertexSrc);
    unsigned fs = compileStage(GL_FRAGMENT_SHADER, fragmentSrc);
    if (!vs || !fs) return false;

    program_ = glCreateProgram();
    glAttachShader(program_, vs);
    glAttachShader(program_, fs);
    glLinkProgram(program_);
    glDeleteShader(vs);
    glDeleteShader(fs);

    int ok = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetProgramInfoLog(program_, sizeof(log), nullptr, log);
        LOG_ERROR("Program link error: %s", log);
        return false;
    }
    return true;
}

bool Shader::loadFromFiles(const std::string& vertPath, const std::string& fragPath) {
    std::string v = readAssetFile(vertPath);
    std::string f = readAssetFile(fragPath);
    if (v.empty() || f.empty()) return false;
    return compile(v, f);
}

void Shader::use() const { glUseProgram(program_); }
int Shader::location(const char* name) const { return glGetUniformLocation(program_, name); }

void Shader::set(const char* n, int v) const { glUniform1i(location(n), v); }
void Shader::set(const char* n, float v) const { glUniform1f(location(n), v); }
void Shader::set(const char* n, const glm::vec2& v) const { glUniform2fv(location(n), 1, glm::value_ptr(v)); }
void Shader::set(const char* n, const glm::vec3& v) const { glUniform3fv(location(n), 1, glm::value_ptr(v)); }
void Shader::set(const char* n, const glm::vec4& v) const { glUniform4fv(location(n), 1, glm::value_ptr(v)); }
void Shader::set(const char* n, const glm::mat4& v) const { glUniformMatrix4fv(location(n), 1, GL_FALSE, glm::value_ptr(v)); }

} // namespace mc
