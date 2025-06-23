cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- ОСНОВНЕ ВИПРАВЛЕННЯ ---
# Використовуємо стандартний модуль CMake для пошуку JNI.
# Це автоматично знайде JDK на будь-якій системі (Windows, Linux, macOS).
find_package(JNI REQUIRED)

# Додаємо підпроект Jolt
add_subdirectory(../Jolt/Build jolt-build)

# Створюємо нашу бібліотеку
add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# Отримуємо шляхи до заголовків Jolt
get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

# Додаємо шляхи до заголовків до нашої бібліотеки:
# 1. Jolt
# 2. JNI (шляхи, знайдені автоматично через find_package)
target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIRS}
)

# Лінкуємо нашу бібліотеку з Jolt
target_link_libraries(inertia_native PRIVATE Jolt)