package io.github.matejkadlec.realmforge.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * A smithing table recipe that puts a netherite upgrade on a netherite piece (RLM-566), as vanilla's armor trim recipe
 * puts a trim on: the template, a piece of {@code #realmforge:netherite_upgradable} and the upgrade's item make the
 * same piece, enchantments, damage and name kept, carrying {@link ItemComponents#NETHERITE_UPGRADE}. A piece takes one
 * upgrade: one that already carries any is refused, so the table shows no result. The pipeline writes one per upgrade,
 * {@code data/realmforge/recipe/netherite_upgrade/<id>.json}.
 */
public final class NetheriteUpgradeRecipe extends SimpleSmithingRecipe {
    public static final MapCodec<NetheriteUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
                    Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                    Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
                    ResourceKey.codec(ItemRegistries.NETHERITE_UPGRADE).fieldOf("upgrade")
                            .forGetter(recipe -> recipe.upgrade))
            .apply(instance, NetheriteUpgradeRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetheriteUpgradeRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
                    Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
                    Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
                    Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
                    ResourceKey.streamCodec(ItemRegistries.NETHERITE_UPGRADE), recipe -> recipe.upgrade,
                    NetheriteUpgradeRecipe::new);
    public static final RecipeSerializer<NetheriteUpgradeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ResourceKey<NetheriteUpgrade> upgrade;

    public NetheriteUpgradeRecipe(Recipe.CommonInfo commonInfo, Ingredient template, Ingredient base,
            Ingredient addition, ResourceKey<NetheriteUpgrade> upgrade) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.upgrade = upgrade;
    }

    /** The upgrade it puts on. */
    public ResourceKey<NetheriteUpgrade> upgrade() {
        return upgrade;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return super.matches(input, level) && !NetheriteUpgrades.isUpgraded(input.base());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        if (NetheriteUpgrades.isUpgraded(input.base())) {
            return ItemStack.EMPTY;
        }
        ItemStack upgraded = input.base().copyWithCount(1);
        upgraded.set(ItemComponents.NETHERITE_UPGRADE.get(), upgrade);
        return upgraded;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(template);
    }

    @Override
    public Ingredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(addition);
    }

    @Override
    public RecipeSerializer<NetheriteUpgradeRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(List.of(template, base, addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay piece = base.display();
        return List.of(new SmithingRecipeDisplay(template.display(), piece, addition.display(), piece,
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}
