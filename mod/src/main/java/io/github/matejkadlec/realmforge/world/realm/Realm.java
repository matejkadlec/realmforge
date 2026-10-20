package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;

/**
 * A realm, an entry of the datapack registry {@code realmforge:realm} from {@code content/realms/} (architecture 5.2,
 * 15.1; the realm portal spec): a dimension of its own that a player enters only with its unlock, the advancement
 * {@code <namespace>:realm/<path>} ({@link Realms#unlockAdvancement}), which its dimension rules require, and the realm
 * portal's look when it opens onto the realm. Synced to clients, which the portal's colour and particles need. The texts
 * are translation keys.
 *
 * <p>Overworld has an entry too, which is no realm ({@link Realms#of} never returns it): always open, without a
 * subtitle, description, hint, flavour line, icon, signet or particle. Its colour is the return portals', and its arrival is where a
 * way back from a realm builds a portal.
 *
 * @param dimension the realm's dimension
 * @param name the realm's name, without an article ("Undervault")
 * @param subtitle its element ("Stone Realm"); empty for Overworld
 * @param description the realm in running text, lowercase and without an article ("stone realm"); empty for Overworld
 * @param lockedHint how to unlock it, the subtitle of a refusal; empty for Overworld
 * @param levels the realm's level band, which the arrival title shows; empty for Overworld
 * @param flavour a flavour line; empty for Overworld
 * @param icon the item its unlock advancement shows
 * @param signet the item that opens the realm portal onto the realm, an id in the static item registry
 * @param colour the portal's colour while it opens onto the realm, RGB with an opaque alpha byte
 * @param particle the vanilla particle without options that the portal shows next to the dust in its colour
 * @param soundPitch the pitch of the vanilla portal sounds for this realm
 * @param arrival where a trip may build a portal in the dimension
 */
public record Realm(ResourceKey<Level> dimension, String name, String subtitle, String description, String lockedHint,
        Optional<Levels> levels, String flavour, Optional<Identifier> icon, Optional<Identifier> signet, int colour, Optional<Identifier> particle,
        float soundPitch, Arrival arrival) {
    public static final Codec<Realm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Realm::dimension),
                    Codec.STRING.fieldOf("name").forGetter(Realm::name),
                    Codec.STRING.optionalFieldOf("subtitle", "").forGetter(Realm::subtitle),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(Realm::description),
                    Codec.STRING.optionalFieldOf("locked_hint", "").forGetter(Realm::lockedHint),
                    Levels.CODEC.optionalFieldOf("levels").forGetter(Realm::levels),
                    Codec.STRING.optionalFieldOf("flavour", "").forGetter(Realm::flavour),
                    Identifier.CODEC.optionalFieldOf("icon").forGetter(Realm::icon),
                    Identifier.CODEC.optionalFieldOf("signet").forGetter(Realm::signet),
                    ExtraCodecs.STRING_RGB_COLOR.fieldOf("colour").forGetter(Realm::colour),
                    Identifier.CODEC.optionalFieldOf("particle").forGetter(Realm::particle),
                    Codec.floatRange(0.5F, 2.0F).optionalFieldOf("sound_pitch", 1.0F).forGetter(Realm::soundPitch),
                    Arrival.CODEC.fieldOf("arrival").forGetter(Realm::arrival))
            .apply(instance, Realm::new));

    /**
     * A realm's level band, from the lowest level to the highest (RLM-188: Undervault's is chapter 2's, 6 to 18).
     *
     * @param lowest its entry level
     * @param highest its last level
     */
    public record Levels(int lowest, int highest) {
        public static final Codec<Levels> CODEC = Codec.intRange(1, Integer.MAX_VALUE).listOf(2, 2).comapFlatMap(
                range -> range.get(0) <= range.get(1)
                        ? DataResult.success(new Levels(range.get(0), range.get(1)))
                        : DataResult.error(() -> "levels " + range + " are not [lowest, highest]"),
                levels -> List.of(levels.lowest(), levels.highest()));
    }

    /**
     * Where a trip may build a portal in the dimension when none is near (realm portal spec, "Building a portal").
     *
     * @param minY the lowest height of a built frame's bottom row
     * @param maxY the highest
     * @param floor the block under and in front of a frame carved out where no spot fits, an id in the block registry
     * @param pocketRadius blocks of safe space around a carved frame, free of fluids and falling blocks
     */
    public record Arrival(int minY, int maxY, Identifier floor, int pocketRadius) {
        public static final Codec<Arrival> CODEC = RecordCodecBuilder.<Arrival>create(instance -> instance.group(
                        Codec.INT.listOf(2, 2).fieldOf("y").forGetter(arrival -> List.of(arrival.minY(), arrival.maxY())),
                        Identifier.CODEC.fieldOf("floor").forGetter(Arrival::floor),
                        Codec.intRange(1, 8).optionalFieldOf("pocket_radius", 3).forGetter(Arrival::pocketRadius))
                .apply(instance, (y, floor, pocketRadius) -> new Arrival(y.get(0), y.get(1), floor, pocketRadius)));

        /** {@code y} clamped into the arrival's heights. */
        public int clampY(int y) {
            return Math.clamp(y, minY, maxY);
        }
    }
}
