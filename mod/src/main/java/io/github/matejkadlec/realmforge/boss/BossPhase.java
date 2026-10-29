package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/**
 * One phase of a boss's fight (architecture 14.3). The first is where the fight starts; every later one has a trigger
 * that enters it from the phase before ({@link BossPhaseMachine}).
 *
 * @param id the phase's id, unique within its boss
 * @param name the translation key of its name, which the boss bar shows after the boss's name
 * @param enterWhen what enters it from the phase before; empty for the first phase
 * @param onEnter spells the boss casts at once when the phase begins, without their cooldowns
 * @param abilities what the boss casts while the phase lasts, most important first
 */
public record BossPhase(String id, String name, Optional<PhaseTrigger> enterWhen, List<ResourceKey<Spell>> onEnter,
        List<BossAbility> abilities) {
    public static final Codec<BossPhase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("id").forGetter(BossPhase::id),
                    Codec.STRING.fieldOf("name").forGetter(BossPhase::name),
                    PhaseTrigger.CODEC.optionalFieldOf("enter_when").forGetter(BossPhase::enterWhen),
                    ResourceKey.codec(SpellRegistries.SPELL).listOf().optionalFieldOf("on_enter", List.of())
                            .forGetter(BossPhase::onEnter),
                    BossAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(BossPhase::abilities))
            .apply(instance, BossPhase::new));

    public BossPhase {
        onEnter = List.copyOf(onEnter);
        abilities = List.copyOf(abilities);
    }

    /** Its name, as the boss bar shows it. */
    public Component displayName() {
        return Component.translatable(name);
    }
}
