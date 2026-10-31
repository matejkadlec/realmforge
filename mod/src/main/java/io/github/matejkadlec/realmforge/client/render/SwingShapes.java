package io.github.matejkadlec.realmforge.client.render;

import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.MeleeEffect;
import io.github.matejkadlec.realmforge.spell.MeleeShape;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.Holder;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;

/**
 * A melee spell's hit shape drawn while it swings (RLM-399; architecture 18): with vanilla's hitbox display on (F3 + B,
 * the debug screen's Entity Hitboxes entry), the shape of every swing this client knows of, the local player's and
 * every tracked caster's, is drawn as outline lines only, round the caster's look as the server takes it at the hit
 * moment ({@code spell/MeleeShape}), so the shapes can be checked and tuned in a playtest.
 */
public final class SwingShapes implements DebugRenderer.SimpleDebugRenderer {
    /** The outline's colour: Better Combat's hitboxes are white, ours a clear orange. */
    private static final int COLOR = 0xFFFF9020;
    private static final int ARC_SEGMENTS = 12;

    private final Minecraft minecraft;

    private SwingShapes(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDebugRenderersEvent.class, event -> event.register(SwingShapes::new));
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum,
            float partialTicks) {
        if (minecraft.level == null || !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity caster)) {
                continue;
            }
            Optional<CombatState.Cast> cast = ClientCasts.current(caster).filter(CombatState.Cast::swing);
            Optional<MeleeEffect> melee = cast.flatMap(swing -> Spells.get(caster.registryAccess(), swing.spell()))
                    .map(Holder::value).flatMap(SwingShapes::melee);
            if (melee.isEmpty()) {
                continue;
            }
            Vec3 eyes = caster.getEyePosition(partialTicks);
            MeleeShape.Frame frame = MeleeShape.Frame.of(eyes, caster.getViewXRot(partialTicks),
                    caster.getViewYRot(partialTicks));
            for (Vec3[] line : melee.get().geometry().outline(frame, caster.getBbWidth() / 2, ARC_SEGMENTS)) {
                Gizmos.line(line[0], line[1], COLOR);
            }
        }
    }

    /** The spell's first melee shape, if it swings one. */
    private static Optional<MeleeEffect> melee(Spell spell) {
        return spell.effects().stream().filter(MeleeEffect.class::isInstance).map(MeleeEffect.class::cast).findFirst();
    }
}
