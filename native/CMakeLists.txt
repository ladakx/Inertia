cmake_minimum_required(VERSION 3.16)
project(inertia_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# THIS IS THE FIX:
# We are now telling CMake to look for the 'Jolt' directory
# one level up from the current directory (native/).
add_subdirectory(../Jolt)

# Створюємо нашу бібліотеку
add_library(inertia_native SHARED src/JNI_Bridge.cpp)

# ОТРИМУЄМО ШЛЯХИ НАПРЯМУ ВІД JOLT:
# Це найнадійніший спосіб. Ми запитуємо у цілі "Jolt",
# які шляхи до заголовків вона використовує.
get_target_property(JOLT_INCLUDE_DIRS Jolt INTERFACE_INCLUDE_DIRECTORIES)

# Додаємо всі необхідні шляхи до нашої бібліотеки
target_include_directories(inertia_native PUBLIC
    ${JOLT_INCLUDE_DIRS}
    ${JNI_INCLUDE_DIR}
    ${JNI_INCLUDE_DIR2}
)

# Підключаємо скомпільовану бібліотеку Jolt
target_link_libraries(inertia_native PRIVATE Jolt)