package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code /qa resource <type> autofill <true|false>}: keeps the player's pool of a resource full, so a playtest needs
 * no hits on the training dummy for rage (Matěj, 2026-10-30). The setting is saved with the player
 * ({@code realmforge_qa:resource_autofill}) and kept through death; {@code /qa spell resource <type> <amount>} still
 * sets an amount once.
 */
public final class ResourceCommands {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RealmforgeQa.MOD_ID);

    /** The resource types a player's pools are kept full of, by id. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<Identifier>>> AUTOFILL =
            ATTACHMENTS.register("resource_autofill", () -> AttachmentType.builder(() -> List.<Identifier>of())
                    .serialize(Identifier.CODEC.listOf().fieldOf("value")).copyOnDeath().build());

    private static final List<DeferredHolder<ResourceType, ResourceType>> RESOURCES =
            List.of(ResourceTypes.MANA, ResourceTypes.RAGE);

    private ResourceCommands() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, ResourceCommands::onPlayerTick);
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> resource = Commands.literal("resource");
        for (DeferredHolder<ResourceType, ResourceType> type : RESOURCES) {
            resource.then(Commands.literal(type.getId().getPath())
                    .then(Commands.literal("autofill")
                            .then(Commands.argument("on", BoolArgumentType.bool())
                                    .executes(context -> autofill(context, type)))));
        }
        return resource;
    }

    /** Turns the autofill of {@code type} on or off for {@code player}; returns whether it is on now. */
    public static boolean setAutofill(Player player, Holder<ResourceType> type, boolean on) {
        Identifier id = type.unwrapKey().orElseThrow().identifier();
        Set<Identifier> types = new TreeSet<>(player.getData(AUTOFILL));
        if (on) {
            types.add(id);
        } else {
            types.remove(id);
        }
        player.setData(AUTOFILL, List.copyOf(types));
        if (on) {
            fill(player, type);
        }
        return on;
    }

    private static int autofill(CommandContext<CommandSourceStack> context, Holder<ResourceType> type)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean on = setAutofill(player, type, BoolArgumentType.getBool(context, "on"));
        context.getSource().sendSuccess(() -> Component.literal((on ? "Keeping " : "No longer keeping ")
                + type.getRegisteredName() + " of " + player.getPlainTextName() + " full"), true);
        return on ? 1 : 0;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        for (Identifier id : player.getExistingData(AUTOFILL).orElse(List.of())) {
            SpellRegistries.RESOURCE_TYPES.get(id).ifPresent(type -> fill(player, type));
        }
    }

    /** Tops the pool up to its maximum, syncing only when it was below it. */
    private static void fill(Player player, Holder<ResourceType> type) {
        double maximum = type.value().maximum(player);
        if (maximum > 0 && Resources.amount(player, type) < maximum) {
            Resources.set(player, type, maximum);
        }
    }
}
