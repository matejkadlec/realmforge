package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The spell feature's public API (architecture 4.3): what other features and the QA mod use. Casting goes through
 * {@link SpellCaster}, resource pools through {@link Resources}.
 */
public final class Spells {
    private Spells() {
    }

    /** The spell with this key, if it exists in the world's data. */
    public static Optional<Holder.Reference<Spell>> get(RegistryAccess registries, ResourceKey<Spell> spell) {
        return registries.lookupOrThrow(SpellRegistries.SPELL).get(spell);
    }

    /**
     * Teaches {@code player} {@code spell} at {@code level}, replacing the level they knew. A spell they did not know
     * goes into the spell bar's first empty slot (C3b, RLM-25); learning a known one again, at any level, leaves the
     * bar as the player arranged it (RLM-374), so a rank-up never brings back a spell they took off. The level must lie
     * between 1 and the spell's {@code max_level}. Character (C4: spells by class level) and talents (C5) call this.
     */
    public static void learn(Player player, Holder.Reference<Spell> spell, int level) {
        if (level < 1 || level > spell.value().maxLevel()) {
            throw new IllegalArgumentException("Spell level " + level + " is outside 1 to " + spell.value().maxLevel()
                    + " for " + spell.key().identifier());
        }
        Spellbook old = player.getData(SpellAttachments.SPELLBOOK);
        Spellbook book = old.withSpell(spell.key(), level);
        boolean placed = old.level(spell.key()).isEmpty() && !spell.value().npcOnly();
        player.setData(SpellAttachments.SPELLBOOK, placed ? book.withSpellOnBar(spell.key()) : book);
    }

    /**
     * Puts {@code spell} into the bar's {@code slot} (0 to 7), or empties the slot: the spellbook's intent
     * ({@code SetSpellBarSlotPayload}). Refused, changing nothing, for another slot or a spell the player does not
     * know; a spell already on the bar moves there, swapping with the one it displaces. Returns whether it changed.
     */
    public static boolean placeOnBar(Player player, int slot, Optional<ResourceKey<Spell>> spell) {
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        if (slot < 0 || slot >= Spellbook.BAR_SLOTS || spell.isPresent() && book.level(spell.get()).isEmpty()) {
            return false;
        }
        Spellbook placed = spell.map(key -> book.placed(slot, key)).orElseGet(() -> book.withSlot(slot,
                Optional.empty()));
        if (placed.equals(book)) {
            return false;
        }
        player.setData(SpellAttachments.SPELLBOOK, placed);
        return true;
    }

    /**
     * Sets the racial ability beside the bar ({@link Spellbook#RACIAL_SLOT}, X), or empties it. The race fills it (C4,
     * RLM-27); the player cannot, so no payload reaches it. The spell must be known to be cast from there.
     */
    public static void setRacialSpell(Player player, Optional<ResourceKey<Spell>> spell) {
        player.setData(SpellAttachments.SPELLBOOK,
                player.getData(SpellAttachments.SPELLBOOK).withSlot(Spellbook.RACIAL_SLOT, spell));
    }

    /** {@code caster}'s cast-time or channelled spell in progress, if any. */
    public static Optional<Cast> currentCast(LivingEntity caster) {
        return Casting.current(caster);
    }

    /**
     * Cancels {@code caster}'s cast-time or channelled spell in progress, if any (the spell bar's cancel, C3b): a cast
     * time pays nothing, a channel keeps the ticks it ran. Returns whether a cast was running.
     */
    public static boolean cancelCast(LivingEntity caster) {
        return Casting.cancel(caster);
    }

    /** Ends every cooldown of {@code caster}, the global one included, and syncs it. */
    public static void resetCooldowns(LivingEntity caster) {
        caster.getData(SpellAttachments.COMBAT).clearCooldowns();
        caster.syncData(SpellAttachments.COMBAT);
    }
}
