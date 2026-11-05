package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Turns a player's aura spells on and off (RLM-595; Matěj, 2026-11-03; architecture 11.1). A "cast" of an aura from
 * the spell bar toggles it instead of running effects: it costs nothing, is off the global cooldown (neither waits for
 * it nor starts it) and never touches a cast in progress. Turning an aura on turns off every other aura of its
 * {@link Aura#group} (a switch: one stance at a time), and turning one on, off or switching starts
 * {@link Aura#COOLDOWN_TICKS} on every aura spell, whatever its group, which Cooldown Reduction never shortens. While
 * that cooldown runs, or while the player is stunned, a toggle is refused and changes nothing. The spellbook saves
 * which auras are on ({@link Spellbook#auras}), so they stay on through death and relog, and {@link Passives} gives
 * an aura's bonus while it is on and its weapon condition holds: an unmet condition pauses the bonus, never the aura.
 * Logical server only.
 */
public final class Auras {
    private Auras() {
    }

    /**
     * {@code player} toggles the aura spell {@code spell}, which they know: on, switching off the other auras of its
     * group, or off if it was on. Refused while the shared aura cooldown runs or while they are stunned.
     */
    static CastResult toggle(Player player, Holder.Reference<Spell> spell) {
        if (!(player.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Auras are toggled on the logical server only");
        }
        Aura aura = spell.value().aura().orElseThrow(() -> new IllegalArgumentException(
                "Not an aura spell: " + spell.key().identifier()));
        CombatState state = player.getData(SpellAttachments.COMBAT);
        long now = level.getGameTime();
        if (state.isOnCooldown(spell.key(), now)) {
            return CastResult.refused(CastFailure.ON_COOLDOWN);
        }
        if (Effects.isStunned(player)) {
            return CastResult.refused(CastFailure.STUNNED);
        }
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        boolean on = !book.isOn(spell.key());
        Spellbook changed = book;
        if (on) {
            for (ResourceKey<Spell> other : book.auras()) {
                if (sameGroup(player, other, aura.group())) {
                    changed = changed.withAura(other, false);
                }
            }
        }
        player.setData(SpellAttachments.SPELLBOOK, changed.withAura(spell.key(), on));
        startCooldown(player, state, now);
        CharacterStats.markDirty(player);
        if (on) {
            SpellCaster.wentOff(player, spell);
        }
        return CastResult.cast(SpellTarget.of(player, player));
    }

    /** Whether {@code player}'s aura {@code spell} is on. */
    public static boolean isOn(Player player, ResourceKey<Spell> spell) {
        return player.getData(SpellAttachments.SPELLBOOK).isOn(spell);
    }

    /**
     * Starts the cooldown every aura shares ({@link Aura#COOLDOWN_TICKS}, no Cooldown Reduction) on every aura spell
     * of the world's data, so a spell bar shows it on each and an aura learned meanwhile waits too.
     */
    private static void startCooldown(Player player, CombatState state, long now) {
        state.clearEndedCooldowns(now);
        player.registryAccess().lookupOrThrow(SpellRegistries.SPELL).listElements()
                .filter(holder -> holder.value().isAura())
                .forEach(holder -> state.setCooldownEnd(holder.key(), now + Aura.COOLDOWN_TICKS));
        player.syncData(SpellAttachments.COMBAT);
    }

    private static boolean sameGroup(Player player, ResourceKey<Spell> spell, String group) {
        Optional<Aura> other = Spells.get(player.registryAccess(), spell).flatMap(holder -> holder.value().aura());
        // An aura whose entry is gone (content removed) gives nothing; switching turns it off with the group.
        return other.map(value -> value.group().equals(group)).orElse(true);
    }
}
