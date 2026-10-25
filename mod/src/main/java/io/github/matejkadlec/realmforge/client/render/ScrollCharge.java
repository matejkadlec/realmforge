package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.matejkadlec.realmforge.character.Scrolls;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * A charging scroll is drawn like a bow (architecture 10.2; Matěj's playtest of 2026-10-24, after Waystones' Warp
 * Scroll): in third person the player holds it in the bow's pose, and in first person it is pulled back and shakes
 * harder as the charge fills, with vanilla's bow transform over the {@value Scrolls#CHARGE_TICKS} ticks of the charge
 * instead of the bow's 20. A scroll is a plain item whose use animation is none, so the look comes from NeoForge's
 * client item extensions, registered for the scrolls' items: the items of the scroll tags ({@link Scrolls}), which a
 * new scroll joins here too.
 */
public final class ScrollCharge {
    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        @Nullable
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return charging(entity, hand) ? HumanoidModel.ArmPose.BOW_AND_ARROW : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
            InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!charging(player, hand)) {
                return false;
            }
            // Vanilla's arm transform and its bow case (ItemInHandRenderer), drawn over the charge.
            int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(invert * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
            poseStack.translate(invert * -0.2785682F, 0.18344387F, 0.15731531F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
            poseStack.mulPose(Axis.YP.rotationDegrees(invert * 35.3F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(invert * -9.785F));
            float held = Scrolls.CHARGE_TICKS - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
            float power = drawPower(held);
            if (power > 0.1F) {
                poseStack.translate(0.0F, Mth.sin((held - 0.1F) * 1.3F) * (power - 0.1F) * 0.004F, 0.0F);
            }
            poseStack.translate(0.0F, 0.0F, power * 0.04F);
            poseStack.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
            poseStack.mulPose(Axis.YN.rotationDegrees(invert * 45.0F));
            return true;
        }
    };

    private ScrollCharge() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientExtensionsEvent.class,
                event -> event.registerItem(EXTENSIONS, GeneratedItems.OBLIVION_SCROLL));
    }

    /**
     * How far a scroll is drawn {@code heldTicks} into its charge, from 0 to 1: vanilla's bow curve, slow at first and
     * quickening toward the full draw, stretched over the whole charge.
     */
    static float drawPower(float heldTicks) {
        float share = Math.max(0.0F, heldTicks) / Scrolls.CHARGE_TICKS;
        return Math.min(1.0F, (share * share + share * 2.0F) / 3.0F);
    }

    /** Whether {@code entity} is charging the scroll in {@code hand}. */
    private static boolean charging(LivingEntity entity, InteractionHand hand) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0;
    }
}
