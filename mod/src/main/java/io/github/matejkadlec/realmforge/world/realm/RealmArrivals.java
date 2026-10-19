package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import io.github.matejkadlec.realmforge.world.MobLevelRule;
import io.github.matejkadlec.realmforge.world.WorldAttachments;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The arrival title (realm portal spec, "Arriving"; RLM-139): on every arrival in a realm, whatever the way in, the
 * realm's name with its element and its levels for 2 seconds; on a player's first arrival, its flavour line in chat
 * too. The levels come from the realm's dimension rules, which stay on the server.
 */
public final class RealmArrivals {
    /** How long the title stays: 2 seconds, between vanilla's short fades. */
    static final int STAY_TICKS = 40;
    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_TICKS = 10;

    private RealmArrivals() {
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Realms.of(player.level().registryAccess(), event.getTo()).ifPresent(realm -> arrive(player, realm));
    }

    private static void arrive(ServerPlayer player, Holder.Reference<Realm> realm) {
        Component name = Component.translatable(realm.value().name());
        Component element = Component.translatable(realm.value().subtitle());
        Optional<MobLevelRule> levels = DimensionRules.mobLevels(player.level().registryAccess(), realm.value().dimension());
        Component subtitle = levels
                .<Component>map(rule -> Component.translatable(GeneratedTexts.MESSAGE_REALM_ARRIVAL_LEVELS, element,
                        rule.minLevel(), rule.maxLevel()))
                .orElse(element);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(name));

        List<String> visited = player.getData(WorldAttachments.VISITED_REALMS);
        String id = realm.key().identifier().toString();
        if (!visited.contains(id)) {
            Set<String> updated = new HashSet<>(visited);
            updated.add(id);
            player.setData(WorldAttachments.VISITED_REALMS, updated.stream().sorted().toList());
            player.sendSystemMessage(Component.translatable(realm.value().flavour())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
