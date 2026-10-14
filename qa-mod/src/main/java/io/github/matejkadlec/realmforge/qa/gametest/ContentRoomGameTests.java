package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.qa.generated.GeneratedRoomExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRoomExpectations.RoomExpectation;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Checks that the game loads every room in {@code content/rooms/} as written, using expectations generated from
 * the same templates ({@link GeneratedRoomExpectations}). The structure manager upgrades an imported room from the
 * data version it was saved with (Minecraft 1.17 for the oldest), so this is where a template the game cannot read
 * shows: it does not load at all (the manager swallows the error), or a block it does not know becomes air. A room
 * dressed with armor stands (tools/realmforge_tools/rooms/dress.py) is placed too: their armor is written in the
 * template's old format, so the stands must come out of the upgrade in full iron or full gold, and with every slot
 * locked: a player in adventure mode, as inside a dungeon, cannot take the armor.
 */
public final class ContentRoomGameTests {
    // High above the test's platform, where the placed room touches no other test.
    private static final BlockPos ABOVE = new BlockPos(0, 40, 0);
    private static final List<Item> IRON = List.of(Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE,
            Items.IRON_HELMET);
    private static final List<Item> GOLD = List.of(Items.GOLDEN_BOOTS, Items.GOLDEN_LEGGINGS,
            Items.GOLDEN_CHESTPLATE, Items.GOLDEN_HELMET);
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS,
            EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    // Where a player's empty hand meets the stand's chestplate (ArmorStand#getClickedSlot).
    private static final Vec3 CHEST_HEIGHT = new Vec3(0.0, 1.2, 0.0);

    private ContentRoomGameTests() {
    }

    @QaGameTest
    public static void contentRoomsLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedRoomExpectations.ROOMS.isEmpty(), "no room expectations were generated");
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        for (RoomExpectation expected : GeneratedRoomExpectations.ROOMS) {
            Optional<StructureTemplate> loaded = templates.get(Identifier.parse(expected.id()));
            helper.assertTrue(loaded.isPresent(), "room does not load: " + expected.id());
            StructureTemplate room = loaded.get();
            helper.assertValueEqual(room.getSize(), new Vec3i(expected.sizeX(), expected.sizeY(), expected.sizeZ()),
                    expected.id() + " size");
            helper.assertValueEqual(room.getJigsaws(BlockPos.ZERO, Rotation.NONE).size(), expected.jigsaws(),
                    expected.id() + " jigsaws");
            helper.assertValueEqual(room.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR).size(),
                    expected.air(), expected.id() + " air blocks (a block the game cannot read becomes air)");
        }
        helper.succeed();
    }

    @QaGameTest
    public static void dressedRoomsKeepTheirArmorStandsArmored(GameTestHelper helper) {
        // The smallest room with armor stands, placed as /place template would.
        RoomExpectation expected = GeneratedRoomExpectations.ROOMS.stream()
                .filter(room -> room.armorStands() > 0)
                .min(Comparator.comparingInt(room -> room.sizeX() * room.sizeY() * room.sizeZ()))
                .orElse(null);
        helper.assertTrue(expected != null, "no room has an armor stand");
        ServerLevel level = helper.getLevel();
        StructureTemplate room = level.getStructureManager().get(Identifier.parse(expected.id())).orElseThrow();
        BlockPos origin = helper.absolutePos(ABOVE);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BoundingBox box = room.getBoundingBox(settings, origin);
        room.placeInWorld(level, origin, origin, settings, RandomSource.create(0), Block.UPDATE_CLIENTS);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, AABB.of(box));
        try {
            helper.assertValueEqual(stands.size(), expected.armorStands(), expected.id() + " armor stands");
            Player player = helper.makeMockPlayer(GameType.ADVENTURE);
            for (ArmorStand stand : stands) {
                helper.assertTrue(wears(stand, IRON) || wears(stand, GOLD),
                        expected.id() + ": an armor stand without a full iron or gold set");
                stand.interact(player, InteractionHand.MAIN_HAND, CHEST_HEIGHT);
                helper.assertTrue(wears(stand, IRON) || wears(stand, GOLD),
                        expected.id() + ": a player took armor off an armor stand");
                helper.assertTrue(player.getMainHandItem().isEmpty(), expected.id() + ": the player got armor");
                helper.assertTrue(stand.isInvulnerable(), expected.id() + ": an armor stand that can be broken");
            }
        } finally {
            stands.forEach(Entity::discard);
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                    box.maxZ())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    private static boolean wears(ArmorStand stand, List<Item> set) {
        for (int i = 0; i < ARMOR.size(); i++) {
            if (stand.getItemBySlot(ARMOR.get(i)).getItem() != set.get(i)) {
                return false;
            }
        }
        return true;
    }
}
