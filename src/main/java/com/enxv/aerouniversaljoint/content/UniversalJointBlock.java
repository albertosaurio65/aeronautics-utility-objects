package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModItems;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class UniversalJointBlock extends DirectionalKineticBlock implements IBE<UniversalJointBlockEntity>, BlockSubLevelAssemblyListener, IWrenchable {
    private static final VoxelShape[] ANDESITE_SHAPES = DirectionalConnectionShapes.makeShapes(0.0D, 10.0D, 0.0D, 6.25D, 0.25D, 1.25D);
    private static final VoxelShape[] BRASS_SHAPES = DirectionalConnectionShapes.makeShapes(0.0D, 10.75D, 0.5D, 5.75D, 0.0D, 2.25D);

    private final JointVariant variant;

    public UniversalJointBlock(Properties properties, JointVariant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    public JointVariant getVariant() {
        return this.variant;
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
        Direction preferredFacing = getPreferredFacing(context);
        Direction facing = preferredFacing != null
                && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())
                ? preferredFacing.getOpposite()
                : context.getClickedFace();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(heldItem.getItem() instanceof UniversalJointRodItem rodItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            if (player != null && player.isShiftKeyDown()) {
                PendingRodSelections.clearClient(player);
            } else if (player != null && PendingRodSelections.readClientPending(player).isEmpty()) {
                BlockEntity selectionBlockEntity = SubLevelReferenceHelper.resolveBlockEntityFast(level, pos, null);
                BlockPos selectionPos = selectionBlockEntity != null ? selectionBlockEntity.getBlockPos() : pos.immutable();
                UUID selectionSubLevelId = selectionBlockEntity != null
                        ? SubLevelReferenceHelper.findContainingSubLevelId(selectionBlockEntity)
                        : SubLevelReferenceHelper.findContainingSubLevelId(level, pos);
                PendingRodSelections.writeClient(player, new JointBindingData.Selection(
                        level.dimension().location(),
                        selectionPos,
                        selectionSubLevelId), rodItem.isBrass());
            } else if (player != null) {
                PendingRodSelections.clearClient(player);
            }
        }

        return onBlockEntityUseItemOn(level, pos, be -> be.handleHeldRodItem(player, hand, rodItem.isBrass()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        UniversalJointBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null || !blockEntity.hasLink()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            blockEntity.detachLink();
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.unlinked"), true);
            return InteractionResult.SUCCESS;
        }

        if (!UniversalJointBlockEntity.isSpeedRatioFeatureEnabled()) {
            return InteractionResult.SUCCESS;
        }

        player.openMenu(blockEntity, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return WrenchRemovalHelper.removeWithDrops(state, context, () -> {
            UniversalJointBlockEntity blockEntity = getBlockEntity(context.getLevel(), context.getClickedPos());
            if (blockEntity != null) {
                blockEntity.detachLinkForBlockRemoval();
            }
        });
    }

    @Override
    public void beforeMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                           BlockState state, BlockPos oldPos, BlockPos newPos) {
        UniversalJointBlockEntity moving = getBlockEntity(oldLevel, oldPos);
        SubLevelMoveHandler.beforeMove(oldLevel, oldPos, moving);
    }

    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        UniversalJointBlockEntity moved = getBlockEntity(newLevel, newPos);
        SubLevelMoveHandler.afterMove(newLevel, oldPos, newPos, moved);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<UniversalJointBlockEntity> getBlockEntityClass() {
        return UniversalJointBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends UniversalJointBlockEntity> getBlockEntityType() {
        return ModBlockEntities.UNIVERSAL_JOINT.get();
    }

    private VoxelShape getFacingShape(BlockState state) {
        VoxelShape[] shapes = this.variant == JointVariant.BRASS ? BRASS_SHAPES : ANDESITE_SHAPES;
        return shapes[state.getValue(FACING).ordinal()];
    }

}
