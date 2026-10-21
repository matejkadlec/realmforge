package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;

/**
 * The local player's casting as the client knows it this tick, for {@link CastQueue}: the {@code realmforge:combat}
 * attachment the server syncs to its owner (the cast in progress, the cooldowns as the game time they end) and the
 * level's game time.
 */
final class ClientSpellState implements CastQueue.State {
    private final LocalPlayer player;
    private final CombatState combat;
    private final long now;

    ClientSpellState(LocalPlayer player) {
        this.player = player;
        this.combat = player.getData(SpellAttachments.COMBAT);
        this.now = player.level().getGameTime();
    }

    @Override
    public Optional<ResourceKey<Spell>> casting() {
        return combat.currentCast().map(CombatState.Cast::spell);
    }

    @Override
    public boolean onCooldown(ResourceKey<Spell> spell) {
        return combat.isOnCooldown(spell, now);
    }

    @Override
    public boolean onGlobalCooldown(ResourceKey<Spell> spell) {
        return combat.isOnGlobalCooldown(now) && Spells.get(player.registryAccess(), spell)
                .map(holder -> holder.value().globalCooldownTicks() > 0).orElse(true);
    }
}
