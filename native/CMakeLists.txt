cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(../Jolt/Build jolt-build)

# Створюємо нашу бібліотеку
add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# Отримуємо шляхи до заголовків (include) напряму від Jolt
get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

# Додаємо всі необхідні шляхи до нашої бібліотеки
target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIR}
    ${JNI_INCLUDE_DIR2}
)

# Підключаємо (лінкуємо) скомпільовану бібліотеку Jolt до нашої
target_link_libraries(inertia_native PRIVATE Jolt)