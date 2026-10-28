package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.spell.Proc;
import io.github.matejkadlec.realmforge.spell.Procs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * The QA mod's proc source (architecture 12): procs given to single entities, until items and talents (postponed,
 * RLM-28) give real ones. GameTests give and take them; weak keys, so a removed entity leaves nothing behind. Server
 * thread only.
 */
public final class QaProcs {
    private static final Map<LivingEntity, List<Proc>> PROCS = new WeakHashMap<>();

    private QaProcs() {
    }

    /** Adds this source to the proc engine. Called once from the QA mod's constructor. */
    public static void register() {
        Procs.addSource(holder -> PROCS.getOrDefault(holder, List.of()));
    }

    /** Gives {@code holder} the proc, after the ones it has. */
    public static void give(LivingEntity holder, Proc proc) {
        PROCS.computeIfAbsent(holder, key -> new ArrayList<>()).add(proc);
    }

    /** Takes every proc this source gave {@code holder}, and ends their internal cooldowns. */
    public static void clear(LivingEntity holder) {
        PROCS.remove(holder);
        Procs.resetCooldowns(holder);
    }
}
