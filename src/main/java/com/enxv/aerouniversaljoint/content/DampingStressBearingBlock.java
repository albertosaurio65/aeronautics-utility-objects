package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.IRotate;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;

public class DampingStressBearingBlock extends SwivelBearingBlock {
    private static final IRotate REAR_OUTPUT = new IRotate() {
        @Override
        public Direction.Axis getRotationAxis(BlockState state) {
            return state.getValue(FACING).getAxis();
        }

        @Override
        public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
            return face == state.getValue(FACING).getOpposite();
        }
    };

    public DampingStressBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            this.withBlockEntityDo(level, pos, base -> {
                if (!(base instanceof DampingStressBearingBlockEntity be)) {
                    return;
                }

                player.openMenu(be, pos);
            });
            return ItemInteractionResult.SUCCESS;
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        this.withBlockEntityDo(level, pos, be -> be.assembleNextTick = true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return WrenchRemovalHelper.removeWithDrops(state, context, () -> {
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DampingStressBearingBlockEntity be) {
                be.disassemble();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<SwivelBearingBlockEntity> getBlockEntityClass() {
        return (Class<SwivelBearingBlockEntity>) (Class<?>) DampingStressBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DampingStressBearingBlockEntity> getBlockEntityType() {
        return ModBlockEntities.DAMPING_STRESS_BEARING.get();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return REAR_OUTPUT;
    }
}
