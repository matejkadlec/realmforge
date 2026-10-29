package io.github.matejkadlec.betterkeybinds;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The plugin of the Controlling mixin config: it names every hook that a Controlling update left without its
 * injection point (RLM-383). The config sets {@code defaultRequire} 0, so such a hook is skipped instead of failing the
 * class, which would crash the game when the Key Binds screen loads it, and Mixin stays silent about it outside its
 * debug mode. After a mixin is applied, a hook whose merged method nothing in the class calls found no injection point.
 * MixinExtras applies its late injectors ({@code @WrapOperation} among them) only after this check, in its Mixin
 * extension, so their handlers are left out here: the row's layout hook reports itself when the row is drawn
 * ({@code ControllingKeyEntryMixin}). It sits outside the mixin package, whose classes Mixin loads only as mixins.
 */
public final class ControllingMixins implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Our hooks' prefix, which the merged methods keep in their names. */
    static final String PREFIX = "betterKeyBinds$";
    /** The handler prefixes of MixinExtras's late injectors, which inject after config plugins' {@code postApply}. */
    static final List<String> LATE_HANDLERS = List.of("wrapOperation$", "wrapWithCondition$", "modifyExpressionValue$");

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (String hook : unusedHooks(targetClass)) {
            LOGGER.warn("Better Key Binds: {} found no injection point in Controlling's {} (a Controlling update moved "
                    + "it); what it adds to the Key Binds screen is missing", hook, targetClassName);
        }
    }

    /**
     * Our methods in {@code node} that no instruction calls or names in a method handle, by their hook names, except
     * the handlers of late injectors, which are not applied yet.
     */
    static List<String> unusedHooks(ClassNode node) {
        Set<String> called = new HashSet<>();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)) {
                    called.add(call.name);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals(node.name)) {
                            called.add(handle.getName());
                        }
                    }
                }
            }
        }
        List<String> unused = new ArrayList<>();
        for (MethodNode method : node.methods) {
            int at = method.name.indexOf(PREFIX);
            boolean late = LATE_HANDLERS.stream().anyMatch(method.name::startsWith);
            if (at >= 0 && !late && !called.contains(method.name)) {
                unused.add(method.name.substring(at));
            }
        }
        return unused;
    }
}
