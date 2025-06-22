// com/inertia/core/native/JNIBridge.java

package com.inertia.core.ntve;

public class JNIBridge {

    // Цей блок виконається один раз при першому завантаженні класу
    static {
        NativeLoader.load();
    }

    // Оголошуємо нативні методи, які ми реалізували в C++
    // Вони мають бути статичними і з ключовим словом native
    public static native void nativeInitPhysicsSystem();

    // Інші нативні методи...
}
