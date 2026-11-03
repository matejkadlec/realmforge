package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.combat.CombatAttachments;
import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.combat.MagicShield;
import io.github.matejkadlec.realmforge.combat.MagicShields;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The QA training dummy (architecture 20): a mob of its own ({@link TrainingDummyMob}, RLM-528) that stands still,
 * never dies and carries a DPS meter. The player hitting it sees each hit and the session's DPS on the action bar;
 * every hit also goes to the combat log ({@link CombatLog}). A session ends after {@value #SESSION_GAP_TICKS} ticks
 * without a hit.
 *
 * <p>A fresh dummy is a clean target (Matěj, 2026-10-30): {@value #MAX_HEALTH} health at every level, no armor and no
 * toughness, no creature type, silent, named {@value #NAME}. Neat's card above it is its only display (RLM-339): its
 * level (RLM-338) and name, and Neat's armor. It takes damage and healing the way a player does: it is not undead, so
 * poison and harming hurt it and healing heals it. Apotheosis's world tier augments, which give every mob joining
 * near a player the armor of that player's tier, never reach it ({@link #APOTHEOSIS_AUGMENTS_APPLIED}).
 *
 * <p>{@code /qa dummy} sets its defenses and effects ({@code DummyCommands}): armor, toughness, magic shield, dodge,
 * resistances, status effects; vanilla's {@code /attribute} reaches every stat of ours on it. Its magic shield refills
 * {@value #SHIELD_REFILL_TICKS} ticks after its last damage, half a player's delay, so a test breaks it again sooner.
 *
 * <p>The dummy is a hostile mob, which gets a level and its scaling when it joins the world (W1, architecture 14.1).
 * A dummy joins at level 1, which adds nothing, so its health and armor stay what it was given; {@code /qa level}
 * still scales it like any hostile mob. A dummy from before RLM-528 is a pillager carrying the dummy's tag:
 * {@code /qa dummy remove} removes it with the others.
 */
public final class TrainingDummy {
    /** The entity tag that marks a training dummy. */
    public static final String TAG = "realmforge_qa.training_dummy";
    static final double MAX_HEALTH = 100;
    static final int SESSION_GAP_TICKS = 100;
    /** Ticks from the dummy's last damage to its magic shield's refill: 5 s, a player's 10 s halved. */
    public static final int SHIELD_REFILL_TICKS = 100;
    static final String NAME = "Training Dummy";
    /**
     * Apotheosis's mark of a mob its world tier augments reached ({@code Apoth.Attachments.TIER_AUGMENTS_APPLIED}): set
     * on a dummy before it joins, they skip it. Looked up by id, so the dummy works without Apotheosis.
     */
    static final Identifier APOTHEOSIS_AUGMENTS_APPLIED =
            Identifier.fromNamespaceAndPath("apotheosis", "tier_augments_applied");

    private static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(RealmforgeQa.MOD_ID);

    /** {@link TrainingDummyMob}: a pillager's size and tracking range. */
    public static final DeferredHolder<EntityType<?>, EntityType<TrainingDummyMob>> ENTITY_TYPE =
            ENTITY_TYPES.registerEntityType("training_dummy", TrainingDummyMob::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 1.95F).clientTrackingRange(8));
    /** The entity tag of the text display that labelled a dummy before RLM-339, removed from a dummy carrying one. */
    public static final String OLD_LABEL_TAG = "realmforge_qa.training_dummy_label";

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TrainingDummy() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class,
                event -> event.put(ENTITY_TYPE.get(), TrainingDummyMob.attributes().build()));
        modBus.addListener(EntityAttributeModificationEvent.class, TrainingDummy::addStats);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, TrainingDummy::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Dodge.class, TrainingDummy::onDodge);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, TrainingDummy::onDeath);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, TrainingDummy::onTick);
    }

    /** Spawns a dummy at {@code position}, facing {@code yaw}, with {@code armor} points of armor. */
    public static TrainingDummyMob spawn(ServerLevel level, Vec3 position, float yaw, double armor) {
        TrainingDummyMob dummy = ENTITY_TYPE.get().create(level, EntitySpawnReason.COMMAND);
        if (dummy == null) {
            throw new IllegalStateException("training dummies cannot be created");
        }
        dummy.snapTo(position.x, position.y, position.z, yaw, 0);
        dummy.setYHeadRot(yaw);
        dummy.addTag(TAG);
        dummy.getAttributes().getInstance(Attributes.ARMOR).setBaseValue(armor);
        dummy.setHealth((float) MAX_HEALTH);
        dummy.setData(StatsAttachments.LEVEL, EntityLevel.DEFAULT);
        dummy.setCustomName(Component.literal(NAME));
        skipTierAugments(dummy);
        level.addFreshEntity(dummy);
        return dummy;
    }

    /** Marks the dummy as one Apotheosis's world tier augments already reached, so they never add their armor. */
    @SuppressWarnings("unchecked")
    private static void skipTierAugments(LivingEntity dummy) {
        AttachmentType<?> applied = NeoForgeRegistries.ATTACHMENT_TYPES.getValue(APOTHEOSIS_AUGMENTS_APPLIED);
        if (applied != null) {
            dummy.setData((AttachmentType<Boolean>) applied, Boolean.TRUE);
        }
    }

    /** Removes the dummy, and a label it carries from before RLM-339. */
    public static void remove(LivingEntity dummy) {
        dummy.getPassengers().forEach(Entity::discard);
        dummy.discard();
    }

    public static boolean isDummy(Entity entity) {
        return entity.entityTags().contains(TAG);
    }

    /** The dummy's current session, if it was hit recently. */
    @Nullable
    public static Session session(Entity dummy) {
        return SESSIONS.get(dummy.getUUID());
    }

    /** Ends the dummy's session: its meter starts again at the next hit. */
    public static void reset(LivingEntity dummy) {
        SESSIONS.remove(dummy.getUUID());
    }

    /**
     * Damage dealt to one dummy since its session began.
     *
     * @param firstTick game time of the session's first hit
     * @param lastTick game time of its latest hit or dodge
     */
    public record Session(long firstTick, long lastTick, double total, int hits, int crits, int dodges) {
        /** Damage per second: the total over the time from the first to the last hit, plus one second. */
        public double dps() {
            return total / ((lastTick - firstTick) / 20.0 + 1);
        }
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity dummy) || !isDummy(dummy)
                || !(dummy.level() instanceof ServerLevel)) {
            return;
        }
        refillShield(dummy);
        if (dummy.tickCount % 20 == 0) {
            dropOldLabel(dummy);
        }
    }

    /**
     * Keeps the dummy's magic shield within its stat and refills it {@value #SHIELD_REFILL_TICKS} ticks after its last
     * damage, as {@code MagicShields} does a player's after its own delay; the pipeline's absorption sets the time of
     * a player's refill.
     */
    private static void refillShield(LivingEntity dummy) {
        float max = (float) DamagePipeline.stat(dummy, GeneratedStats.MAGIC_SHIELD);
        MagicShield shield = dummy.getExistingData(CombatAttachments.MAGIC_SHIELD).orElse(MagicShield.EMPTY);
        int refillAt = shield.refillAt() - (MagicShields.REFILL_DELAY_TICKS - SHIELD_REFILL_TICKS);
        float points = shield.points() > max || dummy.tickCount >= refillAt ? max : shield.points();
        if (points != shield.points()) {
            dummy.setData(CombatAttachments.MAGIC_SHIELD, new MagicShield(points, shield.refillAt()));
        }
    }

    /**
     * Leaves Neat's card as the dummy's only display: hides its name tag and removes the text display that labelled it
     * before RLM-339, both of which a dummy from an older world still has.
     */
    private static void dropOldLabel(LivingEntity dummy) {
        dummy.setCustomNameVisible(false);
        for (Entity passenger : dummy.getPassengers()) {
            if (passenger.entityTags().contains(OLD_LABEL_TAG)) {
                passenger.discard();
            }
        }
    }

    /**
     * Pillagers carry every stat of ours in the dev game, so GameTests can use them as targets with dodge, resistances
     * and the rest, and a dummy from before RLM-528 (a pillager) keeps working.
     */
    private static void addStats(EntityAttributeModificationEvent event) {
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            if (!event.has(EntityType.PILLAGER, stat)) {
                event.add(EntityType.PILLAGER, stat);
            }
        }
    }

    private static void onHit(CombatEvent.Hit event) {
        LivingEntity dummy = event.getEntity();
        if (!isDummy(dummy)) {
            return;
        }
        Session session = update(dummy, event.getInflictedDamage(), event.isCrit(), false);
        dummy.setHealth(dummy.getMaxHealth());
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String crit = event.isCrit() ? " crit" : "";
            player.sendOverlayMessage(Component.literal(String.format(Locale.ROOT, "Hit %s%s | %s DPS | %s in %d hits",
                    DamageNumbers.format(event.getInflictedDamage()), crit, DamageNumbers.format(session.dps()),
                    DamageNumbers.format(session.total()), session.hits())));
        }
    }

    private static void onDodge(CombatEvent.Dodge event) {
        LivingEntity dummy = event.getEntity();
        if (isDummy(dummy)) {
            Session session = update(dummy, 0, false, true);
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.literal("Dodged (" + session.dodges() + " dodges this session)")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** A dummy never dies: one hit bigger than its health refills it instead. */
    private static void onDeath(LivingDeathEvent event) {
        if (isDummy(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().setHealth(event.getEntity().getMaxHealth());
        }
    }

    private static Session update(LivingEntity dummy, double damage, boolean crit, boolean dodge) {
        long now = dummy.level().getGameTime();
        Session old = SESSIONS.get(dummy.getUUID());
        if (old == null || now - old.lastTick() > SESSION_GAP_TICKS) {
            old = new Session(now, now, 0, 0, 0, 0);
        }
        Session session = new Session(old.firstTick(), now, old.total() + damage, old.hits() + (dodge ? 0 : 1),
                old.crits() + (crit ? 1 : 0), old.dodges() + (dodge ? 1 : 0));
        SESSIONS.put(dummy.getUUID(), session);
        return session;
    }
}
