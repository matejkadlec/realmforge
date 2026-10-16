package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Stat points (architecture 10.2; Matěj, 2026-10-15, RLM-196): instead of race and class stats growing with the level,
 * every level from 2 on earns primary stat points, which the player spends on the character sheet.
 *
 * <ul>
 *   <li><b>Earned</b> points are derived, never saved: the curve {@code realmforge:stat_points_per_level} gives the
 *   points of each level reached ({@link #at}), and a player has earned those of every level from 2 to their level
 *   ({@link #earned}). The {@code character} attachment saves only what was spent on each stat, so the unspent points
 *   are the earned ones less the spent ones ({@link #unspent}). A character from before stat points existed therefore
 *   has every point of its levels unspent, once, and a rebalanced curve applies at once.</li>
 *   <li><b>Spending</b> ({@link #spend}) puts one point into a primary stat, from the sheet's {@code +} through
 *   {@code SpendStatPointPayload}; the server checks every request. A point is spent for good until the player
 *   unlearns them all.</li>
 *   <li><b>Unlearning</b> ({@link #unlearn}): an item in the tag {@link #UNLEARN_ITEMS} (the Scroll of Oblivion, whose
 *   {@code use: unlearn_stat_points} the pipeline turns into that tag) returns every spent point and is used up. With
 *   nothing spent it does nothing and is kept.</li>
 *   <li><b>A level that falls</b> below what was spent (a command, a revoked chapter) returns every spent point
 *   ({@link #fit}), which {@link Leveling} checks on each update.</li>
 *   <li>Spent points reach the player as modifiers from their own {@link StatSource}: one per stat, adding its points,
 *   with the id {@code realmforge:stat_points/<namespace>/<path>} of the stat {@code <namespace>:<path>}.</li>
 * </ul>
 */
public final class StatPoints {
    /** The path prefix of every modifier spent points write, in our namespace. */
    public static final String PREFIX = "stat_points/";

    /**
     * {@code realmforge:use/unlearn_stat_points}: the items whose use returns every spent point, written by the
     * pipeline from the items with {@code use: unlearn_stat_points}.
     */
    public static final TagKey<Item> UNLEARN_ITEMS = TagKey.create(Registries.ITEM,
            Realmforge.id("use/unlearn_stat_points"));

    /** Hands {@code stats} the modifiers of each player's spent points. */
    static final StatSource SOURCE = new StatSource() {
        @Override
        public boolean owns(Identifier id) {
            return isStatPointModifier(id);
        }

        @Override
        public List<StatBonus> bonuses(ServerPlayer player) {
            return of(Characters.data(player));
        }
    };

    /** What a request to spend a point led to. Only {@link #SPENT} changes anything. */
    public enum Result {
        SPENT,
        /** The player has no character, so no points. */
        NO_CHARACTER,
        /** Points go only into primary stats. */
        NOT_A_PRIMARY_STAT,
        NO_POINTS
    }

    private StatPoints() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, StatPoints::onRightClickItem);
    }

    /** The points reaching {@code level} gives: the curve's value there, half up, at least 0; none at level 1. */
    public static int at(Curve curve, int level) {
        return level > 1 ? (int) Math.clamp(Math.round(curve.valueAt(level)), 0, Integer.MAX_VALUE) : 0;
    }

    /** The points a player of {@code level} has earned: those of every level from 2 to {@code level}. */
    public static int earned(Curve curve, int level) {
        long total = 0;
        for (int reached = 2; reached <= level; reached++) {
            total += at(curve, reached);
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /** The points a player of {@code level} has earned under the registries' curve; none if a data pack removed it. */
    public static int earned(HolderLookup.Provider registries, int level) {
        return Leveling.curve(registries, GeneratedCurves.STAT_POINTS_PER_LEVEL).map(curve -> earned(curve, level))
                .orElse(0);
    }

    /**
     * The player's unspent points: what their level earned less what they spent, never below 0; none without a
     * character. Reads synced data only, so the client's character sheet shows the same.
     */
    public static int unspent(Player player) {
        CharacterData character = Characters.data(player);
        if (!character.exists()) {
            return 0;
        }
        return Math.max(0, earned(player.registryAccess(), Characters.level(player)) - character.spentStatPoints());
    }

    /** Whether points may go into the attribute: our primary stats only. */
    public static boolean isPrimaryStat(ResourceKey<Attribute> stat) {
        return GeneratedStats.PRIMARY.stream().anyMatch(primary -> primary.is(stat));
    }

    /** The spending rules, without changing anything: what {@link #spend} would do with {@code unspent} points. */
    public static Result check(CharacterData character, int unspent, ResourceKey<Attribute> stat) {
        if (!character.exists()) {
            return Result.NO_CHARACTER;
        }
        if (!isPrimaryStat(stat)) {
            return Result.NOT_A_PRIMARY_STAT;
        }
        return unspent > 0 ? Result.SPENT : Result.NO_POINTS;
    }

    /**
     * Spends one of the player's points on {@code stat} if the rules allow it and recomputes the stats; returns what
     * happened.
     */
    public static Result spend(ServerPlayer player, ResourceKey<Attribute> stat) {
        CharacterData character = Characters.data(player);
        Result result = check(character, unspent(player), stat);
        if (result == Result.SPENT) {
            player.setData(CharacterAttachments.CHARACTER, character.plusStatPoint(stat));
            CharacterStats.recompute(player);
        } else {
            Realmforge.LOGGER.debug("Refused {}'s stat point for {}: {}", player.getPlainTextName(), stat.identifier(),
                    result);
        }
        return result;
    }

    /** Returns every point the player spent and recomputes the stats; returns how many came back (0: none spent). */
    public static int unlearn(ServerPlayer player) {
        CharacterData character = Characters.data(player);
        int spent = character.spentStatPoints();
        if (spent > 0) {
            player.setData(CharacterAttachments.CHARACTER, character.withStatPoints(Map.of()));
            CharacterStats.recompute(player);
        }
        return spent;
    }

    /**
     * Returns every spent point, with a message, when the player's level no longer earns as many as they spent (a
     * command lowered it, a chapter was revoked, the curve gives fewer). Changes nothing otherwise. The caller
     * recomputes the stats; returns whether the points came back.
     */
    static boolean fit(ServerPlayer player) {
        CharacterData character = Characters.data(player);
        int spent = character.spentStatPoints();
        if (spent == 0 || spent <= earned(player.registryAccess(), Characters.level(player))) {
            return false;
        }
        player.setData(CharacterAttachments.CHARACTER, character.withStatPoints(Map.of()));
        player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_STAT_POINTS_RETURNED, unspent(player))
                .withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /**
     * The modifiers a character's spent points give: one per primary stat with points, adding them, in id order. A
     * stat that is no longer a primary stat gives nothing.
     */
    public static List<StatBonus> of(CharacterData character) {
        List<StatBonus> bonuses = new ArrayList<>();
        character.statPoints().entrySet().stream()
                .filter(entry -> isPrimaryStat(entry.getKey()))
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                .forEach(entry -> BuiltInRegistries.ATTRIBUTE.get(entry.getKey()).ifPresent(stat ->
                        bonuses.add(new StatBonus(stat, new AttributeModifier(modifierId(entry.getKey()),
                                entry.getValue(), AttributeModifier.Operation.ADD_VALUE)))));
        return bonuses;
    }

    /** The id of the modifier of the points spent on {@code stat}: {@code realmforge:stat_points/<ns>/<path>}. */
    public static Identifier modifierId(ResourceKey<Attribute> stat) {
        return Realmforge.id(PREFIX + stat.identifier().getNamespace() + "/" + stat.identifier().getPath());
    }

    /** Whether {@code id} names a modifier of spent points, so a recompute may replace or remove it. */
    public static boolean isStatPointModifier(Identifier id) {
        return id.getNamespace().equals(Realmforge.MOD_ID) && id.getPath().startsWith(PREFIX);
    }

    /**
     * Right-clicking an item of {@link #UNLEARN_ITEMS}: on both sides, so the client swings the arm only when points
     * come back (the character attachment is synced); the server returns them, uses the item up and says so.
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(UNLEARN_ITEMS)) {
            return;
        }
        Player player = event.getEntity();
        boolean spent = Characters.data(player).spentStatPoints() > 0;
        event.setCanceled(true);
        event.setCancellationResult(spent ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!spent) {
            serverPlayer.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_STAT_POINTS_NONE_SPENT));
            return;
        }
        unlearn(serverPlayer);
        stack.consume(1, serverPlayer);
        serverPlayer.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_STAT_POINTS_UNLEARNED,
                unspent(serverPlayer)).withStyle(ChatFormatting.YELLOW));
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
