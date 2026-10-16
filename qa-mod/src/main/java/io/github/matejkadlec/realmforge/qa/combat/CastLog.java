package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastEndedEvent;
import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * How each entity's last cast-time or channelled spell ended (C3a-2), from the core mod's {@link CastEndedEvent}:
 * {@code /qa spell show} prints it and the spell GameTests check it. Server thread only; a new server starts empty.
 */
public final class CastLog {
    private static final Map<UUID, Ended> LAST = new HashMap<>();

    private CastLog() {
    }

    /** Called once from {@code RealmforgeQa}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CastEndedEvent.class, event -> LAST.put(event.getCaster().getUUID(),
                new Ended(event.getSpell().key(), event.getEnd())));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> LAST.clear());
    }

    /** How {@code caster}'s last cast ended, if it ever cast one with a cast time or channel. */
    public static Optional<Ended> last(Entity caster) {
        return Optional.ofNullable(LAST.get(caster.getUUID()));
    }

    /** One ended cast: the spell and how it ended. */
    public record Ended(ResourceKey<Spell> spell, CastEnd end) {
    }
}
