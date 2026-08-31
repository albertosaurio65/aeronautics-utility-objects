package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create/Simulated-facing kinetic output for a damping bearing.
 *
 * <p>The parent owns Sable physics sampling and assembly recovery. This
 * auxiliary entity converts those samples into a stable Create generator.</p>
 */
class DampingOutputKinetics extends GeneratingKineticBlockEntity
        implements ExtraKinetics.ExtraKineticsBlockEntity, DetachedKineticSafetyGuard {
    private static final String TAG_GENERATED_SPEED = "GeneratedSpeed";
    private static final String TAG_PUBLISHED_STRESS_CAPACITY = "PublishedStressCapacity";
    private static final float SPEED_EPSILON = 1.0E-3F;
    private static final long OUTPUT_PUBLISH_INTERVAL_TICKS = 20L;
    private static final float OUTPUT_ZERO_DEADBAND_RPM = 1.0F;
    private static final float OUTPUT_PUBLISHED_STEP_RPM = 1.0F;
    private static final int OUTPUT_SOURCE_GRACE_TICKS = 40;

    static final IRotate CONFIG = new IRotate() {
        @Override
        public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state,
                                       Direction face) {
            return face == state.getValue(SwivelBearingBlock.FACING).getOpposite();
        }

        @Override
        public Direction.Axis getRotationAxis(BlockState state) {
            return state.getValue(SwivelBearingBlock.FACING).getAxis();
        }
    };

    private final DampingStressBearingBlockEntity parent;
    private int customValidationCountdown;
    private float generatedSpeed;
    private float publishedStressCapacity;
    private boolean forceUpdate;
    private int invalidSourceTicks;
    private long nextPublishTick = Long.MIN_VALUE;

    DampingOutputKinetics(BlockEntityType<?> type, ExtraBlockPos pos, BlockState state,
                          DampingStressBearingBlockEntity parent) {
        super(type, pos, state);
        this.parent = parent;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.generatedSpeed = 0.0F;
        this.publishedStressCapacity = 0.0F;
        this.invalidSourceTicks = 0;
        this.nextPublishTick = Long.MIN_VALUE;
        this.reActivateSource = true;
        this.forceUpdate = true;
    }

    @Override
    public void tick() {
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide) {
            super.tick();
            return;
        }

        ((KineticBlockEntityExtension) this).simulated$setValidationCountdown(Integer.MAX_VALUE);
        if (--this.customValidationCountdown <= 0) {
            this.customValidationCountdown = AllConfigs.server().kinetics.kineticValidationFrequency.get();
            this.customValidateKinetics();
        }

        float previousSpeed = this.generatedSpeed;
        float previousStressCapacity = this.publishedStressCapacity;
        if (this.shouldPublishOutput()) {
            this.publishOutput(this.parent.measureOutputSpeed());
        }
        super.tick();

        if (this.forceUpdate
                || Math.abs(previousSpeed - this.generatedSpeed) > SPEED_EPSILON
                || Math.abs(previousStressCapacity - this.publishedStressCapacity) > SPEED_EPSILON) {
            this.forceUpdate = false;
            this.updateGeneratedRotation();
        }
    }

    private boolean shouldPublishOutput() {
        return this.forceUpdate
                || this.nextPublishTick == Long.MIN_VALUE
                || this.level != null && this.level.getGameTime() >= this.nextPublishTick;
    }

    private void publishOutput(float measuredSpeed) {
        float targetSpeed = this.quantizePublishedSpeed(measuredSpeed);
        float clampedSpeed = Math.signum(targetSpeed)
                * Math.min(Math.abs(targetSpeed), AeroUniversalJointConfig.dampingRatedOutputSpeedRpm());
        if (this.needsSafeReversal(clampedSpeed)) {
            this.generatedSpeed = 0.0F;
            this.publishedStressCapacity = 0.0F;
            this.restartKineticOutput();
        } else {
            this.generatedSpeed = clampedSpeed;
            this.publishedStressCapacity = this.parent.calculateStressCapacity(targetSpeed);
        }
        if (this.level != null) {
            this.nextPublishTick = this.level.getGameTime() + OUTPUT_PUBLISH_INTERVAL_TICKS;
        }
    }

    private float quantizePublishedSpeed(float speed) {
        float roundedSpeed = Math.round(speed / OUTPUT_PUBLISHED_STEP_RPM) * OUTPUT_PUBLISHED_STEP_RPM;
        return Math.abs(roundedSpeed) < OUTPUT_ZERO_DEADBAND_RPM ? 0.0F : roundedSpeed;
    }

    private void restartKineticOutput() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (!this.hasNetwork() && !this.hasSource() && Math.abs(this.getSpeed()) <= SPEED_EPSILON) {
            return;
        }

        this.detachKinetics();
        this.removeSource();
        this.reActivateSource = false;
        this.invalidSourceTicks = 0;
        this.forceUpdate = true;
    }

    private boolean needsSafeReversal(float targetSpeed) {
        float actualSpeed = this.getSpeed();
        return Math.abs(targetSpeed) > OUTPUT_ZERO_DEADBAND_RPM
                && Math.abs(actualSpeed) > OUTPUT_ZERO_DEADBAND_RPM
                && Math.signum(targetSpeed) != Math.signum(actualSpeed);
    }

    private void customValidateKinetics() {
        if (this.parent.isRuntimeResyncActive()) {
            this.invalidSourceTicks = 0;
            return;
        }
        if (!this.hasSource()) {
            this.invalidSourceTicks = 0;
            return;
        }
        if (!this.hasNetwork()) {
            if (++this.invalidSourceTicks > OUTPUT_SOURCE_GRACE_TICKS) {
                this.removeSource();
                this.invalidSourceTicks = 0;
            }
            return;
        }
        if (!this.level.isLoaded(this.source)) {
            this.invalidSourceTicks = 0;
            return;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(this.source);
        if (blockEntity instanceof ExtraKinetics extraKinetics
                && ((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {
            blockEntity = extraKinetics.getExtraKinetics();
        }

        KineticBlockEntity source = blockEntity instanceof KineticBlockEntity kinetic ? kinetic : null;
        if (source == null || source.getTheoreticalSpeed() == 0.0F) {
            if (++this.invalidSourceTicks > OUTPUT_SOURCE_GRACE_TICKS) {
                this.removeSource();
                this.detachKinetics();
                this.invalidSourceTicks = 0;
            }
            return;
        }
        this.invalidSourceTicks = 0;
    }

    void requestUpdate() {
        this.forceUpdate = true;
        this.invalidSourceTicks = 0;
        this.nextPublishTick = Long.MIN_VALUE;
    }

    @Override
    public float getGeneratedSpeed() {
        return this.generatedSpeed;
    }

    @Override
    public float calculateStressApplied() {
        return 0.0F;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(this.getTheoreticalSpeed());
        if (this.parent.isStressOutputSuppressed() && this.publishedStressCapacity > 0.0F) {
            float capacityPerUnitSpeed = this.publishedStressCapacity / Math.max(speed, 1.0F);
            this.lastCapacityProvided = capacityPerUnitSpeed;
            return capacityPerUnitSpeed;
        }
        if (speed <= SPEED_EPSILON) {
            this.lastCapacityProvided = 0.0F;
            return 0.0F;
        }
        float capacityPerUnitSpeed = this.publishedStressCapacity / speed;
        this.lastCapacityProvided = capacityPerUnitSpeed;
        return capacityPerUnitSpeed;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!added && this.parent.isAssembled()) {
            float maxCapacity = this.parent.isStressOutputSuppressed()
                    ? AeroUniversalJointConfig.dampingSuppressedStressOutput()
                    : this.parent.getFullSpeedStressOutput();
            if (maxCapacity > 0.0F) {
                com.simibubi.create.foundation.utility.CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
                com.simibubi.create.foundation.utility.CreateLang.translate("tooltip.capacityProvided")
                        .style(ChatFormatting.GRAY).forGoggles(tooltip);
                com.simibubi.create.foundation.utility.CreateLang.number(this.publishedStressCapacity)
                        .translate("generic.unit.stress").style(ChatFormatting.AQUA).space()
                        .add(com.simibubi.create.foundation.utility.CreateLang.translate("gui.goggles.at_current_speed")
                                .style(ChatFormatting.DARK_GRAY))
                        .forGoggles(tooltip, 1);
                return true;
            }
        }
        return added;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat(TAG_GENERATED_SPEED, this.generatedSpeed);
            tag.putFloat(TAG_PUBLISHED_STRESS_CAPACITY, this.publishedStressCapacity);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.generatedSpeed = clientPacket && tag.contains(TAG_GENERATED_SPEED) ? tag.getFloat(TAG_GENERATED_SPEED) : 0.0F;
        this.publishedStressCapacity = clientPacket && tag.contains(TAG_PUBLISHED_STRESS_CAPACITY)
                ? tag.getFloat(TAG_PUBLISHED_STRESS_CAPACITY) : 0.0F;
        this.invalidSourceTicks = 0;
        this.nextPublishTick = Long.MIN_VALUE;
        this.reActivateSource = true;
        this.forceUpdate = true;
    }

    @Override
    public KineticBlockEntity getParentBlockEntity() {
        return this.parent;
    }
}
