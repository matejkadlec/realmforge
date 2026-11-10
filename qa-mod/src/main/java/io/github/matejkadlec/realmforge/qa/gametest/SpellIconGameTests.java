package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellIcons;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;

/**
 * The spells' icons (A1, RLM-642): every spell the pipeline lists as having one ({@link GeneratedSpellIcons}) is a
 * loaded spell whose sprite is in the mod's resources, and every spell a player can have has one. The client draws
 * the sprite {@code realmforge:spell/<path>} for a listed spell without asking whether it exists, so a listed spell
 * without its texture would show the missing texture.
 */
public final class SpellIconGameTests {
    private static final String SPRITES = "/assets/" + Realmforge.MOD_ID + "/textures/gui/sprites/spell/";

    private SpellIconGameTests() {
    }

    @QaGameTest
    public static void everyListedIconIsALoadedSpellWithItsSprite(GameTestHelper helper) {
        Registry<Spell> spells = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL);
        helper.assertFalse(GeneratedSpellIcons.SPELLS.isEmpty(), "no spell has an icon");
        for (String path : GeneratedSpellIcons.SPELLS) {
            helper.assertTrue(spells.containsKey(Realmforge.id(path)), "an icon for no spell: " + path);
            helper.assertTrue(Realmforge.class.getResource(SPRITES + path + ".png") != null,
                    "the icon's sprite is not in the mod's resources: " + path);
        }
        helper.succeed();
    }

    /** Every spell of ours that a player can have shows its icon: only test spells and mobs' spells show initials. */
    @QaGameTest
    public static void everyPlayerSpellHasAnIcon(GameTestHelper helper) {
        Registry<Spell> spells = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL);
        for (ResourceKey<Spell> key : GeneratedSpells.ALL) {
            String path = key.identifier().getPath();
            Spell spell = spells.getValueOrThrow(key);
            if (!spell.npcOnly() && !path.startsWith("test_")) {
                helper.assertTrue(GeneratedSpellIcons.SPELLS.contains(path),
                        "a player's spell without an icon: " + path);
            }
        }
        helper.succeed();
    }
}
