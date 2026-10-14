package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A room's connector: a jigsaw block on the room's boundary, pointing out, as {@code rf gen} reads it from the
 * template ({@code tools/realmforge_tools/rooms/connectors.py}). Two rooms join where a connector of one meets a
 * connector of the other by vanilla's jigsaw rules ({@link #takes}).
 *
 * @param pos where it sits in the room, relative to the room's origin corner
 * @param front the way it points: out of the room
 * @param top the way its top faces: up for a connector on a side, a side for one up or down
 * @param name its jigsaw name, which a connector it takes must target
 * @param target the name of the connectors it takes
 * @param joint whether a vertical connector keeps its top ({@code aligned}) or may turn ({@code rollable})
 */
public record Connector(BlockPos pos, Direction front, Direction top, Identifier name, Identifier target,
        JigsawBlockEntity.JointType joint) {
    public static final Codec<Connector> CODEC = RecordCodecBuilder.<Connector>create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(Connector::pos),
                    Direction.CODEC.fieldOf("front").forGetter(Connector::front),
                    Direction.CODEC.fieldOf("top").forGetter(Connector::top),
                    Identifier.CODEC.fieldOf("name").forGetter(Connector::name),
                    Identifier.CODEC.fieldOf("target").forGetter(Connector::target),
                    JigsawBlockEntity.JointType.CODEC.fieldOf("joint").forGetter(Connector::joint))
            .apply(instance, Connector::new)).validate(Connector::validate);

    /** Whether it points up or down. */
    public boolean vertical() {
        return front.getAxis() == Direction.Axis.Y;
    }

    /**
     * This connector once its room is turned by {@code rotation} about the room's origin corner and moved by
     * {@code offset}: where {@code StructureTemplate} places the jigsaw block, and the way it faces then.
     */
    public Connector placed(BlockPos offset, Rotation rotation) {
        BlockPos turned = StructureTemplate.transform(pos, Mirror.NONE, rotation, BlockPos.ZERO);
        return new Connector(turned.offset(offset), rotation.rotate(front), rotation.rotate(top), name, target,
                joint);
    }

    /**
     * Vanilla's {@code JigsawBlock#canAttach} for two placed connectors, this one the source: the fronts are
     * opposite, the tops are the same unless this joint is rollable, and this target is the other's name. The other
     * connector's block then sits right in front of this one.
     */
    public boolean takes(Connector other) {
        return front == other.front.getOpposite()
                && (joint == JigsawBlockEntity.JointType.ROLLABLE || top == other.top)
                && target.equals(other.name);
    }

    /** The orientations a jigsaw block can have: a side with its top up, or up or down with its top on a side. */
    private static DataResult<Connector> validate(Connector connector) {
        boolean sideways = connector.top.getAxis().isHorizontal();
        if (connector.vertical() != sideways) {
            return DataResult.error(() -> "connector at " + connector.pos.toShortString() + " faces "
                    + connector.front.getSerializedName() + " with its top " + connector.top.getSerializedName()
                    + ", which no jigsaw block can");
        }
        return DataResult.success(connector);
    }
}
