package com.ladakx.inertia.api;

import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.version.ApiVersion;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsSlaContract;
import com.ladakx.inertia.api.diagnostics.WorldHealthSnapshot;
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
import java.util.EnumSet;
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
    void shouldMatchCompatibilityByVersionAndCapability() {
        TestInertiaApi api = new TestInertiaApi();

        Assertions.assertTrue(api.isCompatibleWith(ApiVersion.parse("1.2.0"), EnumSet.of(ApiCapability.RENDERING)));
        Assertions.assertFalse(api.isCompatibleWith(ApiVersion.parse("1.3.0"), EnumSet.of(ApiCapability.RENDERING)));
        Assertions.assertFalse(api.isCompatibleWith(ApiVersion.parse("1.2.0"), EnumSet.of(ApiCapability.PHYSICS_SHAPE_CUSTOM)));
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
        public @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull Location location, @NotNull String bodyId) {
            return ApiResult.failure(ApiErrorCode.INTERNAL_ERROR, "error-occurred");
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


        @Override
        public @NotNull DiagnosticsService diagnostics() {
            return new DiagnosticsService() {
                @Override
                public @NotNull Collection<WorldHealthSnapshot> getWorldHealthSnapshots() {
                    return Collections.emptyList();
                }

                @Override
                public @NotNull WorldHealthSnapshot getWorldHealthSnapshot(@NotNull String worldName) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public @NotNull DiagnosticsSlaContract getSlaContract() {
                    return new DiagnosticsSlaContract(20, 150, "physics-loop-thread", 256);
                }
            };
        }
        @Override
        public @NotNull CapabilityService capabilities() {
            return new CapabilityService() {
                @Override
                public boolean supports(@NotNull ApiCapability capability) {
                    return capability == ApiCapability.RENDERING || capability == ApiCapability.INTERACTION_ADVANCED;
                }

                @Override
                public @NotNull ApiVersion apiVersion() {
                    return new ApiVersion(1, 2, 0);
                }
            };
        }
    }
}
