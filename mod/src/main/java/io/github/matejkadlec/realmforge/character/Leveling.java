package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.progression.Chapter;
import io.github.matejkadlec.realmforge.progression.Chapters;
import io.github.matejkadlec.realmforge.progression.ProgressionRegistries;
import io.github.matejkadlec.realmforge.progression.ProgressionTriggers;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Leveling (architecture 10.2): character XP, the level derived from it and the level cap, the XP kills give, and what
 * a level-up does. Character XP is separate from vanilla experience (decision D3): it is never spent, and vanilla XP
 * stays the currency of enchanting and anvils.
 *
 * <ul>
 *   <li>The level is {@link XpTable#levelFor} of the player's total XP under their level cap, written into the
 *   {@code realmforge:level} attachment that every system reads.</li>
 *   <li>XP stops at the cap (Matěj, 2026-10-15): the total never exceeds the XP of the cap's level
 *   ({@link XpTable#capped}). A gain that crosses it counts only up to it, a player at the cap gains nothing, and a
 *   raised cap starts them at 0 XP toward the next level. A lowered cap (a revoked chapter) cuts the total too.</li>
 *   <li>The level cap comes from the chapters the player completed ({@link Chapters#levelCap}), cached in the
 *   {@code character} attachment: recomputed when they log in and whenever a chapter's advancement is granted or
 *   revoked ({@code AdvancementProgressEvent}: a chapter's advancement has no display, and NeoForge posts
 *   {@code AdvancementEarnEvent} only for advancements with one).</li>
 *   <li>A kill of a hostile mob ({@link Enemy}) gives the player who gets vanilla's kill credit the XP of
 *   {@link KillXp}, with the victim's {@link KillXpMultiplier}, shown in the action bar. Players without a character
 *   gain no XP (architecture 10.1).</li>
 *   <li>A level-up recomputes the stats, shows a title, plays a fanfare to the player and bursts particles around
 *   them, and fires {@code realmforge:level_reached}, as does every login. Talent points and class spells join with
 *   C4 and C5.</li>
 * </ul>
 */
public final class Leveling {
    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 50;
    private static final int TITLE_FADE_OUT_TICKS = 20;

    private Leveling() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Leveling::onLogin);
        NeoForge.EVENT_BUS.addListener(AdvancementEvent.AdvancementProgressEvent.class, Leveling::onAdvancementProgress);
        // Last, and only when nothing cancelled the death (a totem, another mod's revive).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, Leveling::onLivingDeath);
    }

    /** What an {@link #update} changed. Levels and caps are equal to the old ones when nothing changed. */
    public record Change(int oldLevel, int level, int oldCap, int cap) {
        public boolean leveledUp() {
            return level > oldLevel;
        }

        public boolean capRaised() {
            return cap > oldCap;
        }
    }

    /**
     * Adds XP the player gained from a reward (a quest, {@code /realmforge xp add}), times their XP multiplier, and
     * levels them up as far as it reaches. Returns the XP gained: 0 for a player without a character or at the level
     * cap, and only the part up to the cap for a gain that crosses it.
     */
    static long reward(ServerPlayer player, long xp) {
        return add(player, Math.max(0, Math.round(xp * multiplier(player))));
    }

    /**
     * Sets the player's total character XP, at most the XP of their cap's level, and their level from it, without
     * level-up effects; false without a character.
     */
    static boolean setTotalXp(ServerPlayer player, long totalXp) {
        CharacterData data = Characters.data(player);
        if (!data.exists()) {
            return false;
        }
        player.setData(CharacterAttachments.CHARACTER, data.withTotalXp(Math.max(0, totalXp)));
        update(player, false);
        return true;
    }

    /** The XP curve from the player's registries; empty when a data pack removed it. */
    static Optional<Curve> curve(HolderLookup.Provider registries, ResourceKey<Curve> key) {
        return registries.lookupOrThrow(CoreRegistries.CURVE).get(key).map(Holder::value);
    }

    /**
     * Brings the player's level cap, XP and level up to date with their chapters: the XP is cut to the cap
     * ({@link XpTable#capped}), which also cleans a save from before XP stopped at the cap. With {@code announce}, a
     * level-up shows its effects and a raised cap its message; logins update silently. Either way a changed level
     * recomputes the stats and fires {@code realmforge:level_reached}. A player without a character is left alone: they gain no
     * XP, and creating the character updates them ({@link CharacterCreation}).
     */
    public static Change update(ServerPlayer player, boolean announce) {
        CharacterData data = Characters.data(player);
        if (!data.exists()) {
            int level = Characters.level(player);
            return new Change(level, level, data.levelCap().orElse(level), data.levelCap().orElse(level));
        }
        int cap = Chapters.levelCap(player);
        int oldCap = data.levelCap().orElse(cap);
        int oldLevel = Characters.level(player);
        Optional<Curve> curve = curve(player.registryAccess(), GeneratedCurves.XP_TO_NEXT_LEVEL);
        long totalXp = curve.map(xp -> XpTable.capped(xp, data.totalXp(), cap)).orElse(data.totalXp());
        int level = curve.map(xp -> XpTable.levelFor(xp, totalXp, cap)).orElse(oldLevel);
        if (data.levelCap().orElse(-1) != cap || totalXp != data.totalXp()) {
            player.setData(CharacterAttachments.CHARACTER, data.withLevelCap(cap).withTotalXp(totalXp));
        }
        Change change = new Change(oldLevel, level, oldCap, cap);
        if (level != oldLevel) {
            player.setData(StatsAttachments.LEVEL, new EntityLevel(level));
            CharacterStats.recompute(player);
            ProgressionTriggers.LEVEL_REACHED.get().trigger(player, level);
            if (announce && change.leveledUp()) {
                celebrate(player, level);
                if (level == cap) {
                    Chapters.next(player).ifPresent(next -> player.sendSystemMessage(Component.translatable(
                            GeneratedTexts.MESSAGE_LEVEL_CAP_REACHED, cap, chapterName(next.value()))));
                }
            }
        }
        return change;
    }

    /**
     * Adds XP as it is, without the multiplier, and levels the player up. Returns the XP gained, which {@link #update}
     * cuts at the cap: 0 for a player without a character or at the cap.
     */
    private static long add(ServerPlayer player, long xp) {
        CharacterData data = Characters.data(player);
        if (!data.exists() || xp <= 0) {
            return 0;
        }
        long before = data.totalXp();
        player.setData(CharacterAttachments.CHARACTER, data.withTotalXp(XpTable.plus(before, xp)));
        update(player, true);
        return Math.max(0, Characters.data(player).totalXp() - before);
    }

    private static double multiplier(Player player) {
        return player.getAttributeValue(GeneratedStats.XP_MULTIPLIER);
    }

    private static Component chapterName(Chapter chapter) {
        return Component.translatable(chapter.name());
    }

    /** The title, the fanfare (for the player alone) and a burst of particles everyone nearby sees. */
    private static void celebrate(ServerPlayer player, int level) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS,
                TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable(GeneratedTexts.MESSAGE_LEVEL_UP_SUBTITLE, level)));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(GeneratedTexts.MESSAGE_LEVEL_UP).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), 0.8F, 1.0F, player.getRandom().nextLong()));
        player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40,
                0.4, 0.8, 0.4, 0.3);
    }

    /** A login brings cap and level up to date silently, and tells the trigger the level, so new criteria catch up. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            update(player, false);
            ProgressionTriggers.LEVEL_REACHED.get().trigger(player, Characters.level(player));
        }
    }

    /** A chapter's advancement granted or revoked: NeoForge posts this after the progress changed. */
    private static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<ResourceKey<Chapter>> chapter = Chapters.ofAdvancement(event.getAdvancement().id());
        if (chapter.isEmpty()) {
            return;
        }
        // The raised cap is announced before the level-up it brings, whose hint may name the next chapter.
        CharacterData data = Characters.data(player);
        int cap = Chapters.levelCap(player);
        if (data.exists() && cap > data.levelCap().orElse(cap)) {
            player.registryAccess().lookupOrThrow(ProgressionRegistries.CHAPTER)
                    .get(chapter.get())
                    .ifPresent(entry -> player.sendSystemMessage(Component.translatable(
                            GeneratedTexts.MESSAGE_LEVEL_CAP_RAISED, chapterName(entry.value()), cap)
                            .withStyle(ChatFormatting.GOLD)));
        }
        Change change = update(player, true);
        Realmforge.LOGGER.debug("{}'s chapter {} changed: level {} -> {}, cap {} -> {}", player.getPlainTextName(),
                chapter.get().identifier(), change.oldLevel(), change.level(), change.oldCap(), change.cap());
    }

    /** A hostile mob's death gives its killer XP: the player with vanilla's kill credit, who must have a character. */
    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy) || victim.level().isClientSide()) {
            return;
        }
        ServerPlayer player = killer(victim, event.getSource());
        if (player == null || !Characters.exists(player)) {
            return;
        }
        int victimLevel = victim.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
        HolderLookup.Provider registries = player.registryAccess();
        Optional<Curve> base = curve(registries, GeneratedCurves.KILL_XP);
        Optional<Curve> below = curve(registries, GeneratedCurves.KILL_XP_BELOW_PLAYER);
        Optional<Curve> above = curve(registries, GeneratedCurves.KILL_XP_ABOVE_PLAYER);
        if (base.isEmpty() || below.isEmpty() || above.isEmpty()) {
            return;
        }
        long xp = add(player, KillXp.of(base.get(), below.get(), above.get(), victimLevel, Characters.level(player),
                killXpMultiplier(registries, victim), multiplier(player)));
        if (xp > 0) {
            player.sendOverlayMessage(Component.translatable(GeneratedTexts.MESSAGE_XP_GAINED, xp)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /**
     * The victim's kill XP multiplier (its rank): its type's {@code realmforge:kill_xp_multiplier} entry, by size for a
     * slime or magma cube, or {@link KillXpMultiplier#DEFAULT} when its type has none.
     */
    public static double killXpMultiplier(HolderLookup.Provider registries, LivingEntity victim) {
        OptionalInt size = victim instanceof Slime slime ? OptionalInt.of(slime.getSize()) : OptionalInt.empty();
        return registries.lookupOrThrow(CharacterRegistries.KILL_XP_MULTIPLIER).listElements()
                .map(Holder::value)
                .filter(entry -> entry.entity().value() == victim.getType())
                .findFirst()
                .map(entry -> entry.at(size))
                .orElse(KillXpMultiplier.DEFAULT);
    }

    /**
     * The player a kill counts for: the one who dealt the killing blow (directly or by a projectile), else the one
     * vanilla credits with it, a player who hurt the victim in the last 5 seconds or owns the pet that did, as for
     * vanilla's experience orbs.
     */
    @Nullable
    private static ServerPlayer killer(LivingEntity victim, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return victim.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
    }
}
