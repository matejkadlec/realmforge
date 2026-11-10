package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellIcons;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The spells' icons (A1, RLM-642): every spell the pipeline lists as having one ({@link GeneratedSpellIcons}) is a
 * loaded spell whose sprite is in the mod's resources. The client draws the sprite {@code realmforge:spell/<path>}
 * for a listed spell without asking whether it exists, so a listed spell without its texture would show the missing
 * texture. A spell without an icon is no error: it shows its initials until its art exists.
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
}
