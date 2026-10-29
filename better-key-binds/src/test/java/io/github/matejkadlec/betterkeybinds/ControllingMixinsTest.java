package io.github.matejkadlec.betterkeybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** RLM-383: a hook whose merged method nothing in the target class calls found no injection point. */
class ControllingMixinsTest {
    private static final String TARGET = "com/blamejared/controlling/client/NewKeyBindsList$KeyEntry";

    @Test
    void aHookNothingCallsIsReported() {
        ClassNode node = new ClassNode();
        node.name = TARGET;
        MethodNode render = method("extractContent");
        render.instructions.add(call("handler$zza000$better_key_binds$betterKeyBinds$renderSecondaryButton"));
        // A lambda inside a hook is reached through a method handle, as javac compiles it.
        render.instructions.add(new InvokeDynamicInsnNode("apply", "()Ljava/util/function/Function;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "()V", false),
                Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new Handle(Opcodes.H_INVOKESTATIC, TARGET, "lambda$betterKeyBinds$search$0", "()V", false),
                Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;")));
        render.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(render);
        node.methods.add(method("handler$zza000$better_key_binds$betterKeyBinds$renderSecondaryButton"));
        node.methods.add(method("lambda$betterKeyBinds$search$0"));
        // Its injection point moved: merged into the class, called from nowhere.
        node.methods.add(method("wrapOperation$zzb000$better_key_binds$betterKeyBinds$moveChangeButton"));
        node.methods.add(method("children"));

        assertEquals(List.of("betterKeyBinds$moveChangeButton"), ControllingMixins.unusedHooks(node));
    }

    @Test
    void aClassWhoseHooksAllLandedReportsNothing() {
        ClassNode node = new ClassNode();
        node.name = TARGET;
        MethodNode init = method("<init>");
        init.instructions.add(call("handler$zza000$better_key_binds$betterKeyBinds$createSecondaryButton"));
        node.methods.add(init);
        MethodNode hook = method("handler$zza000$better_key_binds$betterKeyBinds$createSecondaryButton");
        hook.instructions.add(call("betterKeyBinds$refresh"));
        node.methods.add(hook);
        node.methods.add(method("betterKeyBinds$refresh"));

        assertEquals(List.of(), ControllingMixins.unusedHooks(node));
    }

    private static MethodNode method(String name) {
        return new MethodNode(Opcodes.ACC_PRIVATE, name, "()V", null, null);
    }

    private static MethodInsnNode call(String name) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, name, "()V", false);
    }
}
