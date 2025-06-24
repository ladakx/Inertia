cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_subdirectory(../Jolt/Build jolt-build)

add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# --- ОСНОВНЕ ВИПРАВЛЕННЯ ---
# Додаємо прапори для статичного лінкування стандартних бібліотек C++.
# Це робить нашу .so бібліотеку самодостатньою і вирішує проблеми
# з різними версіями системних бібліотек.
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")

get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(inertia_native PRIVATE Jolt)