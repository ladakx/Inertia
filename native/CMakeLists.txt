cmake_minimum_required(VERSION 3.10)
project(InertiaNative)

# Знайти бібліотеку JNI
find_package(JNI REQUIRED)

# Вказуємо, що ми створюємо динамічну бібліотеку (SHARED)
add_library(inertia SHARED src/JNI_Bridge.cpp)

# Вказуємо, де шукати заголовкові файли JNI
target_include_directories(inertia PUBLIC ${JNI_INCLUDE_DIRS})

# На macOS назва файлу за замовчуванням буде libinertia.dylib
# Ми можемо встановити префікс в "" для отримання inertia.dylib, але стандартний варіант кращий
set_target_properties(inertia PROPERTIES
    PREFIX "" # За бажанням, щоб назва була inertia.dylib, а не libinertia.dylib
)
