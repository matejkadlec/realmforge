package io.github.matejkadlec.realmforge.mixin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.HolderSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a named tag's invalidation listeners thread-safe (RLM-612, architecture 19.3). NeoForge 26.1.2 patches
 * {@link HolderSet.Named} with a plain {@code ArrayList} of listeners, and every holder set that wraps a tag adds one
 * in its constructor: {@code neoforge:not} ({@code NotHolderSet}), {@code neoforge:and} and {@code neoforge:or}
 * ({@code CompositeHolderSet}). Vanilla parses a registry's entries on worker threads
 * ({@code ResourceManagerRegistryLoadTask}), which share each tag through {@code ConcurrentHolderGetter}; its lock
 * covers the lookup, not the add. So two structures whose biomes negate the same tag add to that one list at once.
 * A lost race threw {@code ArrayIndexOutOfBoundsException} from {@code ArrayList#add}, left the structure
 * ({@code apotheosis:tower_sand}) unbound, and the registry freeze failed the whole load: a world, a dedicated server,
 * the GameTest server. A race can also leave a null in the list, which crashes the tag's bind
 * ({@code NullPointerException} in {@code Named#bind}), or drop a listener, whose set then keeps a stale list after
 * a tag reload.
 *
 * <p>NeoForge's newest 26.1.2 build (26.1.2.126) still has the plain list, and no event reaches it, hence a mixin.
 * A {@link CopyOnWriteArrayList} takes concurrent adds safely, and {@code Named#bind} iterates a snapshot of it. A tag
 * gets a few listeners, once per load, so copying on each add costs nothing that matters. Tested by
 * {@code HolderSetListenersTest}, which fails without it.
 */
@Mixin(HolderSet.Named.class)
public abstract class HolderSetNamedMixin {
    /** NeoForge's list, which {@code Named#addInvalidationListener} adds to and {@code Named#bind} runs. */
    @Shadow
    private List<Runnable> invalidationCallbacks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void realmforge$threadSafeListeners(CallbackInfo callback) {
        this.invalidationCallbacks = new CopyOnWriteArrayList<>();
    }
}
