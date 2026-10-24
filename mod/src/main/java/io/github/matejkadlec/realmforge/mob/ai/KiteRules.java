package io.github.matejkadlec.realmforge.mob.ai;

/**
 * How a mob kites (architecture 14.2, research BM-22): between two attacks it takes one quick step of
 * {@code stepBlocks}, chosen by {@link Kiting}, at {@code speed} times its walking speed. A custom mob's YAML gives
 * them ({@code caster.kiting}); any mob's goal can use them with {@link KiteStep}.
 *
 * @param stepBlocks how far one step goes, in blocks
 * @param speed the navigation speed modifier of a step: 1 is its walking speed
 */
public record KiteRules(double stepBlocks, double speed) {
    public KiteRules {
        if (!(stepBlocks > 0) || !(speed > 0)) {
            throw new IllegalArgumentException("A kiting step goes somewhere, at some speed: " + stepBlocks + ", "
                    + speed);
        }
    }
}
