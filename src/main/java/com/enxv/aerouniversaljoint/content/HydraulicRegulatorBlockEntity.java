package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class HydraulicRegulatorBlockEntity extends KineticBlockEntity {
    private static final float STRESS_IMPACT = 8.0F;
    private static final double MIN_TRANSITION_SPEED = 16.0D;
    private static final double MAX_TRANSITION_SPEED = 256.0D;
    private static final double MIN_TRANSITION_MULTIPLIER = 0.5D;
    private static final double MAX_TRANSITION_MULTIPLIER = 4.0D;
    private int lastAppliedSignal = -1;
    @Nullable
    private HydraulicConnectionHeadBlockEntity cachedControlledHead;
    @Nullable
    private BlockPos cachedControlledHeadPos;
    @Nullable
    private UUID cachedControlledHeadSubLevelId;

    public HydraulicRegulatorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HYDRAULIC_REGULATOR.get(), pos, state);
    }

    public HydraulicRegulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.setPowered(this.level.getBestNeighborSignal(this.worldPosition) > 0);

        HydraulicConnectionHeadBlockEntity target = this.getControlledHead();
        if (target == null || !target.hasLink()) {
            this.lastAppliedSignal = -1;
            return;
        }

        RegulatorCommand command = this.getBestCommand(target);
        if (command == null) {
            target.setExpectedLengthApproachMultiplierAndMirror(1.0D);
            this.lastAppliedSignal = -1;
            return;
        }

        int expectedLengthTenths = target.getExpectedLengthTenthsForRedstoneSignal(command.signal());
        if (command.signal() == this.lastAppliedSignal
                && !target.isFreeMode()
                && target.getExpectedLengthTenths() == expectedLengthTenths) {
            target.setExpectedLengthApproachMultiplierAndMirror(command.transitionMultiplier());
            return;
        }

        target.setSettingsAndMirror(target.getStretchResistance(), false, expectedLengthTenths, target.getReturnForce(),
                command.transitionMultiplier());
        this.lastAppliedSignal = command.signal();
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    private HydraulicConnectionHeadBlockEntity getControlledHead() {
        if (this.level == null) {
            return null;
        }

        Direction facing = this.getBlockState().getValue(HydraulicRegulatorBlock.FACING);
        BlockPos targetPos = this.worldPosition.relative(facing);
        UUID containingSubLevelId = SubLevelReferenceHelper.findContainingSubLevelId(this);
        HydraulicConnectionHeadBlockEntity cached = this.cachedControlledHead;
        if (this.isCachedControlledHeadValid(cached, targetPos, containingSubLevelId, facing)) {
            return cached;
        }

        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntityFast(this.level, targetPos, containingSubLevelId);
        if (!(blockEntity instanceof HydraulicConnectionHeadBlockEntity)) {
            blockEntity = SubLevelReferenceHelper.resolveBlockEntity(this.level, targetPos, containingSubLevelId);
        }
        if (!(blockEntity instanceof HydraulicConnectionHeadBlockEntity head)) {
            this.clearCachedControlledHead();
            return null;
        }

        BlockState state = head.getBlockState();
        if (!state.hasProperty(HydraulicConnectionHeadBlock.FACING)
                || state.getValue(HydraulicConnectionHeadBlock.FACING) != facing) {
            this.clearCachedControlledHead();
            return null;
        }

        this.cachedControlledHead = head;
        this.cachedControlledHeadPos = targetPos;
        this.cachedControlledHeadSubLevelId = containingSubLevelId;
        return head;
    }

    private RegulatorCommand getBestCommand(HydraulicConnectionHeadBlockEntity target) {
        RegulatorCommand best = this.getCommandForHead(target);
        HydraulicConnectionHeadBlockEntity other = target.getLoadedLinkedConnectionHead();
        if (other != null && other.references(target)) {
            best = RegulatorCommand.max(best, this.getCommandForHead(other));
        }
        return best;
    }

    private RegulatorCommand getCommandForHead(HydraulicConnectionHeadBlockEntity head) {
        if (this.level == null || !head.hasLink()) {
            return null;
        }

        BlockState headState = head.getBlockState();
        if (!headState.hasProperty(HydraulicConnectionHeadBlock.FACING)) {
            return null;
        }

        Direction facing = headState.getValue(HydraulicConnectionHeadBlock.FACING);
        BlockPos regulatorPos = head.getBlockPos().relative(facing.getOpposite());
        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntityFast(this.level, regulatorPos, head.getContainingSubLevelId());
        if (!(blockEntity instanceof HydraulicRegulatorBlockEntity)) {
            blockEntity = SubLevelReferenceHelper.resolveBlockEntity(this.level, regulatorPos, head.getContainingSubLevelId());
        }
        if (!(blockEntity instanceof HydraulicRegulatorBlockEntity regulator) || !regulator.controlsHead(head)) {
            return null;
        }

        if (regulator.getSpeed() == 0.0F || regulator.isOverStressed()) {
            return null;
        }

        int signal = this.level.getBestNeighborSignal(regulator.worldPosition);
        return new RegulatorCommand(signal, regulator.getTransitionMultiplier());
    }

    boolean controlsHead(HydraulicConnectionHeadBlockEntity head) {
        if (this.level == null || head == null) {
            return false;
        }

        Direction facing = this.getBlockState().getValue(HydraulicRegulatorBlock.FACING);
        if (!this.worldPosition.relative(facing).equals(head.getBlockPos())) {
            return false;
        }
        if (!Objects.equals(SubLevelReferenceHelper.findContainingSubLevelId(this), head.getContainingSubLevelId())) {
            return false;
        }

        BlockState headState = head.getBlockState();
        return headState.hasProperty(HydraulicConnectionHeadBlock.FACING)
                && headState.getValue(HydraulicConnectionHeadBlock.FACING) == facing;
    }

    private boolean isCachedControlledHeadValid(@Nullable HydraulicConnectionHeadBlockEntity head, BlockPos targetPos,
                                                @Nullable UUID containingSubLevelId, Direction facing) {
        if (head == null
                || this.level == null
                || head.isRemoved()
                || head.getLevel() != this.level
                || !targetPos.equals(this.cachedControlledHeadPos)
                || !targetPos.equals(head.getBlockPos())
                || !Objects.equals(containingSubLevelId, this.cachedControlledHeadSubLevelId)
                || !Objects.equals(containingSubLevelId, head.getContainingSubLevelId())) {
            return false;
        }

        BlockState state = head.getBlockState();
        return state.hasProperty(HydraulicConnectionHeadBlock.FACING)
                && state.getValue(HydraulicConnectionHeadBlock.FACING) == facing;
    }

    private void clearCachedControlledHead() {
        this.cachedControlledHead = null;
        this.cachedControlledHeadPos = null;
        this.cachedControlledHeadSubLevelId = null;
    }

    private double getTransitionMultiplier() {
        double speed = Math.abs(this.getSpeed());
        if (!Double.isFinite(speed) || speed <= 0.0D) {
            return 1.0D;
        }
        if (speed <= MIN_TRANSITION_SPEED) {
            return MIN_TRANSITION_MULTIPLIER;
        }
        if (speed >= MAX_TRANSITION_SPEED) {
            return MAX_TRANSITION_MULTIPLIER;
        }

        double normalized = Math.log(speed / MIN_TRANSITION_SPEED) / Math.log(MAX_TRANSITION_SPEED / MIN_TRANSITION_SPEED);
        double multiplier = MIN_TRANSITION_MULTIPLIER
                * Math.pow(MAX_TRANSITION_MULTIPLIER / MIN_TRANSITION_MULTIPLIER, normalized);
        return HydraulicConnectionHeadBlockEntity.clampExpectedLengthApproachMultiplier(multiplier);
    }

    private void setPowered(boolean powered) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.getBlockState();
        if (!state.hasProperty(HydraulicRegulatorBlock.POWERED)
                || state.getValue(HydraulicRegulatorBlock.POWERED) == powered) {
            return;
        }

        this.level.setBlock(this.worldPosition, state.setValue(HydraulicRegulatorBlock.POWERED, powered),
                Block.UPDATE_CLIENTS);
    }

    private record RegulatorCommand(int signal, double transitionMultiplier) {
        private static RegulatorCommand max(RegulatorCommand first, RegulatorCommand second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            if (second.signal > first.signal) {
                return second;
            }
            if (second.signal == first.signal && second.transitionMultiplier > first.transitionMultiplier) {
                return second;
            }
            return first;
        }
    }
}
