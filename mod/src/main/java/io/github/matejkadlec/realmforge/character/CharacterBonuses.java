package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * The attribute modifiers a player's race and classes give (architecture 8.2, 10.1), the same at every level (levels
 * give stat points instead, {@link StatPoints}), the race's racial passive while the main hand holds one of its
 * weapons (C4: the Human's Sword Specialization), and the {@link StatSource} that hands them to {@code stats}. The
 * pipeline computes the same ({@code bonuses} and {@code passive_bonuses} in
 * {@code tools/realmforge_tools/schema/character.py}), and a GameTest compares the two.
 *
 * <p>Modifier ids are unique per entry and position: {@code realmforge:race/<namespace>/<n>/<path>} for the n-th
 * modifier of the race {@code <namespace>:<path>}, {@code realmforge:race/<namespace>/passive/<n>/<path>} for its
 * passive's, and {@code realmforge:class/...} for a class. An entry's path may contain slashes, so it comes last, as in
 * the stat rules' ids. A held item changes the passive's part: an equipment change marks the stats for a recompute
 * at the end of the same tick, as vanilla applies the weapon's own modifiers then.
 */
public final class CharacterBonuses {
    /** The path prefix of every modifier races write, in our namespace. */
    public static final String RACE_PREFIX = "race/";
    /** The path prefix of every modifier classes write, in our namespace. */
    public static final String CLASS_PREFIX = "class/";

    /** Hands {@code stats} the race and class modifiers of each player it recomputes. */
    static final StatSource SOURCE = new StatSource() {
        @Override
        public boolean owns(Identifier id) {
            return isCharacterModifier(id);
        }

        @Override
        public List<StatBonus> bonuses(ServerPlayer player) {
            return of(Characters.data(player), player.registryAccess(), player.getMainHandItem());
        }
    };
    /** The path segment of a racial passive's modifiers, after the race's namespace. */
    private static final String PASSIVE = "passive/";

    private CharacterBonuses() {
    }

    /**
     * Every modifier a character gives with an empty main hand: its race's, then each class's, in their entries' order.
     * A player without a character gets none, and a race or class whose entry no longer exists gives nothing.
     */
    public static List<StatBonus> of(CharacterData character, HolderLookup.Provider registries) {
        return of(character, registries, ItemStack.EMPTY);
    }

    /**
     * Every modifier a character gives with {@code mainHand} in the main hand: its race's, its racial passive's while
     * the main hand holds one of the passive's weapons, then each class's, in their entries' order.
     */
    public static List<StatBonus> of(CharacterData character, HolderLookup.Provider registries, ItemStack mainHand) {
        List<StatBonus> bonuses = new ArrayList<>();
        if (!character.exists()) {
            return bonuses;
        }
        ResourceKey<Race> race = character.race().orElseThrow();
        registries.lookupOrThrow(CharacterRegistries.RACE).get(race).ifPresent(entry -> {
            bonuses.addAll(bonuses(RACE_PREFIX, race.identifier(), entry.value().modifiers()));
            entry.value().racialPassive().filter(passive -> passive.appliesWith(mainHand)).ifPresent(passive ->
                    bonuses.addAll(passiveBonuses(race.identifier(), passive.modifiers())));
        });
        for (ResourceKey<PlayerClass> playerClass : character.classes()) {
            registries.lookupOrThrow(CharacterRegistries.CLASS).get(playerClass).ifPresent(entry ->
                    bonuses.addAll(bonuses(CLASS_PREFIX, playerClass.identifier(), entry.value().modifiers())));
        }
        return bonuses;
    }

    /** The modifiers of one race or class, skipping any whose amount is 0 (the pipeline refuses those). */
    public static List<StatBonus> bonuses(String prefix, Identifier entry, List<CharacterModifier> modifiers) {
        return bonuses(modifiers, n -> modifierId(prefix, entry, n));
    }

    /** The modifiers of the racial passive of {@code race} while it applies, as {@link #bonuses} gives a race's. */
    public static List<StatBonus> passiveBonuses(Identifier race, List<CharacterModifier> modifiers) {
        return bonuses(modifiers, n -> passiveModifierId(race, n));
    }

    private static List<StatBonus> bonuses(List<CharacterModifier> modifiers, IntFunction<Identifier> ids) {
        List<StatBonus> bonuses = new ArrayList<>();
        for (int n = 0; n < modifiers.size(); n++) {
            CharacterModifier modifier = modifiers.get(n);
            if (modifier.amount() != 0) {
                bonuses.add(new StatBonus(modifier.attribute(), new AttributeModifier(ids.apply(n),
                        modifier.amount(), modifier.operation())));
            }
        }
        return bonuses;
    }

    /** The id of the n-th modifier of a race ({@link #RACE_PREFIX}) or class ({@link #CLASS_PREFIX}). */
    public static Identifier modifierId(String prefix, Identifier entry, int n) {
        return Realmforge.id(prefix + entry.getNamespace() + "/" + n + "/" + entry.getPath());
    }

    /** The id of the n-th modifier of the racial passive of {@code race}: {@code realmforge:race/<ns>/passive/<n>/<path>}. */
    public static Identifier passiveModifierId(Identifier race, int n) {
        return Realmforge.id(RACE_PREFIX + race.getNamespace() + "/" + PASSIVE + n + "/" + race.getPath());
    }

    /** Whether {@code id} names a modifier races or classes write, so a recompute may replace or remove it. */
    public static boolean isCharacterModifier(Identifier id) {
        return id.getNamespace().equals(Realmforge.MOD_ID)
                && (id.getPath().startsWith(RACE_PREFIX) || id.getPath().startsWith(CLASS_PREFIX));
    }
}
