#include <jni.h>
#include <iostream>
#include <Jolt/Jolt.h>

JPH_SUPPRESS_WARNINGS
using namespace JPH;

static bool isInitialized = false;

extern "C" {

    JNIEXPORT void JNICALL Java_com_inertia_core_ntve_JNIBridge_init(JNIEnv *env, jclass, jint maxBodies, jint numBodyMutexes, jint maxBodyPairs, int maxContactConstraints) {
        if (isInitialized) return;
        std::cout << "Inertia JNI: Initializing Jolt Physics..." << std::endl;
        isInitialized = true;
    }

    JNIEXPORT void JNICALL Java_com_inertia_core_ntve_JNIBridge_update(JNIEnv *env, jclass, jfloat deltaTime) {
        if (!isInitialized) return;
    }

    JNIEXPORT void JNICALL Java_com_inertia_core_ntve_JNIBridge_shutdown(JNIEnv *env, jclass) {
        if (!isInitialized) return;
        std::cout << "Inertia JNI: Shutting down Jolt Physics..." << std::endl;
        isInitialized = false;
    }

    JNIEXPORT jstring JNICALL Java_com_inertia_core_ntve_JNIBridge_getJoltVersion(JNIEnv *env, jclass) {
        // ТИМЧАСОВЕ РІШЕННЯ:
        // Ми повертаємо статичний рядок, щоб гарантувати успішну збірку.
        // Ми повернемося до виклику функції пізніше.
        // return env->NewStringUTF(JPH::GetJoltVersionString()); // Проблемний рядок
        return env->NewStringUTF("Jolt Version (to be implemented)");
    }
}