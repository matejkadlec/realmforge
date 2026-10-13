package io.github.matejkadlec.realmforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;

class RealmforgeTest {
    @Test
    void idUsesTheModNamespace() {
        assertEquals("realmforge:test_item", Realmforge.id("test_item").toString());
    }

    /** MDG's unit test support boots FML with the mod loaded, so registries are populated. */
    @Test
    void generatedTestItemIsRegistered() {
        assertEquals(GeneratedItems.TEST_ITEM.get(), BuiltInRegistries.ITEM.getValue(Realmforge.id("test_item")));
    }
}
