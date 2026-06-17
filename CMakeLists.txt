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

# GLFW / GLEW / GLM resolved via pkg-config (Linux) with fallbacks.
find_package(PkgConfig)
if(PkgConfig_FOUND)
    pkg_check_modules(GLFW glfw3)
    pkg_check_modules(GLEW glew)
endif()

find_package(glm QUIET)

file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS
    "${CMAKE_SOURCE_DIR}/src/*.cpp")

add_executable(minicraft ${SOURCES})

target_include_directories(minicraft PRIVATE
    "${CMAKE_SOURCE_DIR}/src"
    ${GLFW_INCLUDE_DIRS}
    ${GLEW_INCLUDE_DIRS})

target_link_libraries(minicraft PRIVATE
    OpenGL::GL
    ${GLFW_LIBRARIES}
    ${GLEW_LIBRARIES})

if(GLFW_LIBRARY_DIRS)
    target_link_directories(minicraft PRIVATE ${GLFW_LIBRARY_DIRS})
endif()
if(GLEW_LIBRARY_DIRS)
    target_link_directories(minicraft PRIVATE ${GLEW_LIBRARY_DIRS})
endif()

if(TARGET glm::glm)
    target_link_libraries(minicraft PRIVATE glm::glm)
endif()

find_package(Threads REQUIRED)
target_link_libraries(minicraft PRIVATE Threads::Threads)

# Make the asset directory discoverable at runtime from the build tree.
target_compile_definitions(minicraft PRIVATE
    MINICRAFT_ASSET_DIR="${CMAKE_SOURCE_DIR}/assets")
