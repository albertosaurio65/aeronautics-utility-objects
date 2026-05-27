package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;

public enum JointVariant {
    ANDESITE(15.0D, 1.0D, 2.5D, true),
    BRASS(5.0D, 4.0D, 5.5D, true);

    private final double linkRange;
    private final double stretchSoftRange;
    private final double stretchDisconnectRange;
    private final boolean elastic;

    JointVariant(double linkRange, double stretchSoftRange, double stretchDisconnectRange, boolean elastic) {
        this.linkRange = linkRange;
        this.stretchSoftRange = stretchSoftRange;
        this.stretchDisconnectRange = stretchDisconnectRange;
        this.elastic = elastic;
    }

    public double getLinkRange() {
        return linkRange;
    }

    public double getSoftRange() {
        return stretchSoftRange;
    }

    public double getDisconnectRange() {
        return stretchDisconnectRange;
    }

    public double getMaxForceRange() {
        return this == ANDESITE ? 2.0D : stretchDisconnectRange;
    }

    public boolean isElastic() {
        return elastic;
    }

    public boolean isWithinLinkRange(double distanceSquared) {
        return distanceSquared <= this.linkRange * this.linkRange;
    }

    public boolean isBeyondDisconnectRange(double distanceSquared) {
        return distanceSquared > this.stretchDisconnectRange * this.stretchDisconnectRange;
    }

    public String getSerializedName() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static JointVariant byName(String name) {
        for (JointVariant variant : values()) {
            if (variant.getSerializedName().equals(name)) {
                return variant;
            }
        }
        return null;
    }

    public static JointVariant fromState(BlockState state) {
        return state.is(ModBlocks.BRASS_UNIVERSAL_JOINT.get()) ? BRASS : ANDESITE;
    }
}
