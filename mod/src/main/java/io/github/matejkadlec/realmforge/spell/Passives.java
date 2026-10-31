package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * What a player's passive spells do now (RLM-541; architecture 11.1): each passive spell the player knows, at its
 * rank, while its weapon condition holds ({@link Passive#appliesWith}). Its modifiers reach the player's attributes
 * through {@link #SOURCE}, which {@code stats} recomputes like race and class (architecture 8.2): a held item changes
 * them at the end of the same tick, and learning a spell marks the stats for a recompute. The damage engine reads the
 * rest per hit: {@link #damageBonus} for a spell's hit (the passives of system C's (1 + School Power + passives)),
 * {@link #critChanceAgainst} against its target, and {@code combat} asks {@link #blockCooldownReductionTicks} for a
 * raised two-handed weapon's cooldown. Their procs reach the proc engine through {@link #procs} (RLM-540), each with its
 * internal cooldown per target and its id {@code realmforge:spell/<namespace>/proc/<n>/<path>}. Mobs know no spells, so their passives give nothing. Works on the client too,
 * from the synced spellbook, for the tooltips.
 *
 * <p>Modifier ids are unique per spell and position: {@code realmforge:spell/<namespace>/<n>/<path>} for the n-th
 * modifier of the passive spell {@code <namespace>:<path>}, its path last as in the stat rules' ids.
 */
public final class Passives {
    /** The path prefix of every modifier passive spells write, in our namespace. */
    public static final String PREFIX = "spell/";

    /** Hands {@code stats} the passive spells' modifiers of each player it recomputes. */
    public static final StatSource SOURCE = new StatSource() {
        @Override
        public boolean owns(Identifier id) {
            return isPassiveModifier(id);
        }

        @Override
        public List<StatBonus> bonuses(ServerPlayer player) {
            return Passives.bonuses(player);
        }
    };

    private Passives() {
    }

    /** A passive spell the player knows and whose condition holds, at its rank. */
    private record Known(Holder.Reference<Spell> spell, Passive passive, int level) {
    }

    /** The passive spells {@code entity} knows whose condition holds now, in the spellbook's order; none for a mob. */
    private static List<Known> active(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return List.of();
        }
        List<Known> known = new ArrayList<>();
        for (Map.Entry<ResourceKey<Spell>, Integer> entry : player.getData(SpellAttachments.SPELLBOOK).spells()
                .entrySet()) {
            Optional<Holder.Reference<Spell>> spell = Spells.get(player.registryAccess(), entry.getKey());
            if (spell.isEmpty() || spell.get().value().passive().isEmpty()) {
                continue;
            }
            Passive passive = spell.get().value().passive().get();
            if (passive.appliesWith(player.getMainHandItem())) {
                known.add(new Known(spell.get(), passive, Math.min(entry.getValue(), spell.get().value().maxLevel())));
            }
        }
        return known;
    }

    /** The share {@code caster}'s passive spells add to {@code spell}'s hits: Improved Frostbolt's 15 % at rank 3. */
    public static double damageBonus(LivingEntity caster, ResourceKey<Spell> spell) {
        return active(caster).stream().mapToDouble(known -> known.passive().damageFor(spell, known.level())).sum();
    }

    /** The crit chance {@code caster}'s passive spells add to its spell hits on {@code target}: Shatter's on Frozen. */
    public static double critChanceAgainst(LivingEntity caster, LivingEntity target) {
        return active(caster).stream().mapToDouble(known -> known.passive().critChanceAgainst(target, known.level()))
                .sum();
    }

    /** How many ticks shorter {@code player}'s two-handed block cooldown is (architecture 9.3). */
    public static int blockCooldownReductionTicks(Player player) {
        return active(player).stream().mapToInt(known -> known.passive().blockCooldownReductionAt(known.level()))
                .sum();
    }

    /**
     * The procs {@code holder}'s passive spells give now, at their ranks (RLM-540): a {@link Procs.Source}. Each runs its
     * internal cooldown on every target apart; one that casts a spell casts it at the passive's rank.
     */
    public static List<Proc> procs(LivingEntity holder) {
        List<Proc> procs = new ArrayList<>();
        for (Known known : active(holder)) {
            List<Passive.Proc> own = known.passive().procs();
            for (int n = 0; n < own.size(); n++) {
                Passive.Proc proc = own.get(n);
                procs.add(new Proc(procId(known.spell().key().identifier(), n), proc.trigger(),
                        proc.chanceAt(known.level()), proc.cooldownTicks(), proc.actionAt(known.level()),
                        proc.effect().flatMap(Holder::unwrapKey).map(ResourceKey::identifier),
                        proc.spell().map(ResourceKey::identifier), true));
            }
        }
        return procs;
    }

    /** The id of the n-th proc of the passive spell {@code spell}: {@code realmforge:spell/<ns>/proc/<n>/<path>}. */
    public static Identifier procId(Identifier spell, int n) {
        return Realmforge.id(PREFIX + spell.getNamespace() + "/proc/" + n + "/" + spell.getPath());
    }

    /** The modifiers {@code player}'s passive spells give now, skipping any whose amount is 0 at the rank. */
    public static List<StatBonus> bonuses(Player player) {
        List<StatBonus> bonuses = new ArrayList<>();
        for (Known known : active(player)) {
            List<Passive.Modifier> modifiers = known.passive().modifiers();
            for (int n = 0; n < modifiers.size(); n++) {
                Passive.Modifier modifier = modifiers.get(n);
                double amount = modifier.amountAt(known.level());
                if (amount != 0) {
                    bonuses.add(new StatBonus(modifier.attribute(), new AttributeModifier(
                            modifierId(known.spell().key().identifier(), n), amount, modifier.operation())));
                }
            }
        }
        return bonuses;
    }

    /** The id of the n-th modifier of the passive spell {@code spell}: {@code realmforge:spell/<ns>/<n>/<path>}. */
    public static Identifier modifierId(Identifier spell, int n) {
        return Realmforge.id(PREFIX + spell.getNamespace() + "/" + n + "/" + spell.getPath());
    }

    /** Whether {@code id} names a modifier passive spells write, so a recompute may replace or remove it. */
    public static boolean isPassiveModifier(Identifier id) {
        return id.getNamespace().equals(Realmforge.MOD_ID) && id.getPath().startsWith(PREFIX);
    }
}
