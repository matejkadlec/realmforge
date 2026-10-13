package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The QA training dummy (architecture 20): a husk that stands still, never dies and carries a DPS meter. Its name
 * shows the damage per second of the current session; the player hitting it sees each hit on the action bar; every
 * hit also goes to the combat log ({@link CombatLog}). A session ends after {@value #SESSION_GAP_TICKS} ticks without
 * a hit.
 *
 * <p>Husks carry our stats in the dev game ({@link #addStats}), so the dummy's dodge chance and resistances can be set
 * with vanilla's {@code /attribute} command. Its armor comes from {@code /qa dummy <armor>}.
 */
public final class TrainingDummy {
    /** The entity tag that marks a training dummy. */
    public static final String TAG = "realmforge_qa.training_dummy";
    static final double MAX_HEALTH = 1000;
    static final int SESSION_GAP_TICKS = 100;
    private static final String NAME = "Training Dummy";

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TrainingDummy() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityAttributeModificationEvent.class, TrainingDummy::addStats);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, TrainingDummy::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Dodge.class, TrainingDummy::onDodge);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, TrainingDummy::onDeath);
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
        level.addFreshEntity(dummy);
        showName(dummy, null);
        return dummy;
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
    }
}
