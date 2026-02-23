
# Inertia ⚛️

**Inertia** is a high-performance Minecraft server plugin (Paper/Spigot) that integrates the industry-standard **Jolt Physics** engine into the voxel world.

Unlike standard Minecraft physics, Inertia provides real-time, rigid-body simulation, complex collisions, ragdolls, and physical interactions, all offloaded to native threads for maximum performance.

![Build Status](https://img.shields.io/badge/build-passing-success)
![Version](https://img.shields.io/badge/version-1.0--DEV-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## 🚀 Key Features

* **Jolt Physics Core:** Powered by `jolt-jni`, bringing AAA-game physics to Minecraft.
* **High Performance:** Physics simulation runs on dedicated worker threads (multithreaded), completely separate from the main server tick loop.
* **Advanced Shapes:**
    * Primitives: Box, Sphere, Capsule, Cylinder, Tapered shapes.
    * **Convex Hulls:** Import custom models (`.obj`) from **BlockBench** for precise collisions.
    * Compound Shapes: Combine multiple primitives into complex bodies.
* **Dynamic Objects:**
    * **Rigid Blocks:** Throw, stack, and interact with blocks physically.
    * **Ragdolls:** Multi-part articulated entities with joint limits (e.g., corpses, statues).
    * **Chains:** Physically simulated hanging chains / ropes.
    * **Active TNT:** Physics-based explosives with fuses.
* **Interactive Tools:**
    * 🧲 **Grabber:** "Gravity Gun" style tool to pick up and throw bodies.
    * 🔗 **Welder:** Connect bodies together dynamically.
    * ⛓️ **Chain Tool:** Create physical connections between points.
* **NMS Support:** Multi-version support via abstraction layer (Tested on 1.16.5 - 1.21.x).

---

## 🛠️ Installation

1.  Ensure you are running a **Paper** or **Spigot** server (Java 17+ recommended).
2.  Download the latest `Inertia-1.0-DEV.jar`.
3.  Place it in your `plugins` folder.
4.  Start the server. The plugin will automatically extract the necessary native libraries (`.dll` / `.so`) for your OS.

---

## ⚙️ Configuration

Inertia is highly data-driven. You define your physical objects in YAML files.

### 1. `bodies.yml` (Defining Prefabs)
Create custom physical objects with specific mass, friction, and shapes.

```yaml
# Example: A heavy crate using a custom BlockBench model
crates.heavy_box:
  physics:
    mass: 50.0
    friction: 0.8
    restitution: 0.1 # Bounciness
    motion-type: Dynamic
    # Shape definition using the new parser syntax
    shape:
      - "type=convex_hull mesh=models/crate.obj convexRadius=0.02"
  render:
    model: "crates.heavy_box_visual" # Links to render.yml

# Example: A simple sphere
misc.beach_ball:
  physics:
    mass: 2.0
    restitution: 0.8
    shape:
      - "type=sphere radius=0.5"

```

### 2. `render.yml` (Visuals)

Map physical bodies to Minecraft visual entities (Block Displays or Item Displays).

```yaml
crates.heavy_box_visual:
  sync:
    position: true
    rotation: true
  entities:
    main_display:
      type: ITEM_DISPLAY
      item-model: items.custom_crate # From items.yml
      scale: "1.0 1.0 1.0"
      display-mode: HEAD

```

---

## 🎮 Commands & Permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/inertia help` | `inertia.commands.help` | Show command list. |
| `/inertia reload` | `inertia.commands.reload` | Async reload of configuration & meshes. |
| `/inertia clear` | `inertia.commands.clear` | Remove all physics bodies from the world. |
| `/inertia spawn body <id>` | `inertia.commands.spawn` | Spawn a simple body defined in `bodies.yml`. |
| `/inertia spawn ragdoll <id>` | `inertia.commands.spawn` | Spawn a complex ragdoll. |
| `/inertia spawn chain <id>` | `inertia.commands.spawn` | Spawn a physical chain. |
| `/inertia tool <tool>` | `inertia.commands.tool` | Get tools (`grabber`, `welder`, `remover`). |

---

## 🏗️ Architecture (For Developers)

Inertia uses a **Feature-Based** architecture with **Dependency Injection**.

### Package Structure

* `com.ladakx.inertia.api` - Public API for other plugins.
* `com.ladakx.inertia.core` - Composition Root & Plugin entry point.
* `com.ladakx.inertia.physics` - The heart of the engine (Jolt wrappers, World Registry).
* `com.ladakx.inertia.features` - Gameplay logic (Tools, Commands).
* `com.ladakx.inertia.infrastructure` - Low-level NMS adapters & Native loaders.

### Building from Source

The project uses Gradle with the Shadow plugin.

1. Clone the repository.
2. Run the build command:
```bash
./gradlew shadowJar

```


3. The artifact will be in `inertia-plugin/build/libs/`.

---

## ⚠️ Requirements

* **OS:** Windows (x64), Linux (x64/ARM64), macOS (x64/ARM64).
* **Java:** JDK 16 or higher.
* **Minecraft:** 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.x, 1.21.x.

## Credits

* **Jolt Physics** by Jorrit Rouwe.
* **jolt-jni** binding by Stephen Gold.
* Developed by **Ladakx**.

## API Compatibility Matrix for Third-Party Plugins

When integrating with Inertia API, validate both:
- Minimum API version (`InertiaAPI.apiVersion()` / `isCompatibleWith(...)`)
- Required capabilities (`InertiaAPI.capabilities().supports(...)`)

| Feature | Minimum API version | Required capabilities |
|---|---:|---|
| Core body spawn by configured `bodyId` | `1.2.0` | `PHYSICS_SHAPE_BOX`, `PHYSICS_SHAPE_SPHERE`, `PHYSICS_SHAPE_CAPSULE`, `PHYSICS_SHAPE_CYLINDER`, `PHYSICS_SHAPE_TAPERED_CAPSULE`, `PHYSICS_SHAPE_TAPERED_CYLINDER`, `PHYSICS_SHAPE_CONVEX_HULL`, `PHYSICS_SHAPE_COMPOUND` |
| Rendering API access (`InertiaAPI.rendering()`) | `1.2.0` | `RENDERING` |
| Advanced interaction queries (`PhysicsWorld.getInteraction()`) | `1.2.0` | `INTERACTION_ADVANCED` |
| Custom physics shape descriptors | `1.2.0` | `PHYSICS_SHAPE_CUSTOM` |

Example compatibility gate:

```java
InertiaAPI api = InertiaAPI.resolve();
boolean compatible = api.isCompatibleWith(
        "1.2.0",
        java.util.EnumSet.of(
                com.ladakx.inertia.api.capability.ApiCapability.RENDERING,
                com.ladakx.inertia.api.capability.ApiCapability.INTERACTION_ADVANCED
        )
);
```
