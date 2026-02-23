package com.ladakx.inertia.api;

import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.world.IPhysicsWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

class InertiaApiCompatibilityTest {

    @AfterEach
    void cleanup() {
        InertiaAPI.resetResolver();
    }

    @Test
    void shouldResolveApiWhenProviderRegistered() {
        TestInertiaApi api = new TestInertiaApi();
        InertiaAPI.setResolver(() -> new TestProvider(api));

        InertiaAPI resolved = InertiaAPI.resolve();

        Assertions.assertSame(api, resolved);
    }

    @Test
    void shouldFailFastWhenServiceUnavailable() {
        InertiaAPI.setResolver(() -> null);

        Assertions.assertThrows(InertiaApiUnavailableException.class, InertiaAPI::resolve);
    }

    @Test
    void shouldSupportReloadFlowWithUpdatedProvider() {
        AtomicReference<InertiaApiProvider> providerRef = new AtomicReference<>();
        InertiaAPI.setResolver(providerRef::get);

        TestInertiaApi first = new TestInertiaApi();
        providerRef.set(new TestProvider(first));
        Assertions.assertSame(first, InertiaAPI.resolve());

        providerRef.set(null);
        Assertions.assertThrows(InertiaApiUnavailableException.class, InertiaAPI::resolve);

        TestInertiaApi second = new TestInertiaApi();
        providerRef.set(new TestProvider(second));
        Assertions.assertSame(second, InertiaAPI.resolve());
    }

    private static final class TestProvider implements InertiaApiProvider {
        private final InertiaAPI api;

        private TestProvider(InertiaAPI api) {
            this.api = Objects.requireNonNull(api, "api");
        }

        @Override
        public @NotNull InertiaAPI getApi() {
            return api;
        }
    }

    private static final class TestInertiaApi extends InertiaAPI {
        @Override
        public @Nullable PhysicsBody createBody(@NotNull Location location, @NotNull String bodyId) {
            return null;
        }

        @Override
        public boolean isWorldSimulated(@NotNull String worldName) {
            return false;
        }

        @Override
        public @Nullable IPhysicsWorld getPhysicsWorld(@NotNull World world) {
            return null;
        }

        @Override
        public @NotNull Collection<IPhysicsWorld> getAllPhysicsWorlds() {
            return Collections.emptyList();
        }

        @Override
        public @NotNull RenderingService rendering() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ConfigService configs() {
            throw new UnsupportedOperationException();
        }
    }
}
