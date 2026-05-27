package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

public class HydraulicHingeHeadBlock extends HydraulicConnectionHeadBlock {
    public static final EnumProperty<Direction.Axis> HINGE_AXIS = EnumProperty.create("hinge_axis", Direction.Axis.class);
    private static final MapCodec<HydraulicConnectionHeadBlock> CODEC = simpleCodec(HydraulicHingeHeadBlock::new);

    public HydraulicHingeHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(HINGE_AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<HydraulicConnectionHeadBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE_AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HINGE_AXIS, chooseHingeAxis(context, facing));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction clickedFace) {
        Direction.Axis rotationAxis = clickedFace.getAxis();
        Direction facing = state.getValue(FACING);
        Direction.Axis hingeAxis = getHingeAxis(state);
        Direction newFacing = facing.getAxis() == rotationAxis ? facing : facing.getClockWise(rotationAxis);
        Direction.Axis newHingeAxis = rotateAxis(hingeAxis, rotationAxis);
        if (!isValidHingeAxis(newFacing, newHingeAxis)) {
            newHingeAxis = defaultHingeAxis(newFacing);
        }

        return state
                .setValue(FACING, newFacing)
                .setValue(HINGE_AXIS, newHingeAxis);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction newFacing = rotation.rotate(state.getValue(FACING));
        Direction.Axis newHingeAxis = rotateAxis(getHingeAxis(state), rotation);
        if (!isValidHingeAxis(newFacing, newHingeAxis)) {
            newHingeAxis = defaultHingeAxis(newFacing);
        }
        return state
                .setValue(FACING, newFacing)
                .setValue(HINGE_AXIS, newHingeAxis);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction newFacing = mirror.mirror(state.getValue(FACING));
        Direction.Axis hingeAxis = getHingeAxis(state);
        if (!isValidHingeAxis(newFacing, hingeAxis)) {
            hingeAxis = defaultHingeAxis(newFacing);
        }
        return state
                .setValue(FACING, newFacing)
                .setValue(HINGE_AXIS, hingeAxis);
    }

    @Override
    public Class<HydraulicConnectionHeadBlockEntity> getBlockEntityClass() {
        return HydraulicConnectionHeadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HydraulicConnectionHeadBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HYDRAULIC_CONNECTION_HEAD.get();
    }

    public static boolean isHinged(BlockState state) {
        return state.getBlock() instanceof HydraulicHingeHeadBlock;
    }

    public static Direction.Axis getHingeAxis(BlockState state) {
        if (state.hasProperty(HINGE_AXIS)) {
            Direction.Axis hingeAxis = state.getValue(HINGE_AXIS);
            if (state.hasProperty(FACING) && !isValidHingeAxis(state.getValue(FACING), hingeAxis)) {
                return defaultHingeAxis(state.getValue(FACING));
            }
            return hingeAxis;
        }
        return Direction.Axis.X;
    }

    public static Direction.Axis defaultHingeAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static boolean isValidHingeAxis(Direction facing, Direction.Axis hingeAxis) {
        return facing.getAxis() != hingeAxis;
    }

    private static Direction.Axis chooseHingeAxis(BlockPlaceContext context, Direction facing) {
        Vec3 look = context.getPlayer() != null ? context.getPlayer().getLookAngle() : Vec3.ZERO;
        Direction.Axis first = defaultHingeAxis(facing);
        Direction.Axis second = alternateHingeAxis(facing, first);
        double firstAmount = componentAbs(look, first);
        double secondAmount = componentAbs(look, second);
        return firstAmount >= secondAmount ? first : second;
    }

    private static Direction.Axis alternateHingeAxis(Direction facing, Direction.Axis first) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != facing.getAxis() && axis != first) {
                return axis;
            }
        }
        return first;
    }

    private static double componentAbs(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> Math.abs(vector.x);
            case Y -> Math.abs(vector.y);
            case Z -> Math.abs(vector.z);
        };
    }

    private static Direction.Axis rotateAxis(Direction.Axis axis, Direction.Axis rotationAxis) {
        if (axis == rotationAxis) {
            return axis;
        }
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)
                .getClockWise(rotationAxis)
                .getAxis();
    }

    private static Direction.Axis rotateAxis(Direction.Axis axis, Rotation rotation) {
        return rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)).getAxis();
    }
}
