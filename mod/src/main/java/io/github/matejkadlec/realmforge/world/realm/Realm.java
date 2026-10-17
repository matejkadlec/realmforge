package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A realm, an entry of the datapack registry {@code realmforge:realm} from {@code content/realms/} (architecture 5.2,
 * 15.1; the realm portal spec): a dimension of its own that a player enters only with its unlock, the advancement
 * {@code <namespace>:realm/<path>} ({@link Realms#unlockAdvancement}), which its dimension rules require. Synced to
 * clients, which W2b's portal colours and the arrival title need. The texts are translation keys.
 *
 * @param dimension the realm's dimension
 * @param name the realm's name, without an article ("Undervault")
 * @param subtitle its element ("Stone Realm")
 * @param lockedHint how to unlock it, the subtitle of a refusal
 * @param flavour a flavour line
 * @param icon the item its unlock advancement shows
 */
public record Realm(ResourceKey<Level> dimension, String name, String subtitle, String lockedHint, String flavour,
        Identifier icon) {
    public static final Codec<Realm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Realm::dimension),
                    Codec.STRING.fieldOf("name").forGetter(Realm::name),
                    Codec.STRING.fieldOf("subtitle").forGetter(Realm::subtitle),
                    Codec.STRING.fieldOf("locked_hint").forGetter(Realm::lockedHint),
                    Codec.STRING.fieldOf("flavour").forGetter(Realm::flavour),
                    Identifier.CODEC.fieldOf("icon").forGetter(Realm::icon))
            .apply(instance, Realm::new));
}
