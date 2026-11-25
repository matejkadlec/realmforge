package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/**
 * What the spellbook's Spell Tree tab shows (C5, RLM-333, RLM-631, architecture 18), from synced data and free of
 * client code, so GameTests can read the same states the window draws.
 *
 * @param unspent the player's points left
 * @param picked  whether the player has picked a specialization
 * @param trees   the class's specializations in its order, with those that come later
 */
public record SpellTreeView(int unspent, boolean picked, List<Tree> trees) {
    /** What a click on a tree's spell would do, or why it would do nothing. */
    public enum State {
        LEARNABLE,
        MAX_RANK,
        /** Too few points are spent in the tree to open the spell's row. */
        ROW_CLOSED,
        NEEDS_SPELL,
        NO_POINTS,
        /** No specialization picked yet: picking this tree would make the spell learnable. */
        NEEDS_PICK,
        /** No specialization picked yet: picking this tree teaches this spell free. */
        FIRST_FREE,
        /** The player picked another specialization. */
        OTHER_TREE
    }

    /**
     * A spell in the window.
     *
     * @param spell     the spell
     * @param row       its row, from 0
     * @param column    its column, from 0
     * @param rowPoints the points that must be spent in the tree to open its row
     * @param rank      the rank the player knows it at, 0 when they do not
     * @param state     what a click would do
     * @param requires  the spells its arrows need
     * @param missing   those of them the player does not know yet
     */
    public record Cell(Holder<Spell> spell, int row, int column, int rowPoints, int rank, State state,
            List<Holder<Spell>> requires, List<Holder<Spell>> missing) {
    }

    /**
     * A specialization in the window.
     *
     * @param specialization its data
     * @param chosen         whether the player picked it
     * @param spent          the points spent in it, the first spell's free rank aside; 0 unless chosen
     * @param rowPoints      the points that open each row, top to bottom: 0, 5, 10, ...
     * @param cells          its spells
     */
    public record Tree(Specialization specialization, boolean chosen, int spent, List<Integer> rowPoints,
            List<Cell> cells) {
    }

    /** The view for {@code player}: no trees without a character. */
    public static SpellTreeView of(Player player) {
        Optional<String> picked = Characters.data(player).specialization();
        List<Specialization> specializations = Characters.mainClass(player)
                .map(playerClass -> playerClass.value().specializations()).orElse(List.of());
        Map<ResourceKey<Spell>, Integer> known = player.getData(SpellAttachments.SPELLBOOK).spells();
        List<Tree> trees = new ArrayList<>();
        for (Specialization specialization : specializations) {
            boolean chosen = picked.filter(specialization.id()::equals).isPresent();
            int rows = specialization.spells().stream().mapToInt(TreeSpell::row).max().orElse(0);
            List<Integer> rowPoints = IntStream.rangeClosed(1, rows).map(SpellPoints::rowPoints).boxed().toList();
            List<Cell> cells = new ArrayList<>();
            for (TreeSpell taught : specialization.spells()) {
                List<Holder<Spell>> missing = taught.requires().stream()
                        .filter(required -> !known.containsKey(required.unwrapKey().orElseThrow())).toList();
                cells.add(new Cell(taught.spell(), taught.row() - 1, taught.column() - 1,
                        SpellPoints.rowPoints(taught.row()), Spells.knownLevel(player, taught.key()).orElse(0),
                        state(player, specialization, taught, picked.isPresent(), chosen), taught.requires(),
                        missing));
            }
            int spent = chosen ? SpellTrees.spent(specialization, known) : 0;
            trees.add(new Tree(specialization, chosen, spent, rowPoints, List.copyOf(cells)));
        }
        return new SpellTreeView(SpellTrees.unspent(player), picked.isPresent(), List.copyOf(trees));
    }

    private static State state(Player player, Specialization specialization, TreeSpell taught, boolean picked,
            boolean chosen) {
        if (!picked) {
            return specialization.firstSpellKey().filter(taught.key()::equals).isPresent() ? State.FIRST_FREE
                    : State.NEEDS_PICK;
        }
        if (!chosen) {
            return State.OTHER_TREE;
        }
        return switch (SpellTrees.check(player, taught.key())) {
            case SPENT -> State.LEARNABLE;
            case MAX_RANK -> State.MAX_RANK;
            case ROW_CLOSED -> State.ROW_CLOSED;
            case NEEDS_SPELL -> State.NEEDS_SPELL;
            case NO_POINTS -> State.NO_POINTS;
            case NO_SPECIALIZATION, NOT_IN_TREE -> State.OTHER_TREE;
        };
    }
}
