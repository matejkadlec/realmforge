package io.github.matejkadlec.realmforge.spell;

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
     * Teaches {@code player} {@code spell} at {@code level}, replacing the level they knew. The level must lie between
     * 1 and the spell's {@code max_level}. Character (C4: spells by class level) and talents (C5) call this.
     */
    public static void learn(Player player, Holder.Reference<Spell> spell, int level) {
        if (level < 1 || level > spell.value().maxLevel()) {
            throw new IllegalArgumentException("Spell level " + level + " is outside 1 to " + spell.value().maxLevel()
                    + " for " + spell.key().identifier());
        }
        player.setData(SpellAttachments.SPELLBOOK,
                player.getData(SpellAttachments.SPELLBOOK).withSpell(spell.key(), level));
    }

    /** Ends every cooldown of {@code caster}, the global one included, and syncs it. */
    public static void resetCooldowns(LivingEntity caster) {
        caster.getData(SpellAttachments.COMBAT).clearCooldowns();
        caster.syncData(SpellAttachments.COMBAT);
    }
}
