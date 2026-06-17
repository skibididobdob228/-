cmake_minimum_required(VERSION 3.16)
project(MiniCraft VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_CXX_FLAGS_RELEASE "-O2")

find_package(OpenGL REQUIRED)

# Resolve GLFW / GLEW / GLM. Prefer CMake config packages (e.g. vcpkg on
# Windows); fall back to pkg-config on Linux.
find_package(glfw3 CONFIG QUIET)
find_package(GLEW QUIET)
find_package(glm QUIET)

find_package(PkgConfig)
if(PkgConfig_FOUND)
    if(NOT TARGET glfw)
        pkg_check_modules(GLFW glfw3)
    endif()
    if(NOT TARGET GLEW::GLEW)
        pkg_check_modules(GLEW glew)
    endif()
endif()

file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS
    "${CMAKE_SOURCE_DIR}/src/*.cpp")

add_executable(minicraft ${SOURCES})

target_include_directories(minicraft PRIVATE
    "${CMAKE_SOURCE_DIR}/src"
    ${GLFW_INCLUDE_DIRS}
    ${GLEW_INCLUDE_DIRS})

target_link_libraries(minicraft PRIVATE OpenGL::GL)

if(TARGET glfw)
    target_link_libraries(minicraft PRIVATE glfw)
else()
    target_link_libraries(minicraft PRIVATE ${GLFW_LIBRARIES})
    if(GLFW_LIBRARY_DIRS)
        target_link_directories(minicraft PRIVATE ${GLFW_LIBRARY_DIRS})
    endif()
endif()

if(TARGET GLEW::GLEW)
    target_link_libraries(minicraft PRIVATE GLEW::GLEW)
else()
    target_link_libraries(minicraft PRIVATE ${GLEW_LIBRARIES})
    if(GLEW_LIBRARY_DIRS)
        target_link_directories(minicraft PRIVATE ${GLEW_LIBRARY_DIRS})
    endif()
endif()

if(TARGET glm::glm)
    target_link_libraries(minicraft PRIVATE glm::glm)
endif()

find_package(Threads REQUIRED)
target_link_libraries(minicraft PRIVATE Threads::Threads)

# On Linux, point at the in-tree assets dir so the binary runs from anywhere.
# On Windows we ship assets next to the .exe and load them relatively.
if(NOT WIN32)
    target_compile_definitions(minicraft PRIVATE
        MINICRAFT_ASSET_DIR="${CMAKE_SOURCE_DIR}/assets")
endif()
