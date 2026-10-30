package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What a player reads when a cast fails or ends early (research SP-3; C3b, RLM-25): one text of
 * {@code content/texts/cast.yaml} for each {@link CastFailure}, and "Interrupted" for a cast its caster's side ended.
 * The switches name every value, so a new reason does not compile without its text.
 */
public final class CastTexts {
    private CastTexts() {
    }

    /** Why a cast of {@code spell} was refused; {@code spell} names the resource a cast lacked, if it is known. */
    public static Component failure(CastFailure failure, Optional<Spell> spell) {
        String key = switch (failure) {
            case CANNOT_CAST -> GeneratedTexts.CAST_CANNOT_CAST;
            case UNKNOWN_SPELL -> GeneratedTexts.CAST_UNKNOWN_SPELL;
            case NPC_ONLY -> GeneratedTexts.CAST_NPC_ONLY;
            case NOT_KNOWN -> GeneratedTexts.CAST_NOT_KNOWN;
            case ALREADY_CASTING -> GeneratedTexts.CAST_ALREADY_CASTING;
            case ON_COOLDOWN -> GeneratedTexts.CAST_ON_COOLDOWN;
            case ON_GLOBAL_COOLDOWN -> GeneratedTexts.CAST_ON_GLOBAL_COOLDOWN;
            case NO_TARGET -> GeneratedTexts.CAST_NO_TARGET;
            case INVALID_TARGET -> GeneratedTexts.CAST_INVALID_TARGET;
            case OUT_OF_RANGE -> GeneratedTexts.CAST_OUT_OF_RANGE;
            case NO_LINE_OF_SIGHT -> GeneratedTexts.CAST_NO_LINE_OF_SIGHT;
            case NOT_ENOUGH_RESOURCE -> GeneratedTexts.CAST_NOT_ENOUGH_RESOURCE;
            case STUNNED -> GeneratedTexts.CAST_STUNNED;
            case NO_MELEE_WEAPON -> GeneratedTexts.CAST_NO_MELEE_WEAPON;
            case NO_SHIELD -> GeneratedTexts.CAST_NO_SHIELD;
            case PASSIVE -> GeneratedTexts.CAST_PASSIVE;
        };
        if (failure == CastFailure.NOT_ENOUGH_RESOURCE) {
            Component resource = spell.flatMap(Spell::cost).flatMap(cost -> cost.resource().unwrapKey())
                    .map(CastTexts::resourceName).orElse(Component.empty());
            return Component.translatable(key, resource);
        }
        return Component.translatable(key);
    }

    /** How a cast of {@code spell} ended, for the cast bar; empty when it completed. */
    public static Optional<Component> end(CastEnd end, Optional<Spell> spell) {
        return switch (end) {
            case COMPLETED -> Optional.empty();
            case KNOCKED_BACK, CANCELLED, DIED, CHANGED_DIMENSION, LOGGED_OUT, STUNNED ->
                    Optional.of(Component.translatable(GeneratedTexts.CAST_INTERRUPTED));
            case TARGET_LOST -> Optional.of(Component.translatable(GeneratedTexts.CAST_TARGET_LOST));
            case OUT_OF_RANGE -> Optional.of(failure(CastFailure.OUT_OF_RANGE, spell));
            case NO_LINE_OF_SIGHT -> Optional.of(failure(CastFailure.NO_LINE_OF_SIGHT, spell));
            case INVALID_TARGET -> Optional.of(failure(CastFailure.INVALID_TARGET, spell));
            case NOT_ENOUGH_RESOURCE -> Optional.of(failure(CastFailure.NOT_ENOUGH_RESOURCE, spell));
        };
    }

    /** A resource type's name, {@code resource.realmforge.mana} (content/texts/resource.yaml). */
    public static Component resourceName(ResourceKey<ResourceType> type) {
        Identifier id = type.identifier();
        return Component.translatable("resource." + id.getNamespace() + "." + id.getPath());
    }
}
