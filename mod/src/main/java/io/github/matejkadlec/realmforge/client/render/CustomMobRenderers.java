package io.github.matejkadlec.realmforge.client.render;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCard;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCompat;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.SpellVisuals;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The renderers of the custom mobs (architecture 14.2, 18): GeckoLib's, with the model, animations and texture of the
 * mob's id ({@code assets/realmforge/geckolib/models/entity/<id>.geo.json}, {@code .../animations/entity/},
 * {@code textures/entity/<id>.png}, a variant's {@code <id>_<variant>.png}), and Neat's card above it as above every
 * vanilla mob ({@link NeatCard}). The animations turn the model's head to where the mob looks. While a custom mob (a
 * caster or a boss) casts a spell that forms its projectile, the projectile's orb grows in its raised hands
 * ({@link SpellOrbs}), where the server launches it from.
 */
public final class CustomMobRenderers {
    /** A formed projectile starts at this share of its size and grows to the whole of it by the end of the cast. */
    private static final float FORM_START = 0.15F;

    private CustomMobRenderers() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            for (var mob : GeneratedMobs.ALL) {
                register(event, mob.get());
            }
        });
    }

    private static <T extends CustomMob> void register(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type) {
        event.registerEntityRenderer(type, context -> new Renderer<>(context, type));
    }

    private static final class Renderer<T extends CustomMob> extends GeoEntityRenderer<T, LivingEntityRenderState> {
        private final Optional<NeatCard> neat;
        private final SpellOrbs orbs;

        Renderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
            super(context, new Model<>(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
            neat = NeatCompat.card(context);
            orbs = new SpellOrbs(context);
        }

        @Override
        public LivingEntityRenderState createRenderState(T animatable, @Nullable Void relatedObject) {
            return new State();
        }

        @Override
        public void extractRenderState(T entity, LivingEntityRenderState state, float partialTick) {
            // Neat's card first: GeckoLib's extraction then writes the state the model renders from.
            neat.ifPresent(card -> card.extract(entity, state, partialTick));
            super.extractRenderState(entity, state, partialTick);
            if (state instanceof State custom) {
                Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                custom.texture = entity.variant()
                        .map(variant -> type.withPath("textures/entity/" + type.getPath() + "_" + variant.id() + ".png"))
                        .orElse(null);
                custom.forming = forming(entity, partialTick);
                custom.formAt = SpellVisuals.formOffset(entity, state.bodyRot);
            }
        }

        /** The projectile {@code caster} forms now, at its size so far, if its cast forms one. */
        private static Optional<Forming> forming(CustomMob caster, float partialTick) {
            Optional<CombatState.Cast> cast = ClientCasts.current(caster);
            if (cast.isEmpty() || cast.get().durationTicks() <= 0) {
                return Optional.empty();
            }
            Optional<ProjectileEffect> projectile = Spells.get(caster.registryAccess(), cast.get().spell())
                    .flatMap(spell -> SpellVisuals.formedProjectile(spell.value()));
            if (projectile.isEmpty()) {
                return Optional.empty();
            }
            float elapsed = caster.level().getGameTime() + partialTick - cast.get().startGameTime();
            float progress = Mth.clamp(elapsed / cast.get().durationTicks(), 0.0F, 1.0F);
            float size = (float) projectile.get().size() * (FORM_START + (1 - FORM_START) * progress);
            return Optional.of(new Forming(SpellVisuals.projectileTexture(cast.get().spell()), size));
        }

        @Override
        public void submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state instanceof State custom && custom.forming.isPresent()) {
                Forming forming = custom.forming.get();
                Vec3 at = custom.formAt;
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                orbs.submit(poseStack, collector, forming.texture(), forming.size(), state.ageInTicks,
                        state.lightCoords, state.outlineColor);
                poseStack.popPose();
            }
        }
    }

    /** The model of a custom mob, its texture the variant's when it has one. */
    private static final class Model<T extends CustomMob> extends DefaultedEntityGeoModel<T> {
        Model(Identifier type) {
            super(type);
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            return renderState instanceof State state && state.texture != null ? state.texture
                    : super.getTextureResource(renderState);
        }
    }

    /**
     * What a frame draws of a custom mob beyond vanilla's living entity: its variant's texture, its forming orb. Not
     * final: GeckoLib makes every living entity's render state a {@code GeoRenderState} at runtime, which its model
     * receives this as.
     */
    private static class State extends LivingEntityRenderState {
        @Nullable Identifier texture;
        Optional<Forming> forming = Optional.empty();
        /** Where it forms, from the mob's feet: in its raised hands ({@link SpellVisuals#formOffset}). */
        Vec3 formAt = Vec3.ZERO;
    }

    /** A projectile forming in a caster's hands: its texture and its size so far, in blocks. */
    private record Forming(Identifier texture, float size) {
    }
}
