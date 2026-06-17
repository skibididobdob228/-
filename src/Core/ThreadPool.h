#pragma once
#include <thread>
#include <vector>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <atomic>

namespace mc {

// Minimal fixed-size thread pool used to generate and mesh chunks off the
// main (render) thread so the game does not stall while exploring.
class ThreadPool {
public:
    explicit ThreadPool(unsigned count = 0) {
        if (count == 0) {
            count = std::thread::hardware_concurrency();
            if (count > 1) count -= 1;       // leave a core for the render thread
            if (count == 0) count = 1;
        }
        for (unsigned i = 0; i < count; ++i)
            workers_.emplace_back([this] { workerLoop(); });
    }

    ~ThreadPool() {
        {
            std::unique_lock<std::mutex> lock(mutex_);
            stop_ = true;
        }
        cv_.notify_all();
        for (auto& t : workers_) if (t.joinable()) t.join();
    }

    void enqueue(std::function<void()> job) {
        {
            std::unique_lock<std::mutex> lock(mutex_);
            jobs_.push(std::move(job));
        }
        cv_.notify_one();
    }

    size_t pending() {
        std::unique_lock<std::mutex> lock(mutex_);
        return jobs_.size();
    }

private:
    void workerLoop() {
        for (;;) {
            std::function<void()> job;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                cv_.wait(lock, [this] { return stop_ || !jobs_.empty(); });
                if (stop_ && jobs_.empty()) return;
                job = std::move(jobs_.front());
                jobs_.pop();
            }
            job();
        }
    }

    std::vector<std::thread> workers_;
    std::queue<std::function<void()>> jobs_;
    std::mutex mutex_;
    std::condition_variable cv_;
    bool stop_ = false;
};

} // namespace mc
