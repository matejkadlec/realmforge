package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

/**
 * What a player's cast needs in hand (RLM-499; C4), a spell's {@code requires}: a melee spell strikes with a melee
 * weapon, Shield Bash with a shield. Players only: a mob or boss casts without it (a stone golem has no weapon), and the
 * pipeline refuses a requirement on an {@code npc_only} spell.
 */
public enum SpellRequirement implements StringRepresentable {
    /** An item of {@link #MELEE_WEAPONS} in the main hand. */
    MELEE_WEAPON("melee_weapon", CastFailure.NO_MELEE_WEAPON),
    /** A shield ({@code c:tools/shield}) in either hand; the other may hold a one-handed weapon or nothing. */
    SHIELD("shield", CastFailure.NO_SHIELD);

    /**
     * The item tag of melee weapons, which the pipeline writes (vanilla's swords, axes, spears, the trident and the
     * mace; our own melee weapon types once they exist). Bows, crossbows, wands and tools are none.
     */
    public static final TagKey<Item> MELEE_WEAPONS = TagKey.create(Registries.ITEM, Realmforge.id("melee_weapons"));
    public static final Codec<SpellRequirement> CODEC = StringRepresentable.fromEnum(SpellRequirement::values);

    private final String serializedName;
    private final CastFailure failure;

    SpellRequirement(String serializedName, CastFailure failure) {
        this.serializedName = serializedName;
        this.failure = failure;
    }

    /** Whether {@code caster} holds what the requirement asks for. */
    public boolean metBy(LivingEntity caster) {
        return switch (this) {
            case MELEE_WEAPON -> caster.getMainHandItem().is(MELEE_WEAPONS);
            case SHIELD -> caster.getMainHandItem().is(Tags.Items.TOOLS_SHIELD)
                    || caster.getOffhandItem().is(Tags.Items.TOOLS_SHIELD);
        };
    }

    /** Why a cast without it is refused. */
    public CastFailure failure() {
        return failure;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
