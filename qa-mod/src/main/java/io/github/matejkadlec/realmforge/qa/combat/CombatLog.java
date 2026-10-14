package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The combat log file that agents and playtesters read (architecture 20): one line per hit or dodge that involves a
 * player or a training dummy, written to {@code logs/realmforge-combat.log} in the game directory
 * ({@code run/client/}, {@code run/server/}, {@code run/gametest/}). A new server starts a new file.
 *
 * <p>A line: {@code <time> tick=<game time> hit|dodge target=<name> attacker=<name> type=<damage type>
 * school=<school or -> crit=<true|false> level=<attacker level> original=<damage> armor=<armor blocked>
 * dealt=<damage dealt> health=<health lost>}.
 */
public final class CombatLog {
    /** The file, relative to the game directory. */
    public static final String FILE = "logs/realmforge-combat.log";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Nullable
    private static BufferedWriter writer;

    private CombatLog() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> open(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> close());
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, CombatLog::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Dodge.class, CombatLog::onDodge);
    }

    /** The log file of a server. */
    public static Path file(MinecraftServer server) {
        return server.getServerDirectory().resolve(FILE);
    }

    private static void onHit(CombatEvent.Hit event) {
        LivingEntity target = event.getEntity();
        if (logged(target, event.getSource())) {
            String school = event.getSchool().map(holder -> holder.getRegisteredName()).orElse("-");
            write(target, String.format(Locale.ROOT, "hit %s school=%s crit=%s original=%.3f armor=%.3f dealt=%.3f"
                    + " health=%.3f", who(target, event.getSource()), school, event.isCrit(), event.getOriginalDamage(),
                    event.getArmorReduction(), event.getInflictedDamage(), event.getHealthDamage()));
        }
    }

    private static void onDodge(CombatEvent.Dodge event) {
        if (logged(event.getEntity(), event.getSource())) {
            write(event.getEntity(), "dodge " + who(event.getEntity(), event.getSource()));
        }
    }

    private static boolean logged(LivingEntity target, DamageSource source) {
        return target.level() instanceof ServerLevel
                && (target instanceof Player || TrainingDummy.isDummy(target) || source.getEntity() instanceof Player);
    }

    private static String who(LivingEntity target, DamageSource source) {
        Entity attacker = source.getEntity();
        String level = attacker instanceof LivingEntity living ? Integer.toString(DamagePipeline.level(living)) : "-";
        return "target=" + name(target) + " attacker=" + (attacker == null ? "-" : name(attacker)) + " type="
                + source.typeHolder().getRegisteredName() + " level=" + level;
    }

    /** A name without spaces; a dummy's own name changes with its DPS, so it logs as {@code training_dummy}. */
    private static String name(Entity entity) {
        return TrainingDummy.isDummy(entity) ? "training_dummy" : entity.getPlainTextName().replace(' ', '_');
    }

    private static synchronized void write(LivingEntity target, String line) {
        if (writer == null && target.level().getServer() != null) {
            open(target.level().getServer());
        }
        if (writer == null) {
            return;
        }
        try {
            writer.write(LocalDateTime.now().format(TIME) + " tick=" + target.level().getGameTime() + " " + line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            RealmforgeQa.LOGGER.warn("Could not write the combat log; stopping it", e);
            close();
        }
    }

    private static synchronized void open(MinecraftServer server) {
        close();
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            RealmforgeQa.LOGGER.warn("Could not open the combat log {}", file, e);
        }
    }

    private static synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                RealmforgeQa.LOGGER.warn("Could not close the combat log", e);
            }
            writer = null;
        }
    }
}
