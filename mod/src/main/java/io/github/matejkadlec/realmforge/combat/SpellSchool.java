package io.github.matejkadlec.realmforge.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * A spell school, an entry of the datapack registry {@code realmforge:spell_school} from {@code content/spell_schools/}
 * (architecture 5.2, 9.1): the kind of damage a spell, an ability or a boss attack deals. A hit belongs to a school
 * when its damage type is the school's ({@link SpellSchools#of}); every school's damage type is in
 * {@code #minecraft:bypasses_cooldown}, so its hits are never swallowed by the hurt cooldown.
 *
 * @param name translation key of the school's name
 * @param kind physical (armor reduces it, it can be dodged, attack damage drives its spells) or magic (bypasses armor,
 *     Spell Power drives its spells)
 * @param color colour of the school's spells: spell bar frames, the cast bar, damage numbers in tooltips; opaque ARGB
 *              (written {@code "#RRGGBB"})
 * @param combatTextColor colour of the school's floating combat text, opaque ARGB
 * @param damageType the vanilla damage type of the school's hits
 * @param power the secondary stat that raises the school's damage dealt, if any
 * @param resistance the secondary stat that lowers the school's damage taken, if any
 */
public record SpellSchool(String name, Kind kind, int color, int combatTextColor,
        ResourceKey<DamageType> damageType, Optional<Holder<Attribute>> power, Optional<Holder<Attribute>> resistance) {
    public static final Codec<SpellSchool> CODEC = RecordCodecBuilder.<SpellSchool>create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(SpellSchool::name),
                    Kind.CODEC.fieldOf("kind").forGetter(SpellSchool::kind),
                    ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(SpellSchool::color),
                    ExtraCodecs.STRING_RGB_COLOR.fieldOf("combat_text_color").forGetter(SpellSchool::combatTextColor),
                    ResourceKey.codec(Registries.DAMAGE_TYPE).fieldOf("damage_type").forGetter(SpellSchool::damageType),
                    Attribute.CODEC.optionalFieldOf("power").forGetter(SpellSchool::power),
                    Attribute.CODEC.optionalFieldOf("resistance").forGetter(SpellSchool::resistance))
            .apply(instance, SpellSchool::new))
            .validate(SpellSchool::validate);

    /** Whether the school is magic: bypasses armor, cannot be dodged, its spells scale with Spell Power. */
    public boolean magic() {
        return kind == Kind.MAGIC;
    }

    /** The two kinds of damage (architecture 9.1). */
    public enum Kind implements StringRepresentable {
        /** Armor reduces it and it can be dodged, like a melee hit; its spells scale with attack damage. */
        PHYSICAL("physical"),
        /** Bypasses armor and cannot be dodged; its spells scale with Spell Power, its resistance lowers it. */
        MAGIC("magic");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    private static DataResult<SpellSchool> validate(SpellSchool school) {
        if (school.power().isPresent() && school.power().equals(school.resistance())) {
            return DataResult.error(() -> "A school's power and resistance must be different stats: "
                    + school.power().get().getRegisteredName());
        }
        return DataResult.success(school);
    }
}
