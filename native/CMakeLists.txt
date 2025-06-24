cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_subdirectory(../Jolt/Build jolt-build)

add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# Ми видалили звідси платформо-специфічні прапори
# set(CMAKE_EXE_LINKER_FLAGS ...)
# set(CMAKE_SHARED_LINKER_FLAGS ...)

get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(inertia_native PRIVATE Jolt)