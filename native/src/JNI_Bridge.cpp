/* Original project path: native/src/JNI_Bridge.cpp */

#include <jni.h>
#include <iostream>
#include <map>
#include <atomic>
#include <vector>

// Jolt Physics Library headers
#include <Jolt/Jolt.h>
#include <Jolt/RegisterTypes.h>
#include <Jolt/Core/Factory.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Core/JobSystemThreadPool.h>
#include <Jolt/Physics/PhysicsSettings.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>

JPH_SUPPRESS_WARNINGS
using namespace JPH;

// Global Jolt components
static TempAllocator* gTempAllocator = nullptr;
static JobSystemThreadPool* gJobSystem = nullptr;
static PhysicsSystem* gPhysicsSystem = nullptr;

// Interface objects must persist for the lifetime of PhysicsSystem
static BroadPhaseLayerInterface* gBroadPhaseLayerInterface = nullptr;
static ObjectVsBroadPhaseLayerFilter* gObjectVsBroadPhaseLayerFilter = nullptr;
static ObjectLayerPairFilter* gObjectLayerPairFilter = nullptr;

// Our handle system to map our stable long IDs to Jolt's internal BodyID
static std::atomic<long> gNextBodyID(1);
static std::map<long, BodyID> gBodyIdMap;

// BroadPhase and Object layers
namespace Layers {
    static constexpr ObjectLayer NON_MOVING = 0;
    static constexpr ObjectLayer MOVING = 1;
};

class BPLayerInterfaceImpl final : public BroadPhaseLayerInterface {
public:
    BPLayerInterfaceImpl() {
        mObjectToBroadPhase[Layers::NON_MOVING] = BroadPhaseLayer(0);
        mObjectToBroadPhase[Layers::MOVING] = BroadPhaseLayer(1);
    }
    uint GetNumBroadPhaseLayers() const override { return 2; }
    BroadPhaseLayer GetBroadPhaseLayer(ObjectLayer inLayer) const override { return mObjectToBroadPhase[inLayer]; }
#if defined(JPH_EXTERNAL_PROFILE) || defined(JPH_PROFILE_ENABLED)
    const char * GetBroadPhaseLayerName(BroadPhaseLayer inLayer) const override {
        return inLayer.GetValue() == 0 ? "NON_MOVING" : "MOVING";
    }
#endif
private:
    BroadPhaseLayer mObjectToBroadPhase[Layers::MOVING + 1];
};

class ObjectVsBroadPhaseLayerFilterImpl : public ObjectVsBroadPhaseLayerFilter {
public:
    bool ShouldCollide(ObjectLayer inLayer1, BroadPhaseLayer inLayer2) const override {
        switch (inLayer1) {
            case Layers::NON_MOVING: return inLayer2 == BroadPhaseLayer(1); // MOVING
            case Layers::MOVING: return true;
            default: return false;
        }
    }
};

class ObjectLayerPairFilterImpl : public ObjectLayerPairFilter {
public:
    bool ShouldCollide(ObjectLayer inObject1, ObjectLayer inObject2) const override {
        return inObject1 == Layers::MOVING || inObject2 == Layers::MOVING;
    }
};

extern "C" {
    /**
     * Initializes the Jolt physics system.
     */
    JNIEXPORT void JNICALL Java_com_ladakx_inertia_core_ntve_JNIBridge_init(JNIEnv *env, jclass, jint maxBodies, jint numThreads) {
        if (gPhysicsSystem != nullptr) return;

        RegisterDefaultAllocator();
        Factory::sInstance = new Factory();
        RegisterTypes();

        gTempAllocator = new TempAllocatorImpl(10 * 1024 * 1024);
        gJobSystem = new JobSystemThreadPool(numThreads == 0 ? cMaxPhysicsJobs : numThreads, cMaxPhysicsBarriers, -1);

        const uint cMaxBodyPairs = 65536;
        const uint cMaxContactConstraints = 10240;

        // Create interface objects that will persist for the lifetime of PhysicsSystem
        gBroadPhaseLayerInterface = new BPLayerInterfaceImpl();
        gObjectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterImpl();
        gObjectLayerPairFilter = new ObjectLayerPairFilterImpl();

        gPhysicsSystem = new PhysicsSystem();
        gPhysicsSystem->Init(maxBodies, 0, cMaxBodyPairs, cMaxContactConstraints,
                             *gBroadPhaseLayerInterface, *gObjectVsBroadPhaseLayerFilter, *gObjectLayerPairFilter);

        std::cout << "Inertia JNI: Jolt Physics System Initialized." << std::endl;
    }

    /**
     * Shuts down the Jolt physics system and cleans up resources.
     */
    JNIEXPORT void JNICALL Java_com_ladakx_inertia_core_ntve_JNIBridge_shutdown(JNIEnv *env, jclass) {
        if (gPhysicsSystem == nullptr) return;

        delete gPhysicsSystem;
        gPhysicsSystem = nullptr;

        delete gJobSystem;
        gJobSystem = nullptr;

        delete gTempAllocator;
        gTempAllocator = nullptr;

        // Clean up interface objects
        delete gBroadPhaseLayerInterface;
        gBroadPhaseLayerInterface = nullptr;

        delete gObjectVsBroadPhaseLayerFilter;
        gObjectVsBroadPhaseLayerFilter = nullptr;

        delete gObjectLayerPairFilter;
        gObjectLayerPairFilter = nullptr;

        UnregisterTypes();
        delete Factory::sInstance;
        Factory::sInstance = nullptr;

        gBodyIdMap.clear();
        std::cout << "Inertia JNI: Jolt Physics System Shutdown." << std::endl;
    }

    /**
     * Updates the physics simulation by a delta time and writes the results to the provided buffer.
     */
    JNIEXPORT jint JNICALL Java_com_ladakx_inertia_core_ntve_JNIBridge_updateAndGetTransforms(JNIEnv* env, jclass, jfloat deltaTime, jobject byteBuffer) {
        if (gPhysicsSystem == nullptr) return 0;

        BodyInterface& bodyInterface = gPhysicsSystem->GetBodyInterface();

        gPhysicsSystem->Update(deltaTime, 1, gTempAllocator, gJobSystem);

        void* bufferPtr = env->GetDirectBufferAddress(byteBuffer);
        if (bufferPtr == nullptr) return 0;

        char* currentPtr = static_cast<char*>(bufferPtr);
        int bodyCount = 0;

        BodyIDVector active_bodies;
        gPhysicsSystem->GetActiveBodies(EBodyType::RigidBody, active_bodies);

        for (const BodyID& bodyID : active_bodies) {
            long ourId = -1;
            for (auto const& [key, val] : gBodyIdMap) {
                if (val == bodyID) {
                    ourId = key;
                    break;
                }
            }

            if (ourId != -1) {
                *reinterpret_cast<long*>(currentPtr) = ourId;
                currentPtr += sizeof(long);

                Vec3 position = bodyInterface.GetPosition(bodyID);
                *reinterpret_cast<float*>(currentPtr) = position.GetX(); currentPtr += sizeof(float);
                *reinterpret_cast<float*>(currentPtr) = position.GetY(); currentPtr += sizeof(float);
                *reinterpret_cast<float*>(currentPtr) = position.GetZ(); currentPtr += sizeof(float);

                Quat rotation = bodyInterface.GetRotation(bodyID);
                *reinterpret_cast<float*>(currentPtr) = rotation.GetX(); currentPtr += sizeof(float);
                *reinterpret_cast<float*>(currentPtr) = rotation.GetY(); currentPtr += sizeof(float);
                *reinterpret_cast<float*>(currentPtr) = rotation.GetZ(); currentPtr += sizeof(float);
                *reinterpret_cast<float*>(currentPtr) = rotation.GetW(); currentPtr += sizeof(float);

                bodyCount++;
            }
        }
        return bodyCount;
    }

    /**
     * Creates a box-shaped body.
     */
    JNIEXPORT jlong JNICALL Java_com_ladakx_inertia_core_ntve_JNIBridge_createBoxBody(JNIEnv *env, jclass, jdouble posX, jdouble posY, jdouble posZ, jdouble rotX, jdouble rotY, jdouble rotZ, jdouble rotW, jint bodyType, jfloat halfExtentX, jfloat halfExtentY, jfloat halfExtentZ) {
        if (gPhysicsSystem == nullptr) return -1;

        BodyInterface& bodyInterface = gPhysicsSystem->GetBodyInterface();

        ObjectLayer layer = (EMotionType)bodyType == EMotionType::Static ? Layers::NON_MOVING : Layers::MOVING;

        // --- ФІНАЛЬНЕ ВИПРАВЛЕННЯ ---
        BodyCreationSettings bodySettings(
            new BoxShape(Vec3(halfExtentX, halfExtentY, halfExtentZ)),
            RVec3(posX, posY, posZ),
            Quat(rotX, rotY, rotZ, rotW),
            (EMotionType)bodyType,
            layer
        );

        Body* body = bodyInterface.CreateBody(bodySettings);
        if (body == nullptr) return -1;

        BodyID bodyId = body->GetID();
        bodyInterface.AddBody(bodyId, EActivation::Activate);

        long ourId = gNextBodyID++;
        gBodyIdMap[ourId] = bodyId;

        return ourId;
    }

    /**
     * Destroys a body by its stable ID.
     */
    JNIEXPORT void JNICALL Java_com_ladakx_inertia_core_ntve_JNIBridge_destroyBody(JNIEnv *env, jclass, jlong ourId) {
        if (gPhysicsSystem == nullptr) return;

        auto it = gBodyIdMap.find(ourId);
        if (it != gBodyIdMap.end()) {
            BodyInterface& bodyInterface = gPhysicsSystem->GetBodyInterface();
            bodyInterface.RemoveBody(it->second);
            bodyInterface.DestroyBody(it->second);
            gBodyIdMap.erase(it);
        }
    }
}