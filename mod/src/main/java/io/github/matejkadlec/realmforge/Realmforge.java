package io.github.matejkadlec.realmforge;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.boss.BossFeature;
import io.github.matejkadlec.realmforge.character.CharacterFeature;
import io.github.matejkadlec.realmforge.combat.CombatFeature;
import io.github.matejkadlec.realmforge.command.CommandFeature;
import io.github.matejkadlec.realmforge.compat.CompatFeature;
import io.github.matejkadlec.realmforge.core.CoreFeature;
import io.github.matejkadlec.realmforge.dungeon.DungeonFeature;
import io.github.matejkadlec.realmforge.effect.EffectFeature;
import io.github.matejkadlec.realmforge.item.ItemFeature;
import io.github.matejkadlec.realmforge.loot.LootFeature;
import io.github.matejkadlec.realmforge.mob.MobFeature;
import io.github.matejkadlec.realmforge.network.RealmforgeNetwork;
import io.github.matejkadlec.realmforge.progression.ProgressionFeature;
import io.github.matejkadlec.realmforge.spell.SpellFeature;
import io.github.matejkadlec.realmforge.stats.StatsFeature;
import io.github.matejkadlec.realmforge.talent.TalentFeature;
import io.github.matejkadlec.realmforge.world.WorldFeature;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Realmforge core mod: registers every feature in a fixed order (architecture 4.5). Client-only
 * setup starts in {@code client/RealmforgeClient}.
 */
@Mod(Realmforge.MOD_ID)
public final class Realmforge {
    public static final String MOD_ID = "realmforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Realmforge(IEventBus modBus, ModContainer container) {
        // Lower features first, in the order of the dependency table (architecture 4.4).
        CoreFeature.register(modBus);
        ProgressionFeature.register(modBus);
        StatsFeature.register(modBus);
        CombatFeature.register(modBus);
        EffectFeature.register(modBus);
        SpellFeature.register(modBus);
        TalentFeature.register(modBus);
        ItemFeature.register(modBus);
        LootFeature.register(modBus);
        CharacterFeature.register(modBus);
        WorldFeature.register(modBus);
        MobFeature.register(modBus);
        BossFeature.register(modBus);
        DungeonFeature.register(modBus);
        CommandFeature.register(modBus);
        CompatFeature.register(modBus);
        RealmforgeNetwork.register(modBus, container);
    }

    /** Returns {@code realmforge:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
