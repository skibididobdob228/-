#pragma once
#include <cstdio>
#include <ctime>
#include <mutex>

// Tiny thread-safe logger. Stands in for spdlog without the dependency.
namespace mc {

enum class LogLevel { Trace, Info, Warn, Error };

class Logger {
public:
    static Logger& get() {
        static Logger inst;
        return inst;
    }

    template <typename... Args>
    void log(LogLevel level, const char* fmt, Args... args) {
        std::lock_guard<std::mutex> lock(mutex_);
        std::time_t t = std::time(nullptr);
        char tbuf[16];
        std::strftime(tbuf, sizeof(tbuf), "%H:%M:%S", std::localtime(&t));
        std::fprintf(stderr, "[%s] %-5s ", tbuf, tag(level));
        std::fprintf(stderr, fmt, args...);
        std::fprintf(stderr, "\n");
    }

private:
    static const char* tag(LogLevel l) {
        switch (l) {
            case LogLevel::Trace: return "TRACE";
            case LogLevel::Info:  return "INFO";
            case LogLevel::Warn:  return "WARN";
            case LogLevel::Error: return "ERROR";
        }
        return "?";
    }
    std::mutex mutex_;
};

} // namespace mc

#define LOG_INFO(...)  ::mc::Logger::get().log(::mc::LogLevel::Info,  __VA_ARGS__)
#define LOG_WARN(...)  ::mc::Logger::get().log(::mc::LogLevel::Warn,  __VA_ARGS__)
#define LOG_ERROR(...) ::mc::Logger::get().log(::mc::LogLevel::Error, __VA_ARGS__)
#define LOG_TRACE(...) ::mc::Logger::get().log(::mc::LogLevel::Trace, __VA_ARGS__)
