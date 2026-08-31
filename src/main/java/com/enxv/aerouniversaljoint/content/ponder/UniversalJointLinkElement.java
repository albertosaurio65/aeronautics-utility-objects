package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.content.JointVariant;
import com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity;
import com.enxv.aerouniversaljoint.content.UniversalJointRenderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UniversalJointLinkElement extends AnimatedSceneElementBase {
    private static final int STRAIN_FLASH_TICKS = 18;

    private final SectionAnchor startAnchor;
    private final SectionAnchor endAnchor;
    private final UniversalJointBlockEntity previewJoint;
    private final JointVariant variant;
    private final double restLength;
    private float coreAngle;

    private Vec3 lastStart;
    private Vec3 currentStart;
    private Vec3 lastEnd;
    private Vec3 currentEnd;
    private boolean overLimitLastTick;
    private int strainFlashTicks;

    public UniversalJointLinkElement(ElementLink<WorldSectionElement> startSection,
                                     ElementLink<WorldSectionElement> endSection,
                                     Vec3 startAnchor, Vec3 endAnchor,
                                     BlockState renderState, JointVariant variant) {
        this(new SectionAnchor(startSection, startAnchor), new SectionAnchor(endSection, endAnchor), renderState, variant);
    }

    public UniversalJointLinkElement(SectionAnchor startAnchor, SectionAnchor endAnchor,
                                     BlockState renderState, JointVariant variant) {
        this.startAnchor = startAnchor;
        this.endAnchor = endAnchor;
        this.previewJoint = new UniversalJointBlockEntity(BlockPos.ZERO, renderState);
        this.variant = variant;
        this.lastStart = startAnchor.position();
        this.currentStart = startAnchor.position();
        this.lastEnd = endAnchor.position();
        this.currentEnd = endAnchor.position();
        this.restLength = this.currentStart.distanceTo(this.currentEnd);
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        this.lastStart = this.currentStart = this.startAnchor.resolve(scene);
        this.lastEnd = this.currentEnd = this.endAnchor.resolve(scene);
        this.overLimitLastTick = this.isOverLimit(this.currentStart.distanceTo(this.currentEnd));
        this.strainFlashTicks = 0;
    }

    @Override
    public void tick(PonderScene scene) {
        this.lastStart = this.currentStart;
        this.lastEnd = this.currentEnd;
        this.currentStart = this.startAnchor.resolve(scene);
        this.currentEnd = this.endAnchor.resolve(scene);
        this.coreAngle += this.variant == JointVariant.BRASS ? 0.18F : 0.08F;

        boolean overLimit = this.isOverLimit(this.currentStart.distanceTo(this.currentEnd));
        if (overLimit && !this.overLimitLastTick) {
            this.strainFlashTicks = STRAIN_FLASH_TICKS;
        } else if (!overLimit) {
            this.strainFlashTicks = 0;
        } else if (this.strainFlashTicks > 0) {
            this.strainFlashTicks--;
        }
        this.overLimitLastTick = overLimit;
    }

    @Override
    public void whileSkipping(PonderScene scene) {
        this.lastStart = this.currentStart = this.startAnchor.resolve(scene);
        this.lastEnd = this.currentEnd = this.endAnchor.resolve(scene);
        this.overLimitLastTick = this.isOverLimit(this.currentStart.distanceTo(this.currentEnd));
        this.strainFlashTicks = 0;
    }

    @Override
    protected void renderLast(PonderLevel level, MultiBufferSource buffer, GuiGraphics graphics, float partialTicks, float fade) {
        Vec3 start = lerp(this.lastStart, this.currentStart, partialTicks);
        Vec3 end = lerp(this.lastEnd, this.currentEnd, partialTicks);
        if (start.distanceToSqr(end) < 1.0E-6D) {
            return;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.cutoutMipped());
        float strainFlash = Mth.clamp(this.strainFlashTicks / (float) STRAIN_FLASH_TICKS, 0.0F, 1.0F);
        Color color = Color.WHITE.copy()
                .mixWith(Color.RED, 0.75F * strainFlash)
                .scaleAlpha(fade);
        UniversalJointRenderer.renderPreviewWithAngle(this.previewJoint, this.variant, start, end, graphics.pose(), consumer,
                LightTexture.FULL_BRIGHT, color, this.coreAngle + partialTicks * (this.variant == JointVariant.BRASS ? 0.18F : 0.08F));
    }

    private boolean isOverLimit(double distance) {
        double warningRange = this.variant.getDisconnectRange() * 0.85D;
        if (this.variant == JointVariant.ANDESITE) {
            return Math.abs(distance - this.restLength) >= warningRange;
        }
        return distance >= warningRange;
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, float partialTicks) {
        return new Vec3(
                Mth.lerp(partialTicks, start.x, end.x),
                Mth.lerp(partialTicks, start.y, end.y),
                Mth.lerp(partialTicks, start.z, end.z));
    }
}
