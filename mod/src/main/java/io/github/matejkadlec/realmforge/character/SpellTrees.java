package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Specializations and their spell trees (C5, RLM-333, architecture 10.1). A new character knows only their race's
 * spell. They pick a specialization in the spellbook's Spell Tree tab, which teaches the tree's first spell free at
 * rank 1, and then spend a point per rank ({@link SpellPoints}); every request comes through a payload and is checked
 * here. The reads use synced data, so the client's window shows the same.
 */
public final class SpellTrees {
    /** What a request to pick a specialization led to. Only {@link #PICKED} changes anything. */
    public enum PickResult {
        PICKED,
        NO_CHARACTER,
        /** The character has one: changing it is the Scroll of Rebirth's (RLM-228) or a QA command's. */
        ALREADY_PICKED,
        /** The character's class has no specialization with this id. */
        UNKNOWN,
        /** Its tree does not exist yet (M3). */
        NOT_PLAYABLE
    }

    private SpellTrees() {
    }

    /** The specialization the player picked, in their class's data: empty before they pick or without a character. */
    public static Optional<Specialization> chosen(Player player) {
        return Characters.data(player).specialization().flatMap(id -> Characters.mainClass(player)
                .flatMap(playerClass -> playerClass.value().specialization(id)));
    }

    /** The points the player has spent in their tree: none before they pick. */
    public static int spent(Player player) {
        return chosen(player).map(tree -> spent(tree, player.getData(SpellAttachments.SPELLBOOK).spells())).orElse(0);
    }

    static int spent(Specialization tree, Map<ResourceKey<Spell>, Integer> known) {
        List<ResourceKey<Spell>> spells = tree.spells().stream().map(TreeSpell::key).toList();
        return SpellPoints.spent(known, spells, tree.firstSpellKey());
    }

    /** The ranks the player knows in their tree, the free one included: none before they pick. */
    public static int ranks(Player player) {
        Map<ResourceKey<Spell>, Integer> known = player.getData(SpellAttachments.SPELLBOOK).spells();
        return chosen(player).map(tree -> tree.spells().stream()
                .mapToInt(taught -> known.getOrDefault(taught.key(), 0)).sum()).orElse(0);
    }

    /** The points the player has left, never below 0; none without a character. */
    public static int unspent(Player player) {
        if (!Characters.exists(player)) {
            return 0;
        }
        return Math.max(0, SpellPoints.earned(Characters.level(player)) - spent(player));
    }

    /** The picking rules, without changing anything. */
    public static PickResult check(CharacterData character, Optional<PlayerClass> playerClass, String id) {
        if (!character.exists()) {
            return PickResult.NO_CHARACTER;
        }
        if (character.specialization().isPresent()) {
            return PickResult.ALREADY_PICKED;
        }
        Optional<Specialization> specialization = playerClass.flatMap(entry -> entry.specialization(id));
        if (specialization.isEmpty()) {
            return PickResult.UNKNOWN;
        }
        return specialization.get().playable() ? PickResult.PICKED : PickResult.NOT_PLAYABLE;
    }

    /** Picks the specialization if the rules allow it and teaches its first spell free; returns what happened. */
    public static PickResult pick(ServerPlayer player, String id) {
        CharacterData character = Characters.data(player);
        Optional<PlayerClass> playerClass = Characters.mainClass(player).map(Holder::value);
        PickResult result = check(character, playerClass, id);
        if (result != PickResult.PICKED) {
            Realmforge.LOGGER.debug("Refused {}'s specialization {}: {}", player.getPlainTextName(), id, result);
            return result;
        }
        player.setData(CharacterAttachments.CHARACTER, character.withSpecialization(id));
        chosen(player).flatMap(Specialization::firstSpellKey)
                .filter(first -> Spells.knownLevel(player, first).isEmpty())
                .flatMap(first -> Spells.get(player.registryAccess(), first)).ifPresent(first -> {
                    Spells.learn(player, first, 1);
                    player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_SPELL_LEARNED,
                            Component.translatable(first.value().name())).withStyle(ChatFormatting.YELLOW));
                });
        return result;
    }

    /** Spends a point on the next rank of {@code spell} if the rules allow it; returns what happened. */
    public static SpellPoints.Result spend(ServerPlayer player, ResourceKey<Spell> spell) {
        SpellPoints.Result result = check(player, spell);
        if (result == SpellPoints.Result.SPENT) {
            Spells.get(player.registryAccess(), spell).ifPresent(reference -> Spells.learn(player, reference,
                    Spells.knownLevel(player, spell).orElse(0) + 1));
        } else {
            Realmforge.LOGGER.debug("Refused {}'s spell point for {}: {}", player.getPlainTextName(),
                    spell.identifier(), result);
        }
        return result;
    }

    /** What {@link #spend} would do for {@code spell}, without changing anything; the window asks this too. */
    public static SpellPoints.Result check(Player player, ResourceKey<Spell> spell) {
        Optional<Specialization> tree = chosen(player);
        if (tree.isEmpty()) {
            return SpellPoints.Result.NO_SPECIALIZATION;
        }
        Optional<TreeSpell> taught = tree.get().spell(spell);
        if (taught.isEmpty()) {
            return SpellPoints.Result.NOT_IN_TREE;
        }
        boolean requirementMet = taught.get().requires().flatMap(Holder::unwrapKey)
                .map(required -> Spells.knownLevel(player, required).isPresent()).orElse(true);
        return SpellPoints.check(Characters.level(player), unspent(player), Spells.knownLevel(player, spell).orElse(0),
                taught.get().spell().value().maxLevel(), taught.get().level(), requirementMet);
    }

    /**
     * Returns every point the player spent: the tree's spells are forgotten and leave the bar, but for the first spell,
     * which stays at its free rank 1. The specialization stays. Returns how many came back (0: none spent).
     */
    public static int reset(ServerPlayer player) {
        Optional<Specialization> tree = chosen(player);
        if (tree.isEmpty()) {
            return 0;
        }
        int spent = spent(player);
        Optional<ResourceKey<Spell>> first = tree.get().firstSpellKey();
        for (TreeSpell taught : tree.get().spells()) {
            if (first.filter(taught.key()::equals).isEmpty()) {
                Spells.forget(player, taught.key());
            }
        }
        first.flatMap(key -> Spells.get(player.registryAccess(), key))
                .ifPresent(spell -> Spells.learn(player, spell, 1));
        return spent;
    }

    /**
     * Returns every spent point, with a message, when the player's level no longer pays for them or no longer opens a
     * spell's row (a command lowered it, a chapter was revoked), as stat points do. Returns whether they came back.
     */
    static boolean fit(ServerPlayer player) {
        Optional<Specialization> tree = chosen(player);
        if (tree.isEmpty()) {
            return false;
        }
        int level = Characters.level(player);
        boolean aboveItsRow = tree.get().spells().stream().anyMatch(taught -> taught.level() > level
                && Spells.knownLevel(player, taught.key()).isPresent());
        if (spent(player) <= SpellPoints.earned(level) && !aboveItsRow) {
            return false;
        }
        reset(player);
        player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_SPELL_POINTS_RETURNED, unspent(player))
                .withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /**
     * After a level-up from {@code oldLevel}, which may jump several levels: a line for a row that opened, and one for
     * the points to spend.
     */
    static void announceLevelUp(ServerPlayer player, int oldLevel, int level) {
        chosen(player).filter(tree -> tree.spells().stream()
                        .anyMatch(taught -> taught.level() > oldLevel && taught.level() <= level))
                .ifPresent(tree -> player.sendSystemMessage(Component.translatable(
                        GeneratedTexts.MESSAGE_SPELL_TREE_ROW_OPEN).withStyle(ChatFormatting.YELLOW)));
        if (Characters.data(player).specialization().isEmpty()) {
            remindToPick(player);
            return;
        }
        int unspent = unspent(player);
        if (unspent > 0) {
            player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_SPELL_POINTS_UNSPENT, unspent,
                    Component.keybind(GeneratedTexts.KEY_SPELLBOOK)).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** A line saying where to pick a specialization, for a character who has not, when their class has one to pick. */
    static void remindToPick(ServerPlayer player) {
        boolean canPick = Characters.data(player).specialization().isEmpty() && Characters.mainClass(player)
                .map(playerClass -> playerClass.value().specializations().stream().anyMatch(Specialization::playable))
                .orElse(false);
        if (canPick) {
            player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_SPECIALIZATION_PICK,
                    Component.keybind(GeneratedTexts.KEY_SPELLBOOK)).withStyle(ChatFormatting.YELLOW));
        }
    }
}
