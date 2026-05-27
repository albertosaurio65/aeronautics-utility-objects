package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.client.AeroUniversalJointPartials;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class UniversalJointRenderer extends KineticBlockEntityRenderer<UniversalJointBlockEntity> {
    private static final double MIN_LINK_LENGTH = 1.0E-3D;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float SLEEVE_LENGTH = 4.0F / 16.0F;
    private static final float CORE_MODEL_LENGTH = 24.0F / PIXELS_PER_BLOCK;
    private static final float MIN_ROLL_REFERENCE_LENGTH_SQUARED = 1.0E-6F;
    private static final Vector3f MODEL_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f MODEL_ROLL_REFERENCE = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f MODEL_SECONDARY_ROLL_REFERENCE = new Vector3f(0.0F, 0.0F, 1.0F);

    public UniversalJointRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderRotatingHead(be, ms, buffer, light);
        renderConnectingRod(be, partialTicks, ms, buffer, light);
    }

    @Override
    public boolean shouldRenderOffScreen(UniversalJointBlockEntity be) {
        return true;
    }

    public static void renderPreview(UniversalJointBlockEntity be, JointVariant variant, Vec3 start, Vec3 end,
                                     PoseStack ms, VertexConsumer buffer, int light, Color color, float partialTicks) {
        Vec3 connection = end.subtract(start);
        double distance = connection.length();
        if (distance < MIN_LINK_LENGTH) {
            return;
        }

        Vec3 normalized = connection.scale(1.0D / distance);
        Vector3f direction = normalized.toVector3f();
        Vector3f reverseDirection = normalized.toVector3f().mul(-1.0F);
        float coreLength = Math.max(0.0F, (float) distance - SLEEVE_LENGTH * 2.0F);
        if (coreLength <= 0.0F) {
            return;
        }

        Direction.Axis axis = getRotationAxisOf(be);
        float coreAngle = getAngleForBe(be, be.getBlockPos(), axis);
        renderPreviewSleeve(be, ms, buffer, light, start, direction, color);
        renderPreviewSleeve(be, ms, buffer, light, end, reverseDirection, color);
        renderPreviewStretchedCore(be, ms, buffer, light, start.add(normalized.scale(SLEEVE_LENGTH)), direction,
                coreAngle, coreLength / CORE_MODEL_LENGTH, color);
        renderPreviewCore(be, ms, buffer, light,
                start.add(normalized.scale(distance * 0.5D - CORE_MODEL_LENGTH * 0.5D)),
                direction, coreAngle, variant, color);
    }

    private void renderRotatingHead(UniversalJointBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.getVariant() != JointVariant.BRASS) {
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(UniversalJointBlock.FACING);
        Direction.Axis axis = getRotationAxisOf(be);
        float angle = getAngleForBe(be, be.getBlockPos(), axis);
        VertexConsumer cutoutBuffer = buffer.getBuffer(RenderType.cutoutMipped());

        SuperByteBuffer head = kineticRotationTransform(
                CachedBuffers.partialFacingVertical(AeroUniversalJointPartials.BRASS_UNIVERSAL_JOINT_HEAD, state, facing),
                be,
                axis,
                angle,
                light);
        head.renderInto(ms, cutoutBuffer);
    }

    private void renderConnectingRod(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        if (!be.hasLink() || be.getLevel() == null) {
            return;
        }

        UniversalJointBlockEntity other = be.getLoadedLinkedJoint();
        if (other == null) {
            return;
        }

        if (!other.references(be)) {
            return;
        }

        if (!shouldRenderFrom(be, other)) {
            return;
        }

        Vec3 ownCenter = projectedCenter(be, partialTicks);
        Vec3 otherCenter = projectedCenter(other, partialTicks);
        Vec3 start = toRenderSpace(be, ownCenter, partialTicks);
        Vec3 end = toRenderSpace(be, otherCenter, partialTicks);
        Vec3 connection = end.subtract(start);
        double distance = connection.length();
        if (distance < MIN_LINK_LENGTH) {
            return;
        }

        Vec3 normalized = connection.scale(1.0D / distance);
        Vector3f coreDirection = normalized.toVector3f();

        VertexConsumer cutoutBuffer = buffer.getBuffer(RenderType.cutoutMipped());

        Vector3f startSleeveDirection = normalized.toVector3f();
        Vector3f endSleeveDirection = normalized.toVector3f().mul(-1.0F);
        RollReference startRollReference = endpointRollReferenceInRenderSpace(be, be, partialTicks);
        RollReference endRollReference = endpointRollReferenceInRenderSpace(be, other, partialTicks);
        Color effectColor = LinkVisualEffects.effectColor(be.aeronautics$getLinkStrainEffect());
        renderSleeve(be, ms, cutoutBuffer, light, start, startSleeveDirection, 0.0F, startRollReference, effectColor);
        renderSleeve(be, ms, cutoutBuffer, light, end, endSleeveDirection, 0.0F, endRollReference, effectColor);

        float coreLength = Math.max(0.0F, (float) distance - SLEEVE_LENGTH * 2.0F);
        if (coreLength <= 0.0F) {
            return;
        }

        JointVariant variant = be.getLinkVariant();
        float coreAngle = coreSpinAngle(be, coreDirection, partialTicks);
        Vec3 coreStart = start.add(normalized.scale(SLEEVE_LENGTH));
        renderStretchedCore(be, ms, cutoutBuffer, light, coreStart, coreDirection, coreAngle, coreLength / CORE_MODEL_LENGTH, variant, effectColor);
        Vec3 coreMiddle = start.add(normalized.scale(distance * 0.5D - CORE_MODEL_LENGTH * 0.5D));
        renderCore(be, ms, cutoutBuffer, light, coreMiddle, coreDirection, coreAngle, variant, effectColor);
    }

    private float coreSpinAngle(UniversalJointBlockEntity be, Vector3f coreDirection, float partialTicks) {
        Direction.Axis axis = getRotationAxisOf(be);
        float angle = getAngleForBe(be, be.getBlockPos(), axis);
        Vector3f direction = new Vector3f(coreDirection);
        if (!normalizeIfUsable(direction)) {
            return angle;
        }

        Vector3f renderAxis = normalToRenderSpace(be, normalToWorld(be, positiveAxis(axis), partialTicks), partialTicks).toVector3f();
        if (!normalizeIfUsable(renderAxis)) {
            return angle;
        }

        return renderAxis.dot(direction) < 0.0F ? -angle : angle;
    }

    private void renderSleeve(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                              Vec3 anchor, Vector3f direction, float angle, RollReference rollReference, Color effectColor) {
        SuperByteBuffer sleeve = CachedBuffers.partial(AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_SLEEVE, be.getBlockState());
        if (sleeve.isEmpty()) {
            return;
        }
        sleeve.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .rotateY(angle + rollCorrection(direction, rollReference))
                .color(effectColor)
                .light(light)
                .renderInto(ms, buffer);
    }

    private RollReference endpointRollReferenceInRenderSpace(UniversalJointBlockEntity renderBe,
                                                             UniversalJointBlockEntity endpoint,
                                                             float partialTicks) {
        Direction facing = endpoint.getBlockState().getValue(UniversalJointBlock.FACING);
        Vector3f primary = modelRollReference(facing, MODEL_ROLL_REFERENCE);
        Vector3f secondary = modelRollReference(facing, MODEL_SECONDARY_ROLL_REFERENCE);
        Direction.Axis axis = getRotationAxisOf(endpoint);
        Vector3f axisVector = positiveAxis(axis);
        Quaternionf kineticRotation = new Quaternionf()
                .rotateAxis(getAngleForBe(endpoint, endpoint.getBlockPos(), axis), axisVector.x(), axisVector.y(), axisVector.z());
        kineticRotation.transform(primary);
        kineticRotation.transform(secondary);

        return new RollReference(
                normalToRenderSpace(renderBe, normalToWorld(endpoint, primary, partialTicks), partialTicks).toVector3f(),
                normalToRenderSpace(renderBe, normalToWorld(endpoint, secondary, partialTicks), partialTicks).toVector3f());
    }

    private Vector3f modelRollReference(Direction facing, Vector3f modelReference) {
        Vector3f reference = new Vector3f(modelReference);
        new Quaternionf()
                .rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(facing)))
                .rotateX(AngleHelper.rad(AngleHelper.verticalAngle(facing) + 90.0F))
                .transform(reference);
        return reference;
    }

    private Vector3f positiveAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(1.0F, 0.0F, 0.0F);
            case Y -> new Vector3f(0.0F, 1.0F, 0.0F);
            case Z -> new Vector3f(0.0F, 0.0F, 1.0F);
        };
    }

    private float rollCorrection(Vector3f direction, RollReference reference) {
        Vector3f axis = new Vector3f(direction);
        if (!normalizeIfUsable(axis)) {
            return 0.0F;
        }

        Quaternionf alignment = new Quaternionf().rotationTo(MODEL_AXIS, axis);
        Float primaryCorrection = rollCorrection(axis, alignment, MODEL_ROLL_REFERENCE, reference.primary());
        if (primaryCorrection != null) {
            return primaryCorrection;
        }

        Float secondaryCorrection = rollCorrection(axis, alignment, MODEL_SECONDARY_ROLL_REFERENCE, reference.secondary());
        return secondaryCorrection != null ? secondaryCorrection : 0.0F;
    }

    private Float rollCorrection(Vector3f axis, Quaternionf alignment, Vector3f modelReference, Vector3f targetReference) {
        Vector3f target = projectOntoPlane(targetReference, axis);
        if (!normalizeIfUsable(target)) {
            return null;
        }

        Vector3f current = alignment.transform(new Vector3f(modelReference));
        current = projectOntoPlane(current, axis);
        if (!normalizeIfUsable(current)) {
            return null;
        }

        Vector3f cross = new Vector3f(current).cross(target);
        return (float) Math.atan2(cross.dot(axis), current.dot(target));
    }

    private Vector3f projectOntoPlane(Vector3f vector, Vector3f normal) {
        return new Vector3f(vector).sub(new Vector3f(normal).mul(vector.dot(normal)));
    }

    private boolean normalizeIfUsable(Vector3f vector) {
        if (vector.lengthSquared() < MIN_ROLL_REFERENCE_LENGTH_SQUARED) {
            return false;
        }
        vector.normalize();
        return true;
    }

    private void renderStretchedCore(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                                     Vec3 anchor, Vector3f direction, float angle, float scale, JointVariant variant, Color effectColor) {
        SuperByteBuffer core = CachedBuffers.partial(AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_CORE_STRETCHED, be.getBlockState());
        if (core.isEmpty()) {
            return;
        }
        core.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .rotateY(angle)
                .scaleY(scale)
                .color(effectColor)
                .light(light)
                .renderInto(ms, buffer);
    }

    private void renderCore(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                            Vec3 anchor, Vector3f direction, float angle, JointVariant variant, Color effectColor) {
        SuperByteBuffer core = CachedBuffers.partial(variant == JointVariant.ANDESITE
                ? AeroUniversalJointPartials.ANDESITE_UNIVERSAL_JOINT_LINK_CORE
                : AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_CORE, be.getBlockState());
        if (core.isEmpty()) {
            return;
        }
        core.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .rotateY(angle)
                .color(effectColor)
                .light(light)
                .renderInto(ms, buffer);
    }

    private static void renderPreviewSleeve(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                                            Vec3 anchor, Vector3f direction, Color color) {
        SuperByteBuffer sleeve = CachedBuffers.partial(AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_SLEEVE, be.getBlockState());
        if (sleeve.isEmpty()) {
            return;
        }
        sleeve.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .color(color)
                .light(light)
                .renderInto(ms, buffer);
    }

    private static void renderPreviewStretchedCore(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                                                   Vec3 anchor, Vector3f direction, float angle, float scale, Color color) {
        SuperByteBuffer core = CachedBuffers.partial(AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_CORE_STRETCHED, be.getBlockState());
        if (core.isEmpty()) {
            return;
        }
        core.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .rotateY(angle)
                .scaleY(scale)
                .color(color)
                .light(light)
                .renderInto(ms, buffer);
    }

    private static void renderPreviewCore(UniversalJointBlockEntity be, PoseStack ms, VertexConsumer buffer, int light,
                                          Vec3 anchor, Vector3f direction, float angle, JointVariant variant, Color color) {
        SuperByteBuffer core = CachedBuffers.partial(variant == JointVariant.ANDESITE
                ? AeroUniversalJointPartials.ANDESITE_UNIVERSAL_JOINT_LINK_CORE
                : AeroUniversalJointPartials.UNIVERSAL_JOINT_LINK_CORE, be.getBlockState());
        if (core.isEmpty()) {
            return;
        }
        core.translate(anchor)
                .rotateTo(MODEL_AXIS, direction)
                .rotateY(angle)
                .color(color)
                .light(light)
                .renderInto(ms, buffer);
    }

    private Vec3 projectedCenter(UniversalJointBlockEntity be, float partialTicks) {
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        SubLevel containing = Sable.HELPER.getContaining(be);
        return containing != null ? getRenderPose(containing, partialTicks).transformPosition(center) : center;
    }

    private Vec3 toRenderSpace(UniversalJointBlockEntity be, Vec3 worldPosition, float partialTicks) {
        Vec3 localPosition = worldPosition;
        SubLevel containing = Sable.HELPER.getContaining(be);
        if (containing != null) {
            localPosition = getRenderPose(containing, partialTicks).transformPositionInverse(worldPosition);
        }

        return localPosition.subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    private Vec3 normalToWorld(UniversalJointBlockEntity be, Vector3f localNormal, float partialTicks) {
        Vec3 normal = new Vec3(localNormal.x(), localNormal.y(), localNormal.z());
        SubLevel containing = Sable.HELPER.getContaining(be);
        return containing != null ? getRenderPose(containing, partialTicks).transformNormal(normal) : normal;
    }

    private Vec3 normalToRenderSpace(UniversalJointBlockEntity be, Vec3 worldNormal, float partialTicks) {
        SubLevel containing = Sable.HELPER.getContaining(be);
        return containing != null ? getRenderPose(containing, partialTicks).transformNormalInverse(worldNormal) : worldNormal;
    }

    private Pose3dc getRenderPose(SubLevel subLevel, float partialTicks) {
        return subLevel instanceof ClientSubLevel clientSubLevel ? clientSubLevel.renderPose(partialTicks) : subLevel.logicalPose();
    }

    private boolean shouldRenderFrom(UniversalJointBlockEntity be, UniversalJointBlockEntity other) {
        BlockPos ownPos = be.getBlockPos();
        BlockPos otherPos = other.getBlockPos();
        int byX = Integer.compare(ownPos.getX(), otherPos.getX());
        if (byX != 0) {
            return byX < 0;
        }

        int byY = Integer.compare(ownPos.getY(), otherPos.getY());
        if (byY != 0) {
            return byY < 0;
        }

        int byZ = Integer.compare(ownPos.getZ(), otherPos.getZ());
        if (byZ != 0) {
            return byZ < 0;
        }

        return SubLevelReferenceHelper.compareNullableUuids(be.getContainingSubLevelId(), other.getContainingSubLevelId()) <= 0;
    }

    private record RollReference(Vector3f primary, Vector3f secondary) {
    }
}
