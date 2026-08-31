package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HydraulicConnectionHeadBlock extends DirectionalBlock implements IBE<HydraulicConnectionHeadBlockEntity>, BlockSubLevelAssemblyListener, IWrenchable {
    private static final MapCodec<HydraulicConnectionHeadBlock> CODEC = simpleCodec(HydraulicConnectionHeadBlock::new);
    private static final VoxelShape[] BRASS_SHAPES = DirectionalConnectionShapes.makeShapes(0.0D, 10.75D, 0.5D, 5.75D, 0.0D, 2.25D);

    public HydraulicConnectionHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<HydraulicConnectionHeadBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getFacingShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getFacingShape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getFacingShape(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Let Create's wrench handle wrench interactions before opening the menu.
        if (heldItem.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(heldItem.getItem() instanceof HydraulicRodItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return onBlockEntityUseItemOn(level, pos, be -> be.handleHeldHydraulicRodItem(player, hand));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        HydraulicConnectionHeadBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            if (!blockEntity.hasLink()) {
                return InteractionResult.PASS;
            }
            blockEntity.detachLink();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aeronautics_utility_objects.unlinked"), true);
            return InteractionResult.SUCCESS;
        }

        if (blockEntity.isBrassHingeHead()) {
            HydraulicConnectionHeadMenu.open(player, blockEntity, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return WrenchRemovalHelper.removeWithDrops(state, context, () -> {
            HydraulicConnectionHeadBlockEntity blockEntity = getBlockEntity(context.getLevel(), context.getClickedPos());
            if (blockEntity != null) {
                blockEntity.detachLinkForBlockRemoval();
            }
        });
    }

    @Override
    public void beforeMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                           BlockState state, BlockPos oldPos, BlockPos newPos) {
        HydraulicConnectionHeadBlockEntity moving = getBlockEntity(oldLevel, oldPos);
        SubLevelMoveHandler.beforeMove(oldLevel, oldPos, moving);
    }

    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        HydraulicConnectionHeadBlockEntity moved = getBlockEntity(newLevel, newPos);
        SubLevelMoveHandler.afterMove(newLevel, oldPos, newPos, moved);
    }

    @Override
    public Class<HydraulicConnectionHeadBlockEntity> getBlockEntityClass() {
        return HydraulicConnectionHeadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HydraulicConnectionHeadBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HYDRAULIC_CONNECTION_HEAD.get();
    }

    private VoxelShape getFacingShape(BlockState state) {
        return BRASS_SHAPES[state.getValue(FACING).ordinal()];
    }

}
