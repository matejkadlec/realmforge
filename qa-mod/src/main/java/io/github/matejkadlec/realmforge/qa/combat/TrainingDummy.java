package io.github.matejkadlec.realmforge.qa.combat;

import com.mojang.math.Transformation;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

/**
 * The QA training dummy (architecture 20): a husk that stands still, never dies and carries a DPS meter. Its name
 * shows the damage per second of the current session; the player hitting it sees each hit on the action bar; every
 * hit also goes to the combat log ({@link CombatLog}). A session ends after {@value #SESSION_GAP_TICKS} ticks without
 * a hit.
 *
 * <p>Husks carry our stats in the dev game ({@link #addStats}), so the dummy's dodge chance and resistances can be set
 * with vanilla's {@code /attribute} command. Its armor comes from {@code /qa dummy <armor>}. Its label, a text display
 * riding it, shows two lines (Matěj, 2026-10-13): the armor with the share of a hit it blocks against the nearest
 * player (that player's level and armor pierce), and below it the name with the DPS. Vanilla draws no name tag over an entity that carries a passenger, so the
 * label carries the name too; it changes with every hit and every second.
 *
 * <p>A husk is a hostile mob, which gets a level and its scaling when it joins the world (W1, architecture 14.1). A
 * dummy joins at level 1, which adds nothing, so its health and armor stay what it was given; {@code /qa level} still
 * scales it like any hostile mob.
 */
public final class TrainingDummy {
    /** The entity tag that marks a training dummy. */
    public static final String TAG = "realmforge_qa.training_dummy";
    static final double MAX_HEALTH = 1000;
    static final int SESSION_GAP_TICKS = 100;
    private static final String NAME = "Training Dummy";
    /** The entity tag of the text display that labels a dummy's armor. */
    static final String LABEL_TAG = "realmforge_qa.training_dummy_label";
    /** How far the label sits above the dummy's head, in blocks: where its name tag would be. */
    private static final float LABEL_LIFT = 0.35F;
    /** How far a player may stand from a dummy and still be the level its label shows. */
    private static final double LABEL_RANGE = 32;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TrainingDummy() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityAttributeModificationEvent.class, TrainingDummy::addStats);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, TrainingDummy::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Dodge.class, TrainingDummy::onDodge);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, TrainingDummy::onDeath);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, TrainingDummy::onTick);
    }

    /** Spawns a dummy at {@code position}, facing {@code yaw}, with {@code armor} points of armor. */
    public static Husk spawn(ServerLevel level, Vec3 position, float yaw, double armor) {
        Husk dummy = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
        if (dummy == null) {
            throw new IllegalStateException("husks cannot be created");
        }
        dummy.snapTo(position.x, position.y, position.z, yaw, 0);
        dummy.setYHeadRot(yaw);
        dummy.setNoAi(true);
        dummy.setPersistenceRequired();
        dummy.addTag(TAG);
        dummy.setCustomNameVisible(true);
        dummy.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(MAX_HEALTH);
        dummy.getAttributes().getInstance(Attributes.ARMOR).setBaseValue(armor);
        dummy.getAttributes().getInstance(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        dummy.setHealth((float) MAX_HEALTH);
        dummy.setData(StatsAttachments.LEVEL, EntityLevel.DEFAULT);
        level.addFreshEntity(dummy);
        showName(dummy, null);
        return dummy;
    }

    /** Removes the dummy and its label. */
    public static void remove(LivingEntity dummy) {
        dummy.getPassengers().forEach(Entity::discard);
        dummy.discard();
    }

    /**
     * The label's first line: the dummy's armor (and toughness, when it has any) and the share of a hit our armor
     * formula blocks against {@code attacker}, at its level and with its armor pierce (level 1 and none without an
     * attacker), such as "15 Armor (50% Damage Reduction)".
     */
    public static Component armorLine(LivingEntity dummy, @Nullable LivingEntity attacker) {
        double armor = dummy.getAttributeValue(Attributes.ARMOR);
        double toughness = dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        OptionalDouble share = attacker != null
                ? DamagePipeline.armorShare(dummy, DamagePipeline.level(attacker),
                        DamagePipeline.stat(attacker, GeneratedStats.ARMOR_PIERCE),
                        DamagePipeline.stat(attacker, GeneratedStats.ARMOR_PIERCE_PERCENT))
                : DamagePipeline.armorShare(dummy, EntityLevel.MIN);
        String text = CharacterSheetView.points(armor) + " Armor"
                + (toughness > 0 ? ", " + CharacterSheetView.points(toughness) + " Toughness" : "")
                + " (" + CharacterSheetView.share(share.orElse(0)) + " Damage Reduction)";
        return Component.literal(text);
    }

    /** The text display that labels the dummy, or none (a dummy from before the label). */
    @Nullable
    public static Display.TextDisplay labelEntity(LivingEntity dummy) {
        for (Entity passenger : dummy.getPassengers()) {
            if (passenger instanceof Display.TextDisplay text && passenger.entityTags().contains(LABEL_TAG)) {
                return text;
            }
        }
        return null;
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
        showName(dummy, null);
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
        if (event.getEntity() instanceof LivingEntity dummy && dummy.tickCount % 20 == 0 && isDummy(dummy)
                && dummy.level() instanceof ServerLevel) {
            updateLabel(dummy);
        }
    }

    /**
     * Shows the dummy's armor against the nearest player, at its level and with its armor pierce (level 1 and none
     * when nobody is near), adding the label if needed.
     */
    static void updateLabel(LivingEntity dummy) {
        if (!(dummy.level() instanceof ServerLevel level)) {
            return;
        }
        Display.TextDisplay label = labelEntity(dummy);
        if (label == null) {
            label = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            if (label == null) {
                return;
            }
            label.addTag(LABEL_TAG);
            label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            label.setLineWidth(400);
            label.setTransformation(new Transformation(new Vector3f(0, LABEL_LIFT, 0), null, null, null));
            label.snapTo(dummy.getX(), dummy.getY() + dummy.getBbHeight(), dummy.getZ());
            level.addFreshEntity(label);
            label.startRiding(dummy, true, false);
        }
        Player nearest = level.getNearestPlayer(dummy, LABEL_RANGE);
        Component name = dummy.getCustomName() != null ? dummy.getCustomName() : Component.literal(NAME);
        label.setText(Component.empty()
                .append(armorLine(dummy, nearest))
                .append("\n").append(name));
    }

    private static void addStats(EntityAttributeModificationEvent event) {
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            if (!event.has(EntityType.HUSK, stat)) {
                event.add(EntityType.HUSK, stat);
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
        showName(dummy, session);
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
            showName(dummy, session);
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

    private static void showName(LivingEntity dummy, @Nullable Session session) {
        dummy.setCustomName(session == null
                ? Component.literal(NAME)
                : Component.literal(NAME + " | " + DamageNumbers.format(session.dps()) + " DPS"));
        updateLabel(dummy);
    }
}
