package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import java.util.Comparator;
import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa mob [<entity>]} shows a mob's level and what the level brings (W1, architecture 14.1): its health over its
 * maximum and the multiple of the maximum the level added, its armor and the points the level added, and the multiple
 * of the damage its hits deal. Without an argument it shows the nearest hostile mob within {@value #RANGE} blocks.
 * {@code /qa level <level> <entity>} changes a mob's level.
 */
public final class MobCommands {
    static final double RANGE = 32;

    private static final SimpleCommandExceptionType NO_MOB =
            new SimpleCommandExceptionType(Component.literal("No hostile mob within " + (int) RANGE + " blocks"));
    private static final SimpleCommandExceptionType NOT_A_MOB =
            new SimpleCommandExceptionType(Component.literal("Only mobs have a mob level"));

    private MobCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mob")
                .executes(context -> show(context, nearest(context.getSource())))
                .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(context -> show(context, EntityArgument.getEntity(context, "entity"))));
    }

    /**
     * One line about the mob, such as "Zombie: Level 4, Health 31.2 / 31.2 (x1.30), Armor 3.5 (+1.5), Damage x1.15",
     * or "Cow: No Level".
     */
    public static String describe(Mob mob) {
        OptionalInt level = MobLevels.level(mob);
        if (level.isEmpty()) {
            return mob.getPlainTextName() + ": No Level";
        }
        double healthAdded = amount(mob.getAttribute(Attributes.MAX_HEALTH), MobScaling.HEALTH_MODIFIER);
        double armorAdded = amount(mob.getAttribute(Attributes.ARMOR), MobScaling.ARMOR_MODIFIER);
        double damage = MobLevels.getsLevel(mob)
                ? MobScaling.at(mob.registryAccess(), level.getAsInt()).damage()
                : MobScaling.NONE.damage();
        return String.format(Locale.ROOT, "%s: Level %d, Health %.1f / %.1f (x%.2f), Armor %.1f (+%.1f), Damage x%.2f",
                mob.getPlainTextName(), level.getAsInt(), mob.getHealth(), mob.getMaxHealth(), 1 + healthAdded,
                mob.getAttributeValue(Attributes.ARMOR), armorAdded, damage);
    }

    private static int show(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        if (!(target instanceof Mob mob)) {
            throw NOT_A_MOB.create();
        }
        String line = describe(mob);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return MobLevels.level(mob).orElse(0);
    }

    private static Mob nearest(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 position = source.getPosition();
        return source.getLevel().getEntitiesOfClass(Mob.class, new AABB(position, position).inflate(RANGE),
                        mob -> MobLevels.getsLevel(mob) && mob.isAlive())
                .stream()
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(position)))
                .orElseThrow(NO_MOB::create);
    }

    /** The amount of the modifier with {@code id}, or 0 when the attribute or the modifier is missing. */
    private static double amount(AttributeInstance instance, Identifier id) {
        AttributeModifier modifier = instance != null ? instance.getModifier(id) : null;
        return modifier != null ? modifier.amount() : 0;
    }
}
