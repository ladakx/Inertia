# Inertia

**Inertia** is a high-performance plugin for Minecraft servers (Spigot/Paper) that integrates the **Jolt Physics** engine into your world.

This project replaces vanilla physics, allowing objects (and, in the future, entities) to interact realistically with the environment. It uses Jolt-JNI for high-efficiency native computations, offloading the heavy physics calculations from the main server thread.

## 🚀 Key Features

* **Realistic Physics:** Powered by **Jolt Physics**, a modern and fast physics engine.
* **Multithreading:** Physics calculations (`PhysicsScheduler`) and world generation (`SimulationScheduler`) run on separate, dedicated threads, minimizing impact on server TPS.
* **Dynamic World:** The world around players is dynamically converted into physical objects. The `RayonGenerator` creates static bodies for the landscape in real-time.
* **NMS Support:** A multi-module architecture with NMS adapters to support a wide range of Minecraft versions (from 1.16.3 to 1.21+).
* **Flexible Configuration:**
    * **`bodies.yml`:** Create your own "prefabs" of physical objects (e.g., crates, balls) with custom mass, shape, and friction.
    * **`blocks.yml`:** Configure physical properties (friction, restitution) for every block type in Minecraft.
* **Developer API:** The `inertia-api` module provides events (like `PhysicsLoadSpaceEvent`, `PhysicsContactEvent`) and classes for interacting with the physics world.

## 📁 Project Structure

The project uses a multi-module structure (likely Gradle/Maven) to ensure clean code and support for multiple game versions:

* **`inertia-api`**:
    * The public-facing API for the plugin. Contains events and interfaces.
* **`inertia-core`**:
    * The core of the plugin. Contains the main `InertiaPlugin` class, simulation managers (`BulletManager`, `SpaceManager`), and the Jolt native library loader (`JoltNatives.java`).
    * It also includes the custom `MinecraftSpace` implementation and the `RayonGenerator` world meshing logic.
    * *Note: This module contains a significant amount of code from the `jme3-bullet` library. This is used for math operations (`Vector3f`, `Quaternion`) and collision shape definitions, but the core simulation engine is Jolt.*
* **`inertia-nms-v*`**:
    * A collection of NMS (net.minecraft.server) modules. Each module implements `BulletNMSTools` and `PlayerNMSTools` for a specific Minecraft version, allowing it to retrieve custom block BoundingBoxes and other version-dependent information.
* **`inertia-plugin`**:
    * The assembly module for the final plugin JAR. It contains the final `plugin.yml` and default configuration files (`config.yml`, `bodies.yml`, `blocks.yml`, `lang_uk.yml`).

## ⚙️ Configuration

The main configuration files are located in `inertia-plugin/src/main/resources/`:

* **`config.yml`:**
    * `physics-core`: Settings for gravity and the physics update rate (tick-rate).
    * `world-simulation`: The simulation radius around players.
    * `interaction`: Settings for how players interact with physical objects.
* **`bodies.yml`:**
    * Allows you to create prefabs for spawnable objects. You can define the shape (box, sphere), mass, friction, and restitution (bounciness).
* **`blocks.yml`:**
    * Allows you to override the physical properties for any Minecraft material. For example, you can make ice slippery (low friction) or slime blocks bouncy (high restitution).
* **`lang_*.yml`:**
    * Localization files for plugin messages.

## ⌨️ Commands & Permissions

Here is a list of the primary commands implemented in `inertia-core` and `inertia-plugin`:

| Command                                       | Permission | Description |
|:----------------------------------------------| :--- | :--- |
| `/inertia reload`                             | `inertia.commands.reload` | Reloads the plugin's configuration files. |
| `/inertia help`                               | `inertia.commands.help` | Displays the help message. |
| `/inertia debug bar`                          | `inertia.commands.debug.bar` | Toggles the Boss Bar with physics world stats (MSPT, body count). |
| `/inertia debug block info`                   | `inertia.commands.debug.block` | Shows BoundingBox info for the block you are looking at. |
| `/inertia debug block spawn <block> [radius]` | `inertia.commands.debug.block` | Spawns physics blocks for testing (configured in `config.yml`). |
| `/inertia debug block clear`                  | `inertia.commands.debug.block` | Clears all spawned debug physics blocks. |
| `/inertia hitbox`                             | `inertia.commands.hitbox` | Toggles particle-based hitbox visualization. |
| `/inertia spawn <prefab>`                     | `inertia.command.spawn` | [cite_start]Spawns a physical object from a prefab defined in `bodies.yml`. [cite: 3719, 3720, 3721] |

---

I can also help you write the Build Instructions or a "How it Works" section with a detailed code-flow explanation if you'd like.