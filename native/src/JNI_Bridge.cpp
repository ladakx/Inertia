#include <jni.h>
#include <iostream>


extern "C" JNIEXPORT void JNICALL Java_com_inertia_core_native_JNIBridge_nativeInitPhysicsSystem(JNIEnv *env, jclass cls) {
    std::cout << "Hello from C++! Physics system initialized." << std::endl;
}
