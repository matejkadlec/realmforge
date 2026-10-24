package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.combat.InCombat;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The scrolls (architecture 10.2; Matěj, 2026-10-24, RLM-348): like Waystones' Warp Scroll, a scroll's right click
 * starts a charge, as a bow draws. The player holds right click for {@value #CHARGE_TICKS} ticks (3 s), slowed as with
 * a drawn bow, while purple sparkles rise around them, and only then does the scroll work and get used up. Releasing
 * early or switching items stops the charge, and so does entering combat ({@link InCombat}); the scroll is kept. A
 * scroll is refused in combat and when it would do nothing: it does not start, the arm does not swing and a chat line
 * says why, on the client too, which knows both from synced data. Each scroll says what it checks and does
 * ({@link Scroll}, {@link #add}): the Scroll of Oblivion ({@link StatPoints}) is the first; the Scroll of Respec
 * (RLM-226) and the Scroll of Rebirth (RLM-228) add theirs. A scroll is a plain item without a use duration, so the
 * charge is the item use NeoForge starts with the duration set in {@code LivingEntityUseItemEvent.Start}, the way
 * Sword Blocking Mechanics raises a sword.
 */
public final class Scrolls {
    /** How long a scroll charges: 3 s (Matěj, 2026-10-24). */
    public static final int CHARGE_TICKS = 60;
    /** How often sparkles rise around a charging player. */
    private static final int PARTICLE_INTERVAL = 2;
    /** A held right click asks again every 4 ticks; a refusal says why at most once a second. */
    private static final int MESSAGE_INTERVAL = 20;

    /**
     * What a scroll checks and does.
     *
     * @param refusal why the player cannot use it now, besides being in combat (nothing to undo); empty when they can.
     *     Asked on both sides, so it reads only data the client has too.
     * @param effect what it does once charged, on the server; the scroll is used up after it
     */
    public record Scroll(Function<Player, Optional<Component>> refusal, Consumer<ServerPlayer> effect) {
    }

    private static final Map<TagKey<Item>, Scroll> SCROLLS = new LinkedHashMap<>();
    /** The game time each player was last told why a scroll refused. */
    private static final Map<Player, Long> LAST_MESSAGE = new WeakHashMap<>();

    private Scrolls() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, Scrolls::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Start.class, Scrolls::onUseStart);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Tick.class, Scrolls::onUseTick);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Finish.class, Scrolls::onUseFinish);
    }

    /** Makes the items of {@code items} scrolls. Called at mod construction by the feature that owns the scroll. */
    static void add(TagKey<Item> items, Scroll scroll) {
        SCROLLS.put(items, scroll);
    }

    /** The scroll {@code stack} is, if it is one. */
    static Optional<Scroll> of(ItemStack stack) {
        return SCROLLS.entrySet().stream().filter(entry -> stack.is(entry.getKey())).map(Map.Entry::getValue)
                .findFirst();
    }

    /** Why {@code player} cannot use {@code scroll} now: combat first, then the scroll's own reason. */
    static Optional<Component> refusal(Player player, Scroll scroll) {
        if (InCombat.isInCombat(player)) {
            return Optional.of(Component.translatable(GeneratedTexts.MESSAGE_CANNOT_USE_IN_COMBAT));
        }
        return scroll.refusal().apply(player);
    }

    /** Right-clicking a scroll: on both sides, so the client starts the same charge or swings no arm. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Optional<Scroll> scroll = of(event.getItemStack());
        if (scroll.isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        event.setCanceled(true);
        Optional<Component> refusal = refusal(player, scroll.get());
        if (refusal.isPresent()) {
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player, refusal.get());
            return;
        }
        player.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** A scroll's charge lasts {@value #CHARGE_TICKS} ticks; the plain item has no use duration of its own. */
    private static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player && of(event.getItem()).isPresent()) {
            event.setDuration(CHARGE_TICKS);
        }
    }

    /**
     * Every tick of a charge, on the server: sparkles, and a stop the moment the player enters combat. Not by
     * cancelling the event, which would finish the charge at once, but by stopping the use.
     */
    private static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || of(event.getItem()).isEmpty()) {
            return;
        }
        if (InCombat.isInCombat(player)) {
            player.stopUsingItem();
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_CANNOT_USE_IN_COMBAT));
            return;
        }
        if (event.getDuration() % PARTICLE_INTERVAL == 0) {
            sparkles(level, player, 4);
        }
    }

    /** A full charge, on the server: the scroll works and is used up, unless it is refused now. */
    private static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Scroll> scroll = of(event.getItem());
        if (scroll.isEmpty()) {
            return;
        }
        Optional<Component> refusal = refusal(player, scroll.get());
        if (refusal.isPresent()) {
            tell(player, refusal.get());
            return;
        }
        scroll.get().effect().accept(player);
        event.getResultStack().consume(1, player);
        sparkles(level, player, 24);
    }

    private static void sparkles(ServerLevel level, Player player, int count) {
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1, player.getZ(), count, 0.5, 0.6,
                0.5, 0);
    }

    /** Says why a scroll refused, on the server, at most once every {@value #MESSAGE_INTERVAL} ticks. */
    private static void tell(Player player, Component message) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long now = serverPlayer.level().getGameTime();
        Long last = LAST_MESSAGE.get(serverPlayer);
        if (last == null || now - last >= MESSAGE_INTERVAL || now < last) {
            LAST_MESSAGE.put(serverPlayer, now);
            serverPlayer.sendSystemMessage(message);
        }
    }
}
