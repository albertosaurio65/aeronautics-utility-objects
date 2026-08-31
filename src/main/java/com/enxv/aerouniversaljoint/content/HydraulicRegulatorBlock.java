package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class HydraulicRegulatorBlock extends DirectionalAxisKineticBlock implements IBE<HydraulicRegulatorBlockEntity>, IWrenchable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public HydraulicRegulatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext context) {
        Direction headFacing = this.getFacingTowardsAdjacentHead(context);
        if (headFacing != null) {
            return headFacing;
        }

        Direction preferredFacing = this.getPreferredFacing(context);
        return preferredFacing != null
                && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())
                ? preferredFacing.getOpposite()
                : context.getClickedFace();
    }

    private Direction getFacingTowardsAdjacentHead(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            BlockState state = context.getLevel().getBlockState(pos.relative(direction));
            if (state.getBlock() instanceof HydraulicConnectionHeadBlock
                    && state.hasProperty(HydraulicConnectionHeadBlock.FACING)
                    && state.getValue(HydraulicConnectionHeadBlock.FACING) == direction) {
                return direction;
            }
        }

        return null;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getStressAxis(state);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == this.getRotationAxis(state);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return WrenchRemovalHelper.removeWithDrops(state, context, () -> {
        });
    }

    public static Direction.Axis getStressAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean axisAlongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        return switch (facing.getAxis()) {
            case X -> axisAlongFirst ? Direction.Axis.Y : Direction.Axis.Z;
            case Y -> axisAlongFirst ? Direction.Axis.X : Direction.Axis.Z;
            case Z -> axisAlongFirst ? Direction.Axis.X : Direction.Axis.Y;
        };
    }

    @Override
    public Class<HydraulicRegulatorBlockEntity> getBlockEntityClass() {
        return HydraulicRegulatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HydraulicRegulatorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HYDRAULIC_REGULATOR.get();
    }
}
