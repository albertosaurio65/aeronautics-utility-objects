package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModItems;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import com.mojang.serialization.MapCodec;
import java.util.UUID;
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
import org.jetbrains.annotations.Nullable;

public class HydraulicConnectionHeadBlock extends DirectionalBlock implements IBE<HydraulicConnectionHeadBlockEntity>, BlockSubLevelAssemblyListener, IWrenchable {
    private static final MapCodec<HydraulicConnectionHeadBlock> CODEC = simpleCodec(HydraulicConnectionHeadBlock::new);
    private static final VoxelShape[] BRASS_SHAPES = makeShapes(0.0D, 10.75D, 0.5D, 5.75D, 0.0D, 2.25D);

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
        if (!(heldItem.getItem() instanceof HydraulicRodItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return onBlockEntityUseItemOn(level, pos, be -> be.handleHeldHydraulicRodItem(player, hand));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        HydraulicConnectionHeadBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null || !blockEntity.hasLink()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            blockEntity.detachLink();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aeronautics_utility_objects.unlinked"), true);
            return InteractionResult.SUCCESS;
        }

        player.openMenu(blockEntity, pos);
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
        RecentMoveRemapper.prepare(oldLevel, oldPos, moving != null ? moving.getContainingSubLevelId() : findSubLevelId(oldLevel, oldPos));
        if (moving != null) {
            moving.preserveLinkForSubLevelMove();
        }
    }

    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel oldLevel, net.minecraft.server.level.ServerLevel newLevel,
                          BlockState state, BlockPos oldPos, BlockPos newPos) {
        RecentMoveRemapper.record(newLevel, oldPos, newPos, findSubLevelId(newLevel, newPos));

        HydraulicConnectionHeadBlockEntity moved = getBlockEntity(newLevel, newPos);
        if (moved == null) {
            return;
        }
        moved.remapLinkedReferenceAfterSubLevelMove();

        HydraulicConnectionHeadBlockEntity linked = moved.getLoadedLinkedConnectionHead();
        if (linked != null) {
            linked.updateReferenceTo(newPos, findSubLevelId(newLevel, newPos));
        }
    }

    @Nullable
    private UUID findSubLevelId(Level level, BlockPos pos) {
        return SubLevelReferenceHelper.findContainingSubLevelId(level, pos);
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

    private static VoxelShape[] makeShapes(double shaftMinY, double shaftMaxY, double bodyMinY, double bodyMaxY,
                                           double flangeMinY, double flangeMaxY) {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length];
        for (Direction facing : Direction.values()) {
            shapes[facing.ordinal()] = Shapes.or(
                    rotateBox(facing, 5.5D, shaftMinY, 5.5D, 10.5D, shaftMaxY, 10.5D),
                    rotateBox(facing, 4.25D, bodyMinY, 4.25D, 11.75D, bodyMaxY, 11.75D),
                    rotateBox(facing, 4.0D, flangeMinY, 4.0D, 12.0D, flangeMaxY, 12.0D)
            );
        }
        return shapes;
    }

    private static VoxelShape rotateBox(Direction facing, double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double[][] corners = {
                {minX, minY, minZ},
                {minX, minY, maxZ},
                {minX, maxY, minZ},
                {minX, maxY, maxZ},
                {maxX, minY, minZ},
                {maxX, minY, maxZ},
                {maxX, maxY, minZ},
                {maxX, maxY, maxZ}
        };

        double rotatedMinX = 16.0D;
        double rotatedMinY = 16.0D;
        double rotatedMinZ = 16.0D;
        double rotatedMaxX = 0.0D;
        double rotatedMaxY = 0.0D;
        double rotatedMaxZ = 0.0D;

        for (double[] corner : corners) {
            double[] rotated = rotatePoint(facing, corner[0], corner[1], corner[2]);
            rotatedMinX = Math.min(rotatedMinX, rotated[0]);
            rotatedMinY = Math.min(rotatedMinY, rotated[1]);
            rotatedMinZ = Math.min(rotatedMinZ, rotated[2]);
            rotatedMaxX = Math.max(rotatedMaxX, rotated[0]);
            rotatedMaxY = Math.max(rotatedMaxY, rotated[1]);
            rotatedMaxZ = Math.max(rotatedMaxZ, rotated[2]);
        }

        return Block.box(rotatedMinX, rotatedMinY, rotatedMinZ, rotatedMaxX, rotatedMaxY, rotatedMaxZ);
    }

    private static double[] rotatePoint(Direction facing, double x, double y, double z) {
        double centeredX = x - 8.0D;
        double centeredY = y - 8.0D;
        double centeredZ = z - 8.0D;

        double rotatedX = centeredX;
        double rotatedY = centeredY;
        double rotatedZ = centeredZ;

        switch (facing) {
            case DOWN -> {
                rotatedY = -centeredY;
                rotatedZ = -centeredZ;
            }
            case NORTH -> {
                rotatedY = centeredZ;
                rotatedZ = -centeredY;
            }
            case SOUTH -> {
                rotatedY = -centeredZ;
                rotatedZ = centeredY;
            }
            case EAST -> {
                rotatedX = centeredY;
                rotatedY = -centeredX;
                rotatedZ = centeredZ;
            }
            case WEST -> {
                rotatedX = -centeredY;
                rotatedY = centeredX;
                rotatedZ = centeredZ;
            }
            case UP -> {
            }
        }

        return new double[] {rotatedX + 8.0D, rotatedY + 8.0D, rotatedZ + 8.0D};
    }
}
