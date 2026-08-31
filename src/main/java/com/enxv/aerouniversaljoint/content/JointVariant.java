package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
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
        return this == ANDESITE
                ? AeroUniversalJointConfig.andesiteJointLinkRange()
                : AeroUniversalJointConfig.brassJointLinkRange();
    }

    public double getSoftRange() {
        return this == ANDESITE
                ? AeroUniversalJointConfig.andesiteJointSoftRange()
                : AeroUniversalJointConfig.brassJointSoftRange();
    }

    public double getDisconnectRange() {
        return this == ANDESITE
                ? AeroUniversalJointConfig.andesiteJointDisconnectRange()
                : AeroUniversalJointConfig.brassJointDisconnectRange();
    }

    public double getMaxForceRange() {
        return this == ANDESITE ? 2.0D : this.getDisconnectRange();
    }

    public boolean isElastic() {
        return elastic;
    }

    public boolean isWithinLinkRange(double distanceSquared) {
        double configuredLinkRange = this.getLinkRange();
        return distanceSquared <= configuredLinkRange * configuredLinkRange;
    }

    public boolean isBeyondDisconnectRange(double distanceSquared) {
        double configuredDisconnectRange = this.getDisconnectRange();
        return distanceSquared > configuredDisconnectRange * configuredDisconnectRange;
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
