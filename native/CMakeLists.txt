cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(../Jolt jolt-build)

# Створюємо нашу бібліотеку
add_library(inertia_native SHARED src/JNI_Bridge.cpp)

get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIR}
    ${JNI_INCLUDE_DIR2}
)

target_link_libraries(inertia_native PRIVATE Jolt)