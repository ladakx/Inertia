cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Handle JNI paths manually if provided
if(DEFINED JAVA_INCLUDE_PATH AND DEFINED JAVA_INCLUDE_PATH2)
    message(STATUS "Using provided JNI paths:")
    message(STATUS "  JAVA_INCLUDE_PATH: ${JAVA_INCLUDE_PATH}")
    message(STATUS "  JAVA_INCLUDE_PATH2: ${JAVA_INCLUDE_PATH2}")

    set(JNI_INCLUDE_DIRS ${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH2})
    set(JNI_FOUND TRUE)
else()
    # Fallback to automatic detection
    find_package(JNI REQUIRED)
endif()

add_subdirectory(../Jolt/Build jolt-build)

add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# Static linking configuration
if(UNIX AND NOT APPLE)
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
elseif(APPLE)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++")
endif()

get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(inertia_native PRIVATE Jolt)