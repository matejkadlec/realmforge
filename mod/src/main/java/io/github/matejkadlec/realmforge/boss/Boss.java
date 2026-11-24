package io.github.matejkadlec.realmforge.boss;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;

/**
 * A boss's fight, an entry of the server-only datapack registry {@code realmforge:boss} from {@code content/bosses/}
 * (architecture 5.2, 14.3). Its body is a custom mob of archetype {@code boss} ({@link BossMob}), whose name, size,
 * attributes at level 1, sounds, loot and model come from {@code content/mobs/}; the entry names that entity type and
 * gives everything about the fight.
 *
 * @param entity the entity type of its body
 * @param level its fixed level outside a dungeon run, which scales it like every mob (architecture 14.1)
 * @param arena where it fights
 * @param barColor its boss bar's colour
 * @param phaseChangeTicks how long it stands, unhurt and casting nothing, when it enters a later phase
 * @param immuneTo the status effects it never takes (RLM-539: Frozen, Stunned), per boss: an effect, or every effect
 *     of a tag ({@code #realmforge:bleeds}, RLM-601)
 * @param phases its phases, in the order the fight goes through; never empty
 */
public record Boss(EntityType<?> entity, int level, Arena arena, BossEvent.BossBarColor barColor, int phaseChangeTicks,
        List<Either<TagKey<MobEffect>, Holder<MobEffect>>> immuneTo, List<BossPhase> phases) {
    /** An entry of {@code immune_to}: a status effect tag after {@code #}, or a status effect. */
    private static final Codec<Either<TagKey<MobEffect>, Holder<MobEffect>>> IMMUNITY_CODEC =
            Codec.either(TagKey.hashedCodec(Registries.MOB_EFFECT), MobEffect.CODEC);

    public static final Codec<Boss> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Boss::entity),
                    ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(Boss::level),
                    Arena.CODEC.fieldOf("arena").forGetter(Boss::arena),
                    BossEvent.BossBarColor.CODEC.optionalFieldOf("bar_color", BossEvent.BossBarColor.RED)
                            .forGetter(Boss::barColor),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("phase_change_ticks", 0)
                            .forGetter(Boss::phaseChangeTicks),
                    IMMUNITY_CODEC.listOf().optionalFieldOf("immune_to", List.of()).forGetter(Boss::immuneTo),
                    ExtraCodecs.nonEmptyList(BossPhase.CODEC.listOf()).fieldOf("phases").forGetter(Boss::phases))
            .apply(instance, Boss::new));

    public Boss {
        immuneTo = List.copyOf(immuneTo);
        phases = List.copyOf(phases);
    }

    /**
     * Whether it never takes {@code effect}: one of its entries names it or a tag it is in. Holders compare by their
     * key (NeoForge's {@code equals}), as vanilla's {@code MobEffectInstance#is} does.
     */
    public boolean isImmuneTo(Holder<MobEffect> effect) {
        return immuneTo.stream().anyMatch(entry -> entry.map(effect::is, effect::equals));
    }

    /**
     * Where a boss fights: a sphere round its home, the spot where it first stood on the ground.
     *
     * @param radius the sphere's radius in blocks
     */
    public record Arena(double radius) {
        public static final Codec<Arena> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(1, 256).fieldOf("radius").forGetter(Arena::radius))
                .apply(instance, Arena::new));
    }
}
