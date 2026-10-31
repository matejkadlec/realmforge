package io.github.matejkadlec.realmforge.combat;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What a player's protection enchantments block of a hit of each {@link DamageKind}, for the character sheet's
 * Resistances column (RLM-235; Matěj, 2026-10-31: "the more the sheet shows the player, the better"). Taken from the
 * game itself, as {@code LivingEntity#getDamageAfterMagicAbsorb} does for a real hit: the protection the equipped
 * enchantments give a hit of the kind's damage type ({@code EnchantmentHelper#getDamageProtection}, so Apothic
 * Enchanting's levels and RLM-537's Magic Protection count without code of ours), turned into a share by
 * {@code CombatRules#getDamageAfterMagicAbsorb}, whose formula Apothic Attributes replaces with its own; a hit that
 * bypasses effects or enchantments gets nothing. Armor is not in it.
 *
 * <p>Enchantment effects need a server level, so the server works it out (every half second for each player, kept when
 * it changes) and syncs it to the player in {@code realmforge:enchantment_protection}; the client's sheet reads that.
 *
 * @param shares the share blocked, from 0 to 1, for each kind in {@link DamageKind} order
 */
public record EnchantmentProtection(List<Double> shares) {
    /** Nothing blocked: a new player before the server's first update. */
    public static final EnchantmentProtection NONE = new EnchantmentProtection(List.of());

    public static final StreamCodec<ByteBuf, EnchantmentProtection> STREAM_CODEC = ByteBufCodecs.DOUBLE
            .apply(ByteBufCodecs.list()).map(EnchantmentProtection::new, EnchantmentProtection::shares);

    /** How often the server works the shares out again, in ticks. */
    static final int INTERVAL_TICKS = 10;

    public EnchantmentProtection {
        shares = List.copyOf(shares);
    }

    /** The share blocked of a hit of {@code kind}. */
    public double share(DamageKind kind) {
        return kind.ordinal() < shares.size() ? shares.get(kind.ordinal()) : 0;
    }

    /** The player's shares: worked out now on the server, the synced ones on the client. */
    public static EnchantmentProtection of(Player player) {
        return player.level() instanceof ServerLevel level ? compute(level, player)
                : player.getData(CombatAttachments.ENCHANTMENT_PROTECTION);
    }

    /** What the entity's equipped enchantments block of a hit of each kind. */
    public static EnchantmentProtection compute(ServerLevel level, LivingEntity entity) {
        HolderLookup.RegistryLookup<DamageType> types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        List<Double> shares = new ArrayList<>();
        for (DamageKind kind : DamageKind.values()) {
            shares.add(types.get(kind.example()).map(type -> share(level, entity, new DamageSource(type)))
                    .orElse(0.0));
        }
        return new EnchantmentProtection(shares);
    }

    /** The enchantment step of {@code LivingEntity#getDamageAfterMagicAbsorb} for a hit of 1. */
    static double share(ServerLevel level, LivingEntity entity, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return 0;
        }
        float protection = EnchantmentHelper.getDamageProtection(level, entity, source);
        return protection > 0 ? Mth.clamp(1 - CombatRules.getDamageAfterMagicAbsorb(1.0F, protection), 0, 1) : 0;
    }

    /** Keeps each player's synced shares current. */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel level && player.tickCount % INTERVAL_TICKS == 0) {
            EnchantmentProtection now = compute(level, player);
            if (!now.equals(player.getData(CombatAttachments.ENCHANTMENT_PROTECTION))) {
                player.setData(CombatAttachments.ENCHANTMENT_PROTECTION, now);
            }
        }
    }
}
