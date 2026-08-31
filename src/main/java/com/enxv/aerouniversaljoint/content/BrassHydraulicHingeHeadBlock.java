package com.enxv.aerouniversaljoint.content;

import net.minecraft.world.level.block.state.BlockState;

/** Brass variant of the hinge head.  The block id is separate so old
 * hydraulic_hinge_head saves continue to load as the andesite variant. */
public final class BrassHydraulicHingeHeadBlock extends HydraulicHingeHeadBlock {
    public BrassHydraulicHingeHeadBlock(Properties properties) {
        super(properties);
    }

    public static boolean isBrass(BlockState state) {
        return state.getBlock() instanceof BrassHydraulicHingeHeadBlock;
    }
}
