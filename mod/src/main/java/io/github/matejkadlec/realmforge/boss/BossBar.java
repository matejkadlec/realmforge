package io.github.matejkadlec.realmforge.boss;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * A boss's vanilla boss bar at the top of the screen (RLM-173, architecture 14.3): a {@link ServerBossEvent} the boss
 * entity owns, the Wither's pattern. The boss hands this bar its tracking calls ({@link #startSeenBy} from
 * {@code Entity#startSeenByPlayer}, {@link #stopSeenBy} from {@code #stopSeenByPlayer}), so a player sees the bar
 * exactly while their client tracks the boss: within its entity type's client tracking range and the server's view
 * distance, in its dimension, logged in. It calls {@link #update} every tick, which keeps the bar's progress at the
 * boss's share of health and its title at the boss's name, or at the title the boss set ({@link #setTitle}: W5's
 * phase name). From the boss's death on, nobody sees the bar.
 *
 * <p>The client gets only the title, colour, overlay and progress, so this needs no payload and no drawing of ours,
 * and other mods treat the bar as they treat the Wither's. A boss's entity type also goes into NeoForge's
 * {@code c:bosses} tag, which Neat and our levels (architecture 14.1) read.
 */
public final class BossBar {
    private final LivingEntity boss;
    private final ServerBossEvent event;
    @Nullable
    private Component title;

    /** A bar for {@code boss} in {@code color}, filled to its health; build it when the boss is constructed. */
    public BossBar(LivingEntity boss, BossEvent.BossBarColor color) {
        this.boss = boss;
        this.event = new ServerBossEvent(Mth.createInsecureUUID(boss.getRandom()), boss.getDisplayName(), color,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    /** Shows the bar to a player whose client starts tracking the boss, unless the boss is dying. */
    public void startSeenBy(ServerPlayer player) {
        if (!boss.isDeadOrDying()) {
            event.addPlayer(player);
        }
    }

    /** Hides the bar from a player whose client stops tracking the boss. */
    public void stopSeenBy(ServerPlayer player) {
        event.removePlayer(player);
    }

    /**
     * Follows the boss on the server, every tick: the progress is its share of health and the title its current one;
     * a dying boss's bar goes away. A value that did not change sends nothing.
     */
    public void update() {
        if (boss.level().isClientSide()) {
            return;
        }
        if (boss.isDeadOrDying()) {
            event.removeAllPlayers();
            return;
        }
        event.setProgress(Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0.0F, 1.0F));
        event.setName(currentTitle());
    }

    /** Sets the bar's title, such as the boss's phase; {@code null} goes back to the boss's name. */
    public void setTitle(@Nullable Component title) {
        this.title = title;
        if (!boss.level().isClientSide()) {
            event.setName(currentTitle());
        }
    }

    /** The title the players see. */
    public Component title() {
        return event.getName();
    }

    /** The bar's fill, from 0 to 1. */
    public float progress() {
        return event.getProgress();
    }

    /** The players who see the bar. */
    public Collection<ServerPlayer> players() {
        return event.getPlayers();
    }

    private Component currentTitle() {
        return title != null ? title : boss.getDisplayName();
    }
}
