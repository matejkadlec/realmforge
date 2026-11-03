package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.CombatAttachments;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.MagicShield;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa dummy}: the training dummy ({@link TrainingDummy}).
 * <ul>
 *   <li>{@code /qa dummy [<armor_points>]} spawns one three blocks in front of you, facing you, with that much armor (0 by
 *   default); {@code /qa dummy reset} restarts the DPS meter of every dummy, {@code /qa dummy remove} removes them
 *   all.</li>
 *   <li>Its defenses and effects (RLM-528) act on the dummy you look at within {@value #REACH} blocks, or else the
 *   nearest one within them: {@code armor <points>}, {@code toughness <points>}, {@code shield <points>} (its magic
 *   shield, full at once, refilling {@value TrainingDummy#SHIELD_REFILL_TICKS} ticks after its last damage),
 *   {@code dodge <percent>}, {@code resistance <percent>} (Spell Resistance, which every magic school's hit meets),
 *   {@code resistance <school> <percent>} (the school's own resistance; the Physical school's is Physical
 *   Resistance), and {@code effect <effect> [<strength>] [<seconds>]} (strength 1 is level I, for ever without
 *   seconds; seconds need a strength before them). The stats stop at their caps (75 % for a resistance), and the
 *   reply says the value the dummy has.</li>
 * </ul>
 * Creature types come with RLM-353, and their {@code type} subcommand with them. Vanilla's {@code /attribute} reaches
 * every other stat of ours on a dummy, for example {@code /attribute @n[type=realmforge_qa:training_dummy]
 * realmforge:lifesteal base set 0.1}.
 */
public final class DummyCommands {
    /** How far in front of the player a dummy appears. */
    static final double DISTANCE = 3;
    /** How far the defense and effect subcommands look for a dummy. */
    static final double REACH = 32;
    /** The most strength {@code effect} gives: vanilla's {@code /effect} amplifiers, 0 to 255. */
    static final int MAX_STRENGTH = MobEffectInstance.MAX_AMPLIFIER + 1;
    /** Resistance blocks 20 % per level, so level V already blocks every hit. */
    static final int MAX_RESISTANCE_STRENGTH = 5;

    private static final SimpleCommandExceptionType NO_DUMMY = new SimpleCommandExceptionType(
            Component.literal("No training dummy within " + (int) REACH + " blocks: /qa dummy spawns one"));
    private static final DynamicCommandExceptionType UNKNOWN_SCHOOL = new DynamicCommandExceptionType(
            school -> Component.literal("No spell school " + school));
    private static final DynamicCommandExceptionType NO_RESISTANCE = new DynamicCommandExceptionType(
            school -> Component.literal("The " + school + " school has no resistance stat"));
    private static final DynamicCommandExceptionType UNKNOWN_EFFECT = new DynamicCommandExceptionType(
            effect -> Component.literal("No status effect " + effect));

    private static final SuggestionProvider<CommandSourceStack> SCHOOLS = (context, builder) ->
            SharedSuggestionProvider.suggest(schools(context.getSource()).keySet().stream()
                    .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID)).map(Identifier::getPath), builder);
    private static final SuggestionProvider<CommandSourceStack> EFFECTS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.MOB_EFFECT.keySet(), builder);

    private DummyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dummy")
                .executes(context -> spawn(context, 0))
                // Not "armor": brigadier would merge it with the armor literal below, a child of the same name.
                .then(Commands.argument("armor_points", DoubleArgumentType.doubleArg(0, 1024))
                        .executes(context -> spawn(context, DoubleArgumentType.getDouble(context, "armor_points"))))
                .then(Commands.literal("reset").executes(DummyCommands::reset))
                .then(Commands.literal("remove").executes(DummyCommands::remove))
                .then(Commands.literal("armor").then(Commands.argument("points", DoubleArgumentType.doubleArg(0, 1024))
                        .executes(context -> setStat(context, Attributes.ARMOR, points(context), "armor"))))
                .then(Commands.literal("toughness").then(Commands.argument("points",
                                DoubleArgumentType.doubleArg(0, 1024))
                        .executes(context -> setStat(context, Attributes.ARMOR_TOUGHNESS, points(context),
                                "armor toughness"))))
                .then(Commands.literal("shield").then(Commands.argument("points", DoubleArgumentType.doubleArg(0, 1024))
                        .executes(DummyCommands::shield)))
                .then(Commands.literal("dodge").then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                        .executes(context -> setStat(context, GeneratedStats.DODGE_CHANCE, share(context),
                                "dodge chance"))))
                .then(Commands.literal("resistance")
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(context -> setStat(context, GeneratedStats.SPELL_RESISTANCE, share(context),
                                        "spell resistance (every magic school)")))
                        .then(Commands.argument("school", StringArgumentType.word()).suggests(SCHOOLS)
                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                        .executes(DummyCommands::schoolResistance))))
                .then(Commands.literal("effect").then(Commands.argument("effect", IdentifierArgument.id())
                        .suggests(EFFECTS)
                        .executes(context -> effect(context, 1, MobEffectInstance.INFINITE_DURATION))
                        .then(Commands.argument("strength", IntegerArgumentType.integer(1))
                                .executes(context -> effect(context, IntegerArgumentType.getInteger(context,
                                        "strength"), MobEffectInstance.INFINITE_DURATION))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> effect(context,
                                                IntegerArgumentType.getInteger(context, "strength"),
                                                IntegerArgumentType.getInteger(context, "seconds") * 20))))));
    }

    /**
     * The most strength {@code /qa dummy effect} gives {@code effect}: {@value #MAX_STRENGTH}, vanilla's
     * {@code /effect} range, and {@value #MAX_RESISTANCE_STRENGTH} for Resistance.
     */
    static int maxStrength(Holder<MobEffect> effect) {
        return effect.is(MobEffects.RESISTANCE) ? MAX_RESISTANCE_STRENGTH : MAX_STRENGTH;
    }

    /**
     * The dummy a subcommand acts on: the one the source looks at within {@value #REACH} blocks (its hitbox under the
     * crosshair), or else the one nearest to the source within them.
     */
    static LivingEntity target(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 from = source.getPosition();
        List<LivingEntity> near = dummies(source.getLevel()).stream()
                .filter(dummy -> dummy.distanceToSqr(from) <= REACH * REACH).toList();
        Entity looking = source.getEntity();
        if (looking != null) {
            Vec3 eyes = looking.getEyePosition();
            Vec3 end = eyes.add(looking.getLookAngle().scale(REACH));
            Optional<LivingEntity> sighted = near.stream()
                    .filter(dummy -> dummy.getBoundingBox().inflate(0.3).clip(eyes, end).isPresent())
                    .min(Comparator.comparingDouble(dummy -> dummy.distanceToSqr(eyes)));
            if (sighted.isPresent()) {
                return sighted.get();
            }
        }
        return near.stream().min(Comparator.comparingDouble(dummy -> dummy.distanceToSqr(from)))
                .orElseThrow(NO_DUMMY::create);
    }

    private static int spawn(CommandContext<CommandSourceStack> context, double armor)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 position = player.position().add(flat.scale(DISTANCE));
        // Facing the player: the opposite of the player's yaw.
        TrainingDummy.spawn(player.level(), position, player.getYRot() + 180, armor);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Training dummy with %s armor: hit it, your DPS shows above the hotbar", number(armor))), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        List<LivingEntity> dummies = dummies(context.getSource().getLevel());
        dummies.forEach(TrainingDummy::reset);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + dummies.size() + " DPS meters"), false);
        return dummies.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        List<LivingEntity> dummies = dummies(context.getSource().getLevel());
        dummies.forEach(TrainingDummy::remove);
        context.getSource().sendSuccess(() -> Component.literal("Removed " + dummies.size() + " training dummies"),
                true);
        return dummies.size();
    }

    /** Sets the targeted dummy's base {@code stat} to {@code value} and says what it has now. */
    private static int setStat(CommandContext<CommandSourceStack> context, Holder<Attribute> stat, double value,
            String name) throws CommandSyntaxException {
        LivingEntity dummy = target(context.getSource());
        setBase(dummy, stat, value);
        reply(context, dummy, name, stat);
        return 1;
    }

    /** Sets the targeted dummy's magic shield stat and fills its pool at once. */
    private static int shield(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LivingEntity dummy = target(context.getSource());
        setBase(dummy, GeneratedStats.MAGIC_SHIELD, points(context));
        float max = (float) dummy.getAttributeValue(GeneratedStats.MAGIC_SHIELD);
        dummy.setData(CombatAttachments.MAGIC_SHIELD, new MagicShield(max, dummy.tickCount));
        reply(context, dummy, "magic shield", GeneratedStats.MAGIC_SHIELD);
        return 1;
    }

    /** Sets the resistance of one school: its own resistance stat, or Physical Resistance for a physical school. */
    private static int schoolResistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "school");
        SpellSchool school = schools(context.getSource()).getOptional(Realmforge.id(name))
                .orElseThrow(() -> UNKNOWN_SCHOOL.create(name));
        Holder<Attribute> stat = school.resistance()
                .or(() -> school.magic() ? Optional.empty() : Optional.of(GeneratedStats.PHYSICAL_RESISTANCE))
                .orElseThrow(() -> NO_RESISTANCE.create(name));
        return setStat(context, stat, share(context), name + " resistance");
    }

    /**
     * Gives the targeted dummy a status effect: {@code strength} 1 is level I, and {@code duration} is in ticks or
     * {@link MobEffectInstance#INFINITE_DURATION}. A strength above the effect's maximum fails with the allowed range.
     */
    private static int effect(CommandContext<CommandSourceStack> context, int strength, int duration)
            throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "effect");
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).<Holder<MobEffect>>map(holder -> holder)
                .orElseThrow(() -> UNKNOWN_EFFECT.create(id));
        int max = maxStrength(effect);
        String effectName = effect.value().getDisplayName().getString();
        if (strength > max) {
            throw new SimpleCommandExceptionType(Component.literal(String.format(Locale.ROOT,
                    "Strength %d is out of range for %s: 1 to %d (to give seconds, put a strength before them)",
                    strength, effectName, max))).create();
        }
        LivingEntity dummy = target(context.getSource());
        dummy.addEffect(new MobEffectInstance(effect, duration, strength - 1));
        String time = duration == MobEffectInstance.INFINITE_DURATION ? "for ever" : "for " + duration / 20 + " s";
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Training dummy: %s, strength %d, %s", effectName, strength, time)), true);
        return 1;
    }

    /** Sets the stat's base value on the dummy, within the stat's range. */
    static void setBase(LivingEntity dummy, Holder<Attribute> stat, double value) {
        AttributeInstance instance = dummy.getAttribute(stat);
        if (instance == null) {
            throw new IllegalStateException(dummy.getType() + " lacks " + stat.getRegisteredName());
        }
        instance.setBaseValue(stat.value().sanitizeValue(value));
    }

    private static void reply(CommandContext<CommandSourceStack> context, LivingEntity dummy, String name,
            Holder<Attribute> stat) {
        double value = dummy.getAttributeValue(stat);
        boolean share = stat != Attributes.ARMOR && stat != Attributes.ARMOR_TOUGHNESS
                && stat != GeneratedStats.MAGIC_SHIELD;
        String shown = share ? number(value * 100) + " %" : number(value);
        context.getSource().sendSuccess(() -> Component.literal("Training dummy: " + name + " " + shown), true);
    }

    private static double points(CommandContext<CommandSourceStack> context) {
        return DoubleArgumentType.getDouble(context, "points");
    }

    /** The {@code percent} argument as a share: 50 is 0.5. */
    private static double share(CommandContext<CommandSourceStack> context) {
        return DoubleArgumentType.getDouble(context, "percent") / 100;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static Registry<SpellSchool> schools(CommandSourceStack source) {
        return source.registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL);
    }

    private static List<LivingEntity> dummies(ServerLevel level) {
        return List.copyOf(level.getEntities(EntityTypeTest.forClass(LivingEntity.class), TrainingDummy::isDummy));
    }
}
