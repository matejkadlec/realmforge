package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Attachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The attachments of the {@code combat} feature (architecture 7). */
public final class CombatAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:magic_shield}: the points left in a player's magic shield and when it refills (architecture
     * 9.1). Transient: neither saved nor copied, so every new player entity (a login, a respawn) starts with a full
     * shield at its first tick. Synced to the owner, points only, for the HUD bar.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MagicShield>> MAGIC_SHIELD =
            ATTACHMENTS.register("magic_shield", () -> AttachmentType.builder(() -> MagicShield.EMPTY)
                    .sync(Attachments::toOwner, MagicShield.STREAM_CODEC).build());

    /**
     * {@code realmforge:in_combat}: when a living entity last dealt or took a hit, and when that leaves it out of
     * combat ({@link InCombat}, architecture 9.4). Transient: neither saved nor copied, so every new entity (a login,
     * a respawn) starts out of combat. Synced to the owner, so a client knows its own player's state.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatTime>> IN_COMBAT =
            ATTACHMENTS.register("in_combat", () -> AttachmentType.builder(() -> CombatTime.NONE)
                    .sync(Attachments::toOwner, CombatTime.STREAM_CODEC).build());

    /**
     * {@code realmforge:enchantment_protection}: what a player's protection enchantments block of a hit of each
     * {@link DamageKind}, for the character sheet's Resistances column ({@link EnchantmentProtection}, RLM-235). The
     * server works it out, as enchantment effects need a server level. Transient: neither saved nor copied, as the
     * server works it out again within half a second. Synced to the owner.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EnchantmentProtection>>
            ENCHANTMENT_PROTECTION = ATTACHMENTS.register("enchantment_protection",
                    () -> AttachmentType.builder(() -> EnchantmentProtection.NONE)
                            .sync(Attachments::toOwner, EnchantmentProtection.STREAM_CODEC).build());

    /**
     * {@code realmforge:crit_counters}: a player's crit rolls without a crit since the last one, for weapon attacks
     * and for spells ({@link CritCounters}, RLM-526). Transient: neither saved nor copied, so a login or a respawn
     * starts both counts again. Not synced: only the server rolls.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CritCounters>> CRIT_COUNTERS =
            ATTACHMENTS.register("crit_counters", () -> AttachmentType.builder(() -> CritCounters.NONE).build());

    private CombatAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
