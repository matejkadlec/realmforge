package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;

/**
 * What a run opened through a dungeon portal knows about it (W4d, architecture 15.5): the dungeon type its key opened
 * and where the portal stands.
 *
 * @param type the dungeon type, an id of {@code realmforge:dungeon_type}
 * @param portal the portal block
 */
public record PortalLink(Identifier type, GlobalPos portal) {
    public static final Codec<PortalLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("type").forGetter(PortalLink::type),
            GlobalPos.CODEC.fieldOf("pos").forGetter(PortalLink::portal)
    ).apply(instance, PortalLink::new));
}
