package io.github.matejkadlec.realmforge.spell;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Posted on the game bus ({@code NeoForge.EVENT_BUS}) when a cast-time or channelled spell ends, completed or not
 * (architecture 4.4: higher features react to lower ones through our own events). Procs (C3a-4) and the QA mod's cast
 * log listen; it cannot be cancelled.
 */
public final class CastEndedEvent extends Event {
    private final LivingEntity caster;
    private final Holder.Reference<Spell> spell;
    private final CastEnd end;

    public CastEndedEvent(LivingEntity caster, Holder.Reference<Spell> spell, CastEnd end) {
        this.caster = caster;
        this.spell = spell;
        this.end = end;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public Holder.Reference<Spell> getSpell() {
        return spell;
    }

    public CastEnd getEnd() {
        return end;
    }
}
