package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teaches a character their class's spells by level and their race's racial spell (C4, architecture 10.1). A class
 * spell is learned at rank 1 once the character reaches its level, into the spell bar's first empty slot
 * ({@link Spells#learn}); the racial spell from creation on, into the racial slot beside the bar (X). A spell already
 * known keeps its rank, and a level that falls (a revoked chapter) takes no spell away. Every class starts with one
 * spell (decision D4), and chapter N allows 4 x N of them (the pipeline checks both).
 *
 * <p>Creation teaches with a chat line per spell, a level-up too ({@link Leveling}); a login and the quiet level
 * changes of commands catch up silently, so a character created before its class had spells gets them.
 */
public final class ClassSpells {
    private ClassSpells() {
    }

    /**
     * Teaches the player every class spell of their level or below that they do not know, and their racial spell.
     * With {@code announce}, each new class spell gets a chat line. Nothing for a player without a character.
     */
    public static void teach(ServerPlayer player, boolean announce) {
        CharacterData character = Characters.data(player);
        if (!character.exists()) {
            return;
        }
        Registry<Spell> spells = player.registryAccess().lookupOrThrow(SpellRegistries.SPELL);
        int level = Characters.level(player);
        Characters.mainClass(player).ifPresent(playerClass -> due(playerClass.value().spells(), level).forEach(
                taught -> reference(spells, taught.spell()).ifPresent(spell -> {
                    if (Spells.knownLevel(player, spell.key()).isEmpty()) {
                        Spells.learn(player, spell, 1);
                        if (announce) {
                            player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_SPELL_LEARNED,
                                    Component.translatable(spell.value().name())).withStyle(ChatFormatting.YELLOW));
                        }
                    }
                })));
        character.race().flatMap(key -> player.registryAccess().lookupOrThrow(CharacterRegistries.RACE).get(key))
                .flatMap(race -> race.value().racialSpell())
                .flatMap(spell -> reference(spells, spell))
                .ifPresent(spell -> Spells.learnRacial(player, spell));
    }

    /** The class spells taught at {@code level} or below, by level and then in the class's order. */
    public static List<ClassSpell> due(List<ClassSpell> spells, int level) {
        return spells.stream().filter(taught -> taught.level() <= level)
                .sorted(Comparator.comparingInt(ClassSpell::level)).toList();
    }

    /** The registry's own reference to {@code spell}, which {@link Spells#learn} takes. */
    private static Optional<Holder.Reference<Spell>> reference(Registry<Spell> spells, Holder<Spell> spell) {
        return spell.unwrapKey().flatMap(spells::get);
    }
}
