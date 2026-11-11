package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The animated texture of a realm portal's portal blocks (RLM-300; realm portal spec, "Look and sound"), chosen by
 * the realm the portal opens onto ({@link Realm.Portal#animation}) and set on the portal blocks as their
 * {@link #PROPERTY} when the frame lights ({@link RealmPortals#light}). The pipeline draws one texture per value
 * ({@code generate/portal_textures.py}); a new one is a value here and a name in its {@code PORTAL_ANIMATIONS}.
 */
public enum PortalAnimation implements StringRepresentable {
    /** The shared one: spirals turning round the block's corners, so a whole swirl forms where four blocks meet. */
    SWIRL("swirl"),
    /** Undervault's: a stream of pixels flowing at 45 degrees from the bottom left to the top right. */
    STREAM("stream");

    public static final Codec<PortalAnimation> CODEC = StringRepresentable.fromEnum(PortalAnimation::values);
    /** {@code animation}, on the portal block. */
    public static final EnumProperty<PortalAnimation> PROPERTY = EnumProperty.create("animation", PortalAnimation.class);

    private final String serializedName;

    PortalAnimation(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
