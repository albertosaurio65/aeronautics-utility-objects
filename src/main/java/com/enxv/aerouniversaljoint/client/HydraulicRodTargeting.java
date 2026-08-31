package com.enxv.aerouniversaljoint.client;

import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.GiantHydraulicRodVisualState;
import com.enxv.aerouniversaljoint.network.OpenHydraulicRodSettingsPayload;
import com.enxv.aerouniversaljoint.network.BreakHydraulicRodPayload;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side hit testing for visual hydraulic rods. */
public final class HydraulicRodTargeting {
    private static final double PIXEL = 1.0D / 16.0D;
    private static final Map<Key, Candidate> CANDIDATES = new LinkedHashMap<>();
    private static Candidate hovered;
    private static long frameCounter;
    private static boolean usePressConsumed;
    private static boolean attackPressConsumed;

    private HydraulicRodTargeting() {}

    public static void register(HydraulicConnectionHeadBlockEntity head, Vec3 start, Vec3 end,
                                Vec3 rollReference, double radius) {
        HydraulicConnectionHeadBlockEntity settingsHead = head.isBrassHingeHead()
                ? head.getLoadedLinkedConnectionHead() : head;
        if (settingsHead == null) return;
        UUID subLevelId = SubLevelReferenceHelper.findContainingSubLevelId(settingsHead);
        CANDIDATES.put(new Key(settingsHead.getBlockPos(), subLevelId), new Candidate(settingsHead.getBlockPos().immutable(), subLevelId,
                start, end, rollReference, radius, head.isGiantHydraulicLink(), frameCounter));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        frameCounter++;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        CANDIDATES.values().removeIf(candidate -> candidate.frame() < frameCounter - 2);
        hovered = pick(minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (hovered == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType lines = RenderType.lines();
        renderModelEdges(poseStack, buffers.getBuffer(lines), hovered);
        buffers.endBatch(lines);
        poseStack.popPose();
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        boolean use = event.isUseItem();
        boolean attack = event.isAttack();
        if (!use && !attack) return;
        if (use) {
            if (usePressConsumed) return;
            usePressConsumed = true;
        }
        if (attack) {
            if (attackPressConsumed) return;
            attackPressConsumed = true;
        }
        if (hovered == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (attack) {
            PacketDistributor.sendToServer(new BreakHydraulicRodPayload(hovered.pos(), hovered.subLevelId()));
        } else {
            PacketDistributor.sendToServer(new OpenHydraulicRodSettingsPayload(hovered.pos(), hovered.subLevelId()));
        }
        event.setCanceled(true);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) usePressConsumed = false;
        if (!minecraft.options.keyAttack.isDown()) attackPressConsumed = false;
    }

    private static Candidate pick(Minecraft minecraft, float partialTicks) {
        Vec3 origin = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = minecraft.player.getViewVector(partialTicks).normalize();
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Candidate candidate : CANDIDATES.values()) {
            double hit = candidate.giant()
                    ? giantHitDistance(origin, direction, candidate)
                    : prismHitDistance(origin, direction, candidate, 0.0D,
                            candidate.end().subtract(candidate.start()).length(),
                            candidate.radius(), 4, Math.PI / 4.0D);
            if (!Double.isFinite(hit) || hit > minecraft.player.blockInteractionRange()) continue;
            if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS
                    && minecraft.hitResult.getLocation().distanceTo(origin) + 0.05D < hit) {
                continue;
            }
            if (hit < bestDistance) {
                bestDistance = hit;
                best = candidate;
            }
        }
        return best;
    }

    private static double giantHitDistance(Vec3 origin, Vec3 direction, Candidate candidate) {
        Vec3 delta = candidate.end().subtract(candidate.start());
        double distance = delta.length();
        if (distance < 1.0E-6D) return Double.POSITIVE_INFINITY;
        Vec3 axis = delta.scale(1.0D / distance);
        Vec3 basisX = candidate.rollReference().subtract(axis.scale(candidate.rollReference().dot(axis)));
        if (basisX.lengthSqr() < 1.0E-6D) {
            basisX = Math.abs(axis.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            basisX = basisX.subtract(axis.scale(basisX.dot(axis)));
        }
        basisX = basisX.normalize();
        Vec3 basisZ = axis.cross(basisX).normalize();
        double best = Double.POSITIVE_INFINITY;
        double barrelRadius = Math.hypot(6.5D, 2.69239D) * PIXEL;
        double barrelPhase = Math.atan2(2.69239D, 6.5D);
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 0.9D * PIXEL,
                Math.min(distance, 19.9D * PIXEL), barrelRadius, 8, barrelPhase, axis, basisX, basisZ));
        double collarRadius = Math.hypot(7.0D, 2.89949D) * PIXEL;
        double collarPhase = Math.atan2(2.89949D, 7.0D);
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 19.9D * PIXEL,
                Math.min(distance, 23.1D * PIXEL), collarRadius, 8, collarPhase, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 23.1D * PIXEL,
                Math.min(distance, 24.9D * PIXEL), barrelRadius, 8, barrelPhase, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 24.9D * PIXEL,
                Math.min(distance, 28.1D * PIXEL), collarRadius, 8, collarPhase, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 28.1D * PIXEL,
                Math.min(distance, 34.1D * PIXEL), barrelRadius, 8, barrelPhase, axis, basisX, basisZ));
        double noseRadius = Math.hypot(5.0D, 2.07107D) * PIXEL;
        double nosePhase = Math.atan2(2.07107D, 5.0D);
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 34.1D * PIXEL,
                Math.min(distance, 37.1D * PIXEL), noseRadius, 8, nosePhase, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 37.1D * PIXEL,
                Math.min(distance, 37.9D * PIXEL), Math.hypot(4.0D, 1.65685D) * PIXEL,
                8, Math.atan2(1.65685D, 4.0D), axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 37.9D * PIXEL,
                Math.min(distance, 39.1D * PIXEL), noseRadius,
                8, nosePhase, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 39.1D * PIXEL,
                Math.min(distance, 40.0D * PIXEL), 3.0D * Math.sqrt(2.0D) * PIXEL,
                4, Math.PI / 4.0D, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate, 0.0D,
                Math.min(distance, 0.9D * PIXEL), Math.hypot(5.5D, 2.27817D) * PIXEL,
                8, Math.atan2(2.27817D, 5.5D), axis, basisX, basisZ));
        GiantHydraulicRodVisualState visual = GiantHydraulicRodVisualState.fromDistance(distance);
        double movingLength = GiantHydraulicRodVisualState.NOMINAL_ROD_LENGTH * visual.continuousScale();
        best = Math.min(best, prismHitDistance(origin, direction, candidate,
                visual.thickOffset(), Math.min(distance, visual.thickOffset() + movingLength),
                2.5D * Math.sqrt(2.0D) / 16.0D, 4, Math.PI / 4.0D, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate,
                visual.mediumOffset(), Math.min(distance, visual.mediumOffset() + movingLength),
                2.0D * Math.sqrt(2.0D) / 16.0D, 4, Math.PI / 4.0D, axis, basisX, basisZ));
        best = Math.min(best, prismHitDistance(origin, direction, candidate,
                visual.thinOffset(), Math.min(distance, visual.thinOffset() + movingLength),
                1.5D * Math.sqrt(2.0D) / 16.0D, 4, Math.PI / 4.0D, axis, basisX, basisZ));
        return best;
    }

    private static double prismHitDistance(Vec3 origin, Vec3 direction, Candidate candidate,
                                           double startY, double endY, double radius, int sides, double phase) {
        Vec3 delta = candidate.end().subtract(candidate.start());
        double distance = delta.length();
        if (distance < 1.0E-6D) return Double.POSITIVE_INFINITY;
        Vec3 axis = delta.scale(1.0D / distance);
        Vec3 basisX = candidate.rollReference().subtract(axis.scale(candidate.rollReference().dot(axis)));
        if (basisX.lengthSqr() < 1.0E-6D) {
            basisX = Math.abs(axis.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            basisX = basisX.subtract(axis.scale(basisX.dot(axis)));
        }
        basisX = basisX.normalize();
        return prismHitDistance(origin, direction, candidate, startY, endY, radius, sides, phase,
                axis, basisX, axis.cross(basisX).normalize());
    }

    private static double prismHitDistance(Vec3 origin, Vec3 direction, Candidate candidate,
                                           double startY, double endY, double radius, int sides, double phase,
                                           Vec3 axis, Vec3 basisX, Vec3 basisZ) {
        if (endY <= startY + 1.0E-6D) return Double.POSITIVE_INFINITY;
        Vec3 relative = origin.subtract(candidate.start());
        double ox = relative.dot(basisX), oy = relative.dot(axis), oz = relative.dot(basisZ);
        double dx = direction.dot(basisX), dy = direction.dot(axis), dz = direction.dot(basisZ);
        double tMin = 0.0D, tMax = 64.0D;
        if (Math.abs(dy) < 1.0E-9D) {
            if (oy < startY - 1.0E-7D || oy > endY + 1.0E-7D) return Double.POSITIVE_INFINITY;
        } else {
            double a = (startY - oy) / dy, b = (endY - oy) / dy;
            if (a > b) { double tmp = a; a = b; b = tmp; }
            tMin = Math.max(tMin, a); tMax = Math.min(tMax, b);
            if (tMin > tMax) return Double.POSITIVE_INFINITY;
        }
        for (int i = 0; i < sides; i++) {
            double a1 = phase + Math.PI * 2.0D * i / sides;
            double a2 = phase + Math.PI * 2.0D * ((i + 1) % sides) / sides;
            double x1 = Math.cos(a1) * radius, z1 = Math.sin(a1) * radius;
            double ex = Math.cos(a2) * radius - x1, ez = Math.sin(a2) * radius - z1;
            double centerValue = ex * (0.0D - z1) - ez * (0.0D - x1);
            double sign = centerValue >= 0.0D ? 1.0D : -1.0D;
            double value = sign * (ex * (oz - z1) - ez * (ox - x1));
            double slope = sign * (ex * dz - ez * dx);
            if (Math.abs(slope) < 1.0E-9D) {
                if (value < -1.0E-7D) return Double.POSITIVE_INFINITY;
            } else if (slope > 0.0D) {
                tMin = Math.max(tMin, -value / slope);
            } else {
                tMax = Math.min(tMax, -value / slope);
            }
            if (tMin > tMax) return Double.POSITIVE_INFINITY;
        }
        return tMin <= tMax ? tMin : Double.POSITIVE_INFINITY;
    }

    private static void renderModelEdges(PoseStack poseStack, VertexConsumer consumer, Candidate candidate) {
        Vec3 axis = candidate.end().subtract(candidate.start());
        double distance = axis.length();
        if (distance < 1.0E-6D) return;
        axis = axis.scale(1.0D / distance);
        Vec3 basisX = candidate.rollReference().subtract(axis.scale(candidate.rollReference().dot(axis)));
        if (basisX.lengthSqr() < 1.0E-6D) {
            basisX = Math.abs(axis.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            basisX = basisX.subtract(axis.scale(basisX.dot(axis)));
        }
        basisX = basisX.normalize();
        Vec3 basisZ = axis.cross(basisX).normalize();

        if (!candidate.giant()) {
            renderPrism(poseStack, consumer, candidate.start().add(axis.scale(1.0D / 16.0D)),
                    candidate.end().subtract(axis.scale(1.0D / 16.0D)), basisX, basisZ,
                    Math.sqrt(2.0D) * 3.5D / 16.0D, 4, Math.PI / 4.0D);
            return;
        }

        renderFixedBarrelEdges(poseStack, consumer, candidate, axis, basisX, basisZ, distance);

        GiantHydraulicRodVisualState visual = GiantHydraulicRodVisualState.fromDistance(distance);
        double movingLength = GiantHydraulicRodVisualState.NOMINAL_ROD_LENGTH * visual.continuousScale();
        double thickEnd = visual.thickOffset() + movingLength;
        double mediumEnd = visual.mediumOffset() + movingLength;
        double thinEnd = visual.thinOffset() + movingLength;
        renderMovingStage(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                visual.thickOffset(), Math.min(distance, thickEnd), 2.5D * PIXEL);
        renderMovingStage(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                Math.max(visual.mediumOffset(), thickEnd), Math.min(distance, mediumEnd), 2.0D * PIXEL);
        renderMovingStage(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                Math.max(visual.thinOffset(), Math.max(thickEnd, mediumEnd)),
                Math.min(distance, thinEnd), 1.5D * PIXEL);
    }

    private static void renderMovingStage(PoseStack poseStack, VertexConsumer consumer, Vec3 owner, Vec3 axis,
                                          Vec3 basisX, Vec3 basisZ, double start, double end, double halfWidth) {
        if (end <= start + 1.0E-6D) return;
        renderPrism(poseStack, consumer, owner.add(axis.scale(start)), owner.add(axis.scale(end)),
                basisX, basisZ, halfWidth * Math.sqrt(2.0D), 4, Math.PI / 4.0D);
    }

    private static void renderFixedBarrelEdges(PoseStack poseStack, VertexConsumer consumer,
                                               Candidate candidate, Vec3 axis, Vec3 basisX, Vec3 basisZ,
                                               double distance) {
        double barrelRadius = Math.hypot(6.5D, 2.69239D) * PIXEL;
        double barrelPhase = Math.atan2(2.69239D, 6.5D);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                0.9D * PIXEL, Math.min(distance, 19.9D * PIXEL), barrelRadius, barrelPhase);
        double collarRadius = Math.hypot(7.0D, 2.89949D) * PIXEL;
        double collarPhase = Math.atan2(2.89949D, 7.0D);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                19.9D * PIXEL, Math.min(distance, 23.1D * PIXEL), collarRadius, collarPhase);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                23.1D * PIXEL, Math.min(distance, 24.9D * PIXEL), barrelRadius, barrelPhase);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                24.9D * PIXEL, Math.min(distance, 28.1D * PIXEL), collarRadius, collarPhase);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                28.1D * PIXEL, Math.min(distance, 34.1D * PIXEL), barrelRadius, barrelPhase);
        double noseRadius = Math.hypot(5.0D, 2.07107D) * PIXEL;
        double nosePhase = Math.atan2(2.07107D, 5.0D);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                34.1D * PIXEL, Math.min(distance, 37.1D * PIXEL), noseRadius, nosePhase);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                37.1D * PIXEL, Math.min(distance, 37.9D * PIXEL),
                Math.hypot(4.0D, 1.65685D) * PIXEL, Math.atan2(1.65685D, 4.0D));
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                37.9D * PIXEL, Math.min(distance, 39.1D * PIXEL), noseRadius, nosePhase);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                39.1D * PIXEL, Math.min(distance, 40.0D * PIXEL),
                3.0D * Math.sqrt(2.0D) * PIXEL, Math.PI / 4.0D, 4);
        renderProfile(poseStack, consumer, candidate.start(), axis, basisX, basisZ,
                0.0D, Math.min(distance, 0.9D * PIXEL),
                Math.hypot(5.5D, 2.27817D) * PIXEL, Math.atan2(2.27817D, 5.5D));
    }

    private static void renderProfile(PoseStack poseStack, VertexConsumer consumer, Vec3 owner, Vec3 axis,
                                      Vec3 basisX, Vec3 basisZ, double start, double end,
                                      double radius, double phase) {
        if (end <= start + 1.0E-6D) return;
        renderPrism(poseStack, consumer, owner.add(axis.scale(start)), owner.add(axis.scale(end)),
                basisX, basisZ, radius, 8, phase);
    }

    private static void renderProfile(PoseStack poseStack, VertexConsumer consumer, Vec3 owner, Vec3 axis,
                                      Vec3 basisX, Vec3 basisZ, double start, double end,
                                      double radius, double phase, int sides) {
        if (end <= start + 1.0E-6D) return;
        renderPrism(poseStack, consumer, owner.add(axis.scale(start)), owner.add(axis.scale(end)),
                basisX, basisZ, radius, sides, phase);
    }

    private static void renderPrism(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end,
                                    Vec3 basisX, Vec3 basisZ, double radius, int sides, double phase) {
        Vec3[] startRing = new Vec3[sides];
        Vec3[] endRing = new Vec3[sides];
        for (int i = 0; i < sides; i++) {
            double angle = phase + Math.PI * 2.0D * i / sides;
            Vec3 radial = basisX.scale(Math.cos(angle) * radius).add(basisZ.scale(Math.sin(angle) * radius));
            startRing[i] = start.add(radial);
            endRing[i] = end.add(radial);
        }
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            line(poseStack, consumer, startRing[i], startRing[next]);
            line(poseStack, consumer, endRing[i], endRing[next]);
            line(poseStack, consumer, startRing[i], endRing[i]);
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        Vec3 normal = to.subtract(from).normalize();
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(5, 5, 5, 255).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(5, 5, 5, 255).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record Key(BlockPos pos, UUID subLevelId) {}
    private record Candidate(BlockPos pos, UUID subLevelId, Vec3 start, Vec3 end, Vec3 rollReference,
                             double radius, boolean giant, long frame) {}
}
