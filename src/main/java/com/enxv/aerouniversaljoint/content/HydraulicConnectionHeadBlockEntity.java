package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.ModItems;
import com.enxv.aerouniversaljoint.network.SyncHydraulicSelectionPayload;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class HydraulicConnectionHeadBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, MenuProvider {
    private static final String TAG_LINKED_POS = "LinkedPos";
    private static final String TAG_LINKED_SUB_LEVEL = "LinkedSubLevel";
    private static final String TAG_STRETCH_RESISTANCE = "StretchResistance";
    private static final String TAG_FREE_MODE = "FreeMode";
    private static final String TAG_EXPECTED_LENGTH_TENTHS = "ExpectedLengthTenths";
    private static final String TAG_REDSTONE_MIN_LENGTH_TENTHS = "RedstoneMinLengthTenths";
    private static final String TAG_REDSTONE_MAX_LENGTH_TENTHS = "RedstoneMaxLengthTenths";
    private static final String TAG_EFFECTIVE_EXPECTED_LENGTH = "EffectiveExpectedLength";
    private static final String TAG_RETURN_FORCE = "ReturnForce";
    private static final String TAG_EFFECTIVE_RETURN_FORCE = "EffectiveReturnForce";
    private static final String TAG_CREATIVE_LINK = "CreativeLink";
    private static final String TAG_RENDER_OWNER = "RenderOwner";
    private static final String TAG_LINK_STRAIN_EFFECT = "LinkStrainEffect";
    private static final String TAG_LINK_STRAINED = "LinkStrained";
    private static final String TAG_HINGE_SUB_LEVEL = "HingeSubLevel";
    private static final String TAG_HINGE_LINK_POS = "HingeLinkPos";
    private static final double MIN_LINK_LENGTH = 2.0D;
    private static final double MAX_LINK_LENGTH = 15.0D;
    private static final double BREAK_LINK_LENGTH = 17.0D;
    private static final double LINK_STRAIN_LENGTH = 16.5D;
    private static final int MIN_EXPECTED_LENGTH_TENTHS = (int) Math.round(MIN_LINK_LENGTH * 10.0D);
    private static final int MAX_EXPECTED_LENGTH_TENTHS = (int) Math.round(MAX_LINK_LENGTH * 10.0D);
    private static final int MAX_STRETCH_RESISTANCE_VALUE = 65536;
    private static final int MAX_RETURN_FORCE_VALUE = 4096;
    private static final int DEFAULT_STRETCH_RESISTANCE = 256;
    private static final int DEFAULT_RETURN_FORCE = 1024;
    private static final double LENGTH_LIMIT_STIFFNESS = 48.0D;
    private static final double LENGTH_LIMIT_CURVE = 4.0D;
    private static final double STRETCH_RESISTANCE_MOTOR_DAMPING_PER_UNIT = 0.5D;
    private static final double STRETCH_RESISTANCE_MOTOR_MAX_FORCE_PER_UNIT = 12.0D;
    private static final double RETURN_FORCE_PER_UNIT = 2.0D;
    private static final double RETURN_FORCE_CURVE = 0.5D;
    private static final double EXPECTED_LENGTH_APPROACH_RATE = 3.0D;
    private static final double RETURN_FORCE_APPROACH_RATE = 1024.0D;
    private static final double MIN_EXPECTED_LENGTH_APPROACH_MULTIPLIER = 0.5D;
    private static final double MAX_EXPECTED_LENGTH_APPROACH_MULTIPLIER = 4.0D;
    private static final double MAX_LENGTH_LIMIT_IMPULSE = 256.0D;
    private static final double MAX_EXPECTED_RETURN_IMPULSE = 256.0D;
    private static final double MAX_COMBINED_LENGTH_CONTROL_IMPULSE = 512.0D;
    private static final double CREATIVE_LENGTH_SERVO_STIFFNESS = 16384.0D;
    private static final double CREATIVE_LENGTH_SERVO_DAMPING = 1024.0D;
    private static final double CREATIVE_LENGTH_APPROACH_RATE = 12.0D;
    private static final double CREATIVE_LENGTH_MAX_FORCE = 1.0E12D;
    private static final double CREATIVE_LENGTH_WAKE_EPSILON = 0.01D;
    private static final int CREATIVE_LENGTH_WAKE_TICKS = 80;
    private static final double MIN_PHYSICS_DISTANCE = 1.0E-4D;
    private static final double HINGE_FREE_SPIN_DAMPING = 1.0E-3D;
    private static final double ALIGNMENT_DOT_THRESHOLD = Math.cos(Math.toRadians(35.0D));
    private static final double HINGED_AXIS_ALIGNMENT_THRESHOLD = Math.cos(Math.toRadians(85.0D));
    private static final double QUARTER_TURN_RADIANS = Math.PI * 0.5D;
    private static final double MIN_ROLL_REFERENCE_LENGTH_SQUARED = 1.0E-8D;
    private static final int SERVER_LINK_VALIDATION_INTERVAL = 10;
    private static final int LINK_STRAIN_RECOVERY_TICKS = 12;
    private static final long SUB_LEVEL_MOVE_PRESERVE_WINDOW_MS = 5_000L;
    private static final Set<ConstraintJointAxis> LOCKED_AXES = EnumSet.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Y,
            ConstraintJointAxis.ANGULAR_Z);
    @Nullable
    private BlockPos linkedPos;
    @Nullable
    private UUID linkedSubLevelId;
    private boolean hasRenderOwnerPreference = false;
    private boolean renderOwner = false;
    @Nullable
    private GenericConstraintHandle constraintHandle;
    @Nullable
    private UUID hingeSubLevelId;
    @Nullable
    private BlockPos hingeLinkPos;
    @Nullable
    private RotaryConstraintHandle hingeConstraintHandle;
    @Nullable
    private Direction.Axis hingeConstraintAxis;
    private int serverValidationCountdown = SERVER_LINK_VALIDATION_INTERVAL;
    private int stretchResistance = DEFAULT_STRETCH_RESISTANCE;
    private boolean freeMode = false;
    private int expectedLengthTenths = MIN_EXPECTED_LENGTH_TENTHS;
    private int redstoneMinLengthTenths = MIN_EXPECTED_LENGTH_TENTHS;
    private int redstoneMaxLengthTenths = MAX_EXPECTED_LENGTH_TENTHS;
    private double effectiveExpectedLengthBlocks = MIN_LINK_LENGTH;
    private boolean expectedLengthTransitionPending = true;
    private double expectedLengthApproachMultiplier = 1.0D;
    private int returnForce = DEFAULT_RETURN_FORCE;
    private double effectiveReturnForce = 0.0D;
    private boolean creativeLink;
    private final ForceTotal lengthForceTotal = new ForceTotal();
    private final ForceTotal partnerLengthForceTotal = new ForceTotal();
    private boolean detachingForBlockRemoval;
    private long preserveLinkForSubLevelMoveUntil;
    private long lastCreativeServoWakeGameTime = Long.MIN_VALUE;
    private long creativeServoWakeUntilGameTime = Long.MIN_VALUE;
    private double snappedConnectionRollOffset = Double.NaN;
    private int linkStrainEffectDelay;
    private float linkStrainEffect;
    private boolean linkWasStrained;
    @Nullable
    private HydraulicConnectionHeadBlockEntity cachedLinkedHead;
    @Nullable
    private BlockPos cachedLinkedHeadPos;
    @Nullable
    private UUID cachedLinkedHeadSubLevelId;

    public HydraulicConnectionHeadBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HYDRAULIC_CONNECTION_HEAD.get(), pos, state);
    }

    public HydraulicConnectionHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        if (this.level == null) {
            return;
        }

        super.tick();
        this.tickLinkStrainEffect();

        if (this.level.isClientSide || this.linkedPos == null) {
            if (!this.level.isClientSide && this.hingeSubLevelId != null) {
                this.disassembleHingeAssembly();
            }
            return;
        }

        if (this.isHingedHead()) {
            this.ensureHingeAssemblyForLink();
        } else {
            this.disassembleHingeAssembly();
        }

        if (--this.serverValidationCountdown <= 0) {
            this.serverValidationCountdown = SERVER_LINK_VALIDATION_INTERVAL;
            this.validateLinkState(true);
        }
    }

    @Override
    public void lazyTick() {
        if (this.level == null) {
            return;
        }

        super.lazyTick();

        if (this.level.isClientSide || this.linkedPos == null) {
            return;
        }

        this.validateLinkState(false);
    }

    @Override
    public void remove() {
        boolean preservingForSubLevelMove = this.isPreservingLinkForSubLevelMove();
        this.preserveLinkForSubLevelMoveUntil = 0L;
        if (this.level != null && !this.level.isClientSide && this.linkedPos != null) {
            if (preservingForSubLevelMove) {
                this.clearLinkInternal(false);
            } else if (this.detachingForBlockRemoval) {
                this.clearLinkInternal(true);
            } else {
                this.detachLink(true);
            }
        }
        if (this.level != null && !this.level.isClientSide && this.linkedPos == null && this.hingeSubLevelId != null) {
            this.disassembleHingeAssembly();
        }
        super.remove();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (this.linkedPos != null) {
            tag.put(TAG_LINKED_POS, NbtUtils.writeBlockPos(this.linkedPos));
        }
        if (this.linkedSubLevelId != null) {
            tag.putUUID(TAG_LINKED_SUB_LEVEL, this.linkedSubLevelId);
        }
        if (this.linkedPos != null && this.hasRenderOwnerPreference) {
            tag.putBoolean(TAG_RENDER_OWNER, this.renderOwner);
        }
        tag.putInt(TAG_STRETCH_RESISTANCE, this.stretchResistance);
        tag.putBoolean(TAG_FREE_MODE, this.freeMode);
        tag.putInt(TAG_EXPECTED_LENGTH_TENTHS, this.expectedLengthTenths);
        tag.putInt(TAG_REDSTONE_MIN_LENGTH_TENTHS, this.redstoneMinLengthTenths);
        tag.putInt(TAG_REDSTONE_MAX_LENGTH_TENTHS, this.redstoneMaxLengthTenths);
        tag.putDouble(TAG_EFFECTIVE_EXPECTED_LENGTH, this.effectiveExpectedLengthBlocks);
        tag.putInt(TAG_RETURN_FORCE, this.returnForce);
        tag.putDouble(TAG_EFFECTIVE_RETURN_FORCE, this.effectiveReturnForce);
        if (this.creativeLink) {
            tag.putBoolean(TAG_CREATIVE_LINK, true);
        }
        if (this.linkStrainEffect != 0.0F) {
            tag.putFloat(TAG_LINK_STRAIN_EFFECT, this.linkStrainEffect);
        }
        if (this.linkWasStrained) {
            tag.putBoolean(TAG_LINK_STRAINED, true);
        }
        if (this.hingeSubLevelId != null && this.hingeLinkPos != null) {
            tag.putUUID(TAG_HINGE_SUB_LEVEL, this.hingeSubLevelId);
            tag.put(TAG_HINGE_LINK_POS, NbtUtils.writeBlockPos(this.hingeLinkPos));
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        BlockPos oldLinkedPos = this.linkedPos;
        UUID oldLinkedSubLevelId = this.linkedSubLevelId;
        UUID oldHingeSubLevelId = this.hingeSubLevelId;
        BlockPos oldHingeLinkPos = this.hingeLinkPos;
        boolean oldHasRenderOwnerPreference = this.hasRenderOwnerPreference;
        boolean oldRenderOwner = this.renderOwner;
        this.linkedPos = tag.contains(TAG_LINKED_POS, Tag.TAG_INT_ARRAY)
                ? NbtUtils.readBlockPos(tag, TAG_LINKED_POS).orElse(null)
                : null;
        this.linkedSubLevelId = tag.hasUUID(TAG_LINKED_SUB_LEVEL) ? tag.getUUID(TAG_LINKED_SUB_LEVEL) : null;
        this.hasRenderOwnerPreference = this.linkedPos != null && tag.contains(TAG_RENDER_OWNER, Tag.TAG_BYTE);
        this.renderOwner = this.hasRenderOwnerPreference && tag.getBoolean(TAG_RENDER_OWNER);
        this.stretchResistance = tag.contains(TAG_STRETCH_RESISTANCE, Tag.TAG_INT)
                ? clampStretchResistance(tag.getInt(TAG_STRETCH_RESISTANCE))
                : DEFAULT_STRETCH_RESISTANCE;
        this.freeMode = tag.contains(TAG_FREE_MODE, Tag.TAG_BYTE) && tag.getBoolean(TAG_FREE_MODE);
        this.expectedLengthTenths = tag.contains(TAG_EXPECTED_LENGTH_TENTHS, Tag.TAG_INT)
                ? clampExpectedLengthTenths(tag.getInt(TAG_EXPECTED_LENGTH_TENTHS))
                : MIN_EXPECTED_LENGTH_TENTHS;
        this.redstoneMinLengthTenths = tag.contains(TAG_REDSTONE_MIN_LENGTH_TENTHS, Tag.TAG_INT)
                ? clampExpectedLengthTenths(tag.getInt(TAG_REDSTONE_MIN_LENGTH_TENTHS))
                : MIN_EXPECTED_LENGTH_TENTHS;
        this.redstoneMaxLengthTenths = tag.contains(TAG_REDSTONE_MAX_LENGTH_TENTHS, Tag.TAG_INT)
                ? clampExpectedLengthTenths(tag.getInt(TAG_REDSTONE_MAX_LENGTH_TENTHS))
                : MAX_EXPECTED_LENGTH_TENTHS;
        this.normalizeRedstoneLengthRange();
        boolean hasEffectiveExpectedLength = tag.contains(TAG_EFFECTIVE_EXPECTED_LENGTH, Tag.TAG_DOUBLE);
        this.effectiveExpectedLengthBlocks = hasEffectiveExpectedLength
                ? clampEffectiveExpectedLength(tag.getDouble(TAG_EFFECTIVE_EXPECTED_LENGTH))
                : this.expectedLengthTenths / 10.0D;
        this.expectedLengthTransitionPending = !this.freeMode && this.linkedPos != null && !hasEffectiveExpectedLength;
        this.returnForce = tag.contains(TAG_RETURN_FORCE, Tag.TAG_INT)
                ? clampReturnForce(tag.getInt(TAG_RETURN_FORCE))
                : DEFAULT_RETURN_FORCE;
        this.effectiveReturnForce = tag.contains(TAG_EFFECTIVE_RETURN_FORCE, Tag.TAG_DOUBLE)
                ? clampEffectiveReturnForce(tag.getDouble(TAG_EFFECTIVE_RETURN_FORCE))
                : 0.0D;
        this.creativeLink = tag.contains(TAG_CREATIVE_LINK, Tag.TAG_BYTE) && tag.getBoolean(TAG_CREATIVE_LINK);
        this.linkStrainEffect = tag.contains(TAG_LINK_STRAIN_EFFECT, Tag.TAG_FLOAT)
                ? tag.getFloat(TAG_LINK_STRAIN_EFFECT)
                : 0.0F;
        this.linkWasStrained = tag.contains(TAG_LINK_STRAINED, Tag.TAG_BYTE)
                ? tag.getBoolean(TAG_LINK_STRAINED)
                : this.linkStrainEffect > 0.0F;
        this.hingeSubLevelId = tag.hasUUID(TAG_HINGE_SUB_LEVEL) ? tag.getUUID(TAG_HINGE_SUB_LEVEL) : null;
        this.hingeLinkPos = tag.contains(TAG_HINGE_LINK_POS, Tag.TAG_INT_ARRAY)
                ? NbtUtils.readBlockPos(tag, TAG_HINGE_LINK_POS).orElse(null)
                : null;
        this.linkStrainEffectDelay = this.linkWasStrained || this.linkStrainEffect >= 0.0F
                ? 0
                : LINK_STRAIN_RECOVERY_TICKS;
        super.read(tag, registries, clientPacket);

        if (!Objects.equals(oldLinkedPos, this.linkedPos)
                || !Objects.equals(oldLinkedSubLevelId, this.linkedSubLevelId)
                || oldHasRenderOwnerPreference != this.hasRenderOwnerPreference
                || oldRenderOwner != this.renderOwner
                || !Objects.equals(oldHingeSubLevelId, this.hingeSubLevelId)
                || !Objects.equals(oldHingeLinkPos, this.hingeLinkPos)) {
            this.clearCachedLinkedHead();
            this.invalidateRenderBoundingBox();
        }
    }

    public ItemInteractionResult handleHeldHydraulicRodItem(Player player, InteractionHand hand) {
        if (this.level == null) {
            return ItemInteractionResult.SUCCESS;
        }

        if (this.level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        Optional<JointBindingData.Selection> selection = PendingHydraulicSelections.read(player);
        if (player.isShiftKeyDown() && selection.isPresent()) {
            PendingHydraulicSelections.clear(player);
            syncSelectionToClient(player, null);
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.selection_cleared"), true);
            return ItemInteractionResult.SUCCESS;
        }

        if (selection.isEmpty()) {
            boolean creativeRod = player.getItemInHand(hand).getItem() instanceof HydraulicRodItem rodItem && rodItem.isCreative();
            JointBindingData.Selection newSelection = new JointBindingData.Selection(
                    this.level.dimension().location(),
                    this.worldPosition,
                    this.getContainingSubLevelId(),
                    creativeRod);
            PendingHydraulicSelections.write(player, newSelection);
            syncSelectionToClient(player, newSelection);
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.endpoint_selected"), true);
            return ItemInteractionResult.SUCCESS;
        }

        JointBindingData.Selection storedSelection = selection.get();
        JointBindingData.Selection stored = this.resolveReferenceFast(storedSelection.pos(), storedSelection.subLevelId()) != null
                ? storedSelection
                : RecentMoveRemapper.remap(this.level, storedSelection).orElse(storedSelection);
        if (!stored.equals(storedSelection)) {
            PendingHydraulicSelections.write(player, stored);
            syncSelectionToClient(player, stored);
        }
        boolean creativeRod = player.getItemInHand(hand).getItem() instanceof HydraulicRodItem rodItem && rodItem.isCreative();
        LinkResult result = this.linkToSelection(stored, creativeRod || stored.creativeHydraulic());
        if (result.clearsSelection()) {
            PendingHydraulicSelections.clear(player);
            syncSelectionToClient(player, null);
            if (result.consumesRod() && !player.hasInfiniteMaterials()) {
                ItemStack heldStack = player.getItemInHand(hand);
                heldStack.shrink(1);
                player.setItemInHand(hand, heldStack.isEmpty() ? ItemStack.EMPTY : heldStack);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        player.displayClientMessage(result.message(), true);
        return ItemInteractionResult.SUCCESS;
    }

    public LinkResult linkToSelection(JointBindingData.Selection selection) {
        return this.linkToSelection(selection, selection.creativeHydraulic());
    }

    public LinkResult linkToSelection(JointBindingData.Selection selection, boolean creativeLink) {
        if (this.level == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (!this.level.dimension().location().equals(selection.dimensionId())) {
            return LinkResult.WRONG_DIMENSION;
        }

        HydraulicConnectionHeadBlockEntity other = this.resolveReference(selection.pos(), selection.subLevelId());
        if (other == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }

        return this.createMutualLink(other, creativeLink);
    }

    public LinkResult createMutualLink(HydraulicConnectionHeadBlockEntity other) {
        return this.createMutualLink(other, false);
    }

    public LinkResult createMutualLink(HydraulicConnectionHeadBlockEntity other, boolean creativeLink) {
        if (other == this) {
            return LinkResult.SELF;
        }

        if (this.level == null || other.level == null || !this.level.dimension().equals(other.level.dimension())) {
            return LinkResult.WRONG_DIMENSION;
        }

        if (!this.isAllowedLinkPair(other)) {
            return LinkResult.SAME_STRUCTURE;
        }

        if (!this.isWithinLinkRange(other)) {
            return LinkResult.TOO_FAR;
        }

        if (!this.isAlignedForLink(other)) {
            return LinkResult.NOT_ALIGNED;
        }

        if (this.references(other) && other.references(this)) {
            return LinkResult.ALREADY_LINKED;
        }

        this.clearLinkInternal(true);
        other.clearLinkInternal(true);

        if (!this.ensureHingeAssemblyForLink() || !other.ensureHingeAssemblyForLink()) {
            this.disassembleHingeAssembly();
            other.disassembleHingeAssembly();
            return LinkResult.TARGET_UNAVAILABLE;
        }

        this.applyLinkReference(other.worldPosition, other.getContainingSubLevelId(), false, true, creativeLink);
        other.applyLinkReference(this.worldPosition, this.getContainingSubLevelId(), true, true, creativeLink);
        this.cacheLinkedHead(other);
        other.cacheLinkedHead(this);
        this.initializeExpectedLength(other);
        return LinkResult.SUCCESS;
    }

    public void detachLink() {
        this.detachLink(true);
    }

    public void detachLinkWithoutDrop() {
        this.detachLink(false);
    }

    public void detachLinkForBlockRemoval() {
        this.detachingForBlockRemoval = true;
        this.detachLink(true);
    }

    public void preserveLinkForSubLevelMove() {
        this.preserveLinkForSubLevelMoveUntil = System.currentTimeMillis() + SUB_LEVEL_MOVE_PRESERVE_WINDOW_MS;
    }

    public boolean hasLink() {
        return this.linkedPos != null;
    }

    @Nullable
    public BlockPos getLinkedPos() {
        return this.linkedPos;
    }

    @Nullable
    public UUID getLinkedSubLevelId() {
        return this.linkedSubLevelId;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HydraulicConnectionHeadMenu(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Nullable
    public HydraulicConnectionHeadBlockEntity getLoadedLinkedConnectionHead() {
        return this.resolveLinkedConnectionHead();
    }

    public boolean isHingedHead() {
        return HydraulicHingeHeadBlock.isHinged(this.getBlockState());
    }

    public boolean isCreativeLink() {
        return this.creativeLink;
    }

    @Nullable
    public UUID getHingeSubLevelId() {
        return this.hingeSubLevelId;
    }

    @Nullable
    public BlockPos getHingeLinkPos() {
        return this.hingeLinkPos;
    }

    public void updateReferenceTo(BlockPos newPos, @Nullable UUID newSubLevelId) {
        if (this.linkedPos == null) {
            return;
        }
        this.applyLinkReference(newPos, newSubLevelId);
    }

    public void remapLinkedReferenceAfterSubLevelMove() {
        if (this.level == null || this.linkedPos == null) {
            return;
        }

        Optional<JointBindingData.Selection> remapped = RecentMoveRemapper.remap(this.level, this.linkedSelection());
        if (remapped.isPresent() && !this.matchesSelection(remapped.get())) {
            this.applyLinkReference(remapped.get().pos(), remapped.get().subLevelId());
        }
    }

    public boolean matchesSelection(JointBindingData.Selection selection) {
        return this.level != null
                && this.level.dimension().location().equals(selection.dimensionId())
                && this.worldPosition.equals(selection.pos())
                && Objects.equals(this.getContainingSubLevelId(), selection.subLevelId());
    }

    public LinkResult previewLinkToSelection(JointBindingData.Selection selection) {
        if (this.level == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (!this.level.dimension().location().equals(selection.dimensionId())) {
            return LinkResult.WRONG_DIMENSION;
        }

        HydraulicConnectionHeadBlockEntity other = this.resolveReference(selection.pos(), selection.subLevelId());
        if (other == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (other == this) {
            return LinkResult.SELF;
        }
        if (other.level == null || !this.level.dimension().equals(other.level.dimension())) {
            return LinkResult.WRONG_DIMENSION;
        }
        if (!this.isAllowedLinkPair(other)) {
            return LinkResult.SAME_STRUCTURE;
        }
        if (!this.isWithinLinkRange(other)) {
            return LinkResult.TOO_FAR;
        }
        if (!this.isAlignedForLink(other)) {
            return LinkResult.NOT_ALIGNED;
        }
        if (this.references(other) && other.references(this)) {
            return LinkResult.ALREADY_LINKED;
        }
        return LinkResult.SUCCESS;
    }

    public boolean references(HydraulicConnectionHeadBlockEntity other) {
        return this.linkedPos != null
                && this.linkedPos.equals(other.worldPosition)
                && Objects.equals(this.linkedSubLevelId, other.getContainingSubLevelId());
    }

    public boolean shouldRenderConnectionTo(HydraulicConnectionHeadBlockEntity other) {
        if (this.hasRenderOwnerPreference && other.hasRenderOwnerPreference
                && this.renderOwner != other.renderOwner) {
            return this.renderOwner;
        }

        if (this.hasRenderOwnerPreference != other.hasRenderOwnerPreference) {
            return this.hasRenderOwnerPreference ? this.renderOwner : !other.renderOwner;
        }

        return this.isCoordinateRenderOwnerComparedTo(other);
    }

    @Nullable
    public UUID getContainingSubLevelId() {
        if (this.level == null) {
            return null;
        }
        return SubLevelReferenceHelper.findContainingSubLevelId(this.level, this.worldPosition);
    }

    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (this.level == null || this.linkedPos == null) {
            return null;
        }

        List<SubLevel> dependencies = new ArrayList<>(3);
        this.addDependency(dependencies, this.getHingeSubLevel());

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other != null) {
            this.addDependency(dependencies, other.getRodConstraintSubLevel());
            this.addDependency(dependencies, other.getHingeSubLevel());
        } else if (this.linkedSubLevelId != null) {
            this.addDependency(dependencies, SubLevelContainer.getContainer(this.level).getSubLevel(this.linkedSubLevelId));
        }

        return dependencies.isEmpty() ? null : dependencies;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (this.level == null || this.level.isClientSide || this.linkedPos == null) {
            return;
        }

        if (!Objects.equals(this.getContainingSubLevelId(), subLevel.getUniqueId())) {
            return;
        }

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other == null || !other.references(this)) {
            return;
        }

        if (!this.ensureHingeAssemblyForLink()) {
            this.removeConstraintHandle();
            return;
        }

        if (!this.shouldControlConstraint(other)) {
            this.removeConstraintHandle();
            return;
        }

        if (!other.ensureHingeAssemblyForLink()) {
            this.removeConstraintHandle();
            return;
        }

        ServerSubLevel otherContainingSubLevel = other.getContainingServerSubLevel();
        ServerSubLevel ownSubLevel = this.getRodConstraintServerSubLevel(subLevel);
        ServerSubLevel otherSubLevel = other.getRodConstraintServerSubLevel(otherContainingSubLevel);
        if (ownSubLevel == null) {
            this.removeConstraintHandle();
            return;
        }

        RigidBodyHandle ownHandle = ownSubLevel == subLevel ? handle : RigidBodyHandle.of(ownSubLevel);
        if (ownHandle == null || !ownHandle.isValid()) {
            this.removeConstraintHandle();
            return;
        }

        Vector3d ownLocal = this.getRodConstraintLocalAnchor();
        Vector3d otherLocal = other.getRodConstraintLocalAnchor();
        Vector3d ownWorld = this.toWorldPosition(ownSubLevel, ownLocal);
        Vector3d otherWorld = this.toWorldPosition(otherSubLevel, otherLocal);

        Vector3d worldDirection = this.getLengthControlDirection(ownSubLevel, ownWorld, otherWorld);
        if (worldDirection.lengthSquared() < 1.0E-8D) {
            this.removeConstraintHandle();
            return;
        }

        Vector3d ownLocalAxis = this.getLocalFacingVector();
        Vector3d otherLocalAxis = other.getLocalFacingVector().negate(new Vector3d());
        if (ownLocalAxis.lengthSquared() < 1.0E-8D || otherLocalAxis.lengthSquared() < 1.0E-8D) {
            this.removeConstraintHandle();
            return;
        }

        Quaterniond ownOrientation = this.frameOrientation(ownLocalAxis);
        Quaterniond otherBaseOrientation = other.frameOrientation(otherLocalAxis);
        if (!Double.isFinite(this.snappedConnectionRollOffset)) {
            this.snappedConnectionRollOffset = this.snapConnectionRollOffset(
                    ownSubLevel, otherSubLevel, ownOrientation, otherBaseOrientation);
        }
        Quaterniond otherOrientation = new Quaterniond(otherBaseOrientation)
                .mul(new Quaterniond().rotationY(this.snappedConnectionRollOffset));
        if (this.constraintHandle == null || !this.constraintHandle.isValid()) {
            this.removeConstraintHandle();
            this.constraintHandle = SubLevelPhysicsSystem.require(this.level).getPipeline().addConstraint(ownSubLevel, otherSubLevel,
                    new GenericConstraintConfiguration(ownLocal, otherLocal, ownOrientation, otherOrientation, LOCKED_AXES));
            if (this.constraintHandle == null) {
                return;
            }
        } else {
            this.constraintHandle.setFrame1(ownLocal, ownOrientation);
            this.constraintHandle.setFrame2(otherLocal, otherOrientation);
        }
        if (!this.creativeLink) {
            this.refreshRodDampingMotor();
        }

        this.applyLengthLimits(other, ownSubLevel, ownHandle, ownLocal, ownWorld,
                otherSubLevel, otherLocal, otherWorld, worldDirection, timeStep);
    }

    private void refreshRodDampingMotor() {
        if (this.constraintHandle == null || !this.constraintHandle.isValid()) {
            return;
        }

        if (this.creativeLink) {
            double target = Double.isFinite(this.effectiveExpectedLengthBlocks)
                    ? this.effectiveExpectedLengthBlocks
                    : this.expectedLengthTenths / 10.0D;
            this.constraintHandle.setMotor(ConstraintJointAxis.LINEAR_Y, target,
                    CREATIVE_LENGTH_SERVO_STIFFNESS, CREATIVE_LENGTH_SERVO_DAMPING, true,
                    CREATIVE_LENGTH_MAX_FORCE);
            return;
        }

        double damping = Math.max(0.0D, this.stretchResistance * STRETCH_RESISTANCE_MOTOR_DAMPING_PER_UNIT);
        double maxForce = Math.max(0.0D, this.stretchResistance * STRETCH_RESISTANCE_MOTOR_MAX_FORCE_PER_UNIT);
        this.constraintHandle.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0D, 0.0D, damping, true, maxForce);
    }

    private Vector3d getRodConstraintLocalAnchor() {
        return this.isHingedHead() && this.hingeLinkPos != null
                ? this.localCenterOf(this.hingeLinkPos)
                : this.localCenterOf(this.worldPosition);
    }

    @Nullable
    private ServerSubLevel getRodConstraintServerSubLevel(@Nullable ServerSubLevel containingSubLevel) {
        if (this.isHingedHead()) {
            ServerSubLevel hingeSubLevel = this.getHingeServerSubLevel();
            if (hingeSubLevel != null) {
                return hingeSubLevel;
            }
        }
        return containingSubLevel != null ? containingSubLevel : this.getContainingServerSubLevel();
    }

    @Nullable
    private SubLevel getRodConstraintSubLevel() {
        if (this.isHingedHead()) {
            SubLevel hingeSubLevel = this.getHingeSubLevel();
            if (hingeSubLevel != null) {
                return hingeSubLevel;
            }
        }
        return this.getContainingSubLevel();
    }

    private Vector3d getLengthControlDirection(ServerSubLevel ownSubLevel, Vector3d ownWorld, Vector3d otherWorld) {
        Vector3d connection = otherWorld.sub(ownWorld, new Vector3d());
        if (normalizeIfUsable(connection)) {
            return connection;
        }

        return this.getWorldFacingVector(ownSubLevel);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        AABB base = super.createRenderBoundingBox();
        if (this.linkedPos == null) {
            return base;
        }

        if (this.getContainingSubLevelId() != null || this.linkedSubLevelId != null) {
            double radius = BREAK_LINK_LENGTH + 1.0D;
            double diameter = radius * 2.0D;
            return AABB.ofSize(Vec3.atCenterOf(this.worldPosition), diameter, diameter, diameter);
        }

        return base.minmax(AABB.ofSize(Vec3.atCenterOf(this.linkedPos), 1.0D, 1.0D, 1.0D)).inflate(1.0D);
    }

    private boolean isAllowedLinkPair(HydraulicConnectionHeadBlockEntity other) {
        UUID ownSubLevelId = this.getContainingSubLevelId();
        UUID otherSubLevelId = other.getContainingSubLevelId();
        if (ownSubLevelId == null && otherSubLevelId == null) {
            return false;
        }
        return !Objects.equals(ownSubLevelId, otherSubLevelId);
    }

    private boolean isWithinLinkRange(HydraulicConnectionHeadBlockEntity other) {
        return this.distanceSquaredTo(other) <= BREAK_LINK_LENGTH * BREAK_LINK_LENGTH;
    }

    private boolean isAlignedForLink(HydraulicConnectionHeadBlockEntity other) {
        SubLevel ownSubLevel = this.getContainingSubLevel();
        SubLevel otherSubLevel = other.getContainingSubLevel();
        Vector3d ownWorld = this.toWorldPosition(ownSubLevel, this.localCenterOf(this.worldPosition));
        Vector3d otherWorld = this.toWorldPosition(otherSubLevel, this.localCenterOf(other.worldPosition));
        Vector3d connection = otherWorld.sub(ownWorld, new Vector3d());
        if (connection.lengthSquared() < MIN_PHYSICS_DISTANCE * MIN_PHYSICS_DISTANCE) {
            return false;
        }

        Vector3d direction = connection.normalize();
        Vector3d ownFacing = this.getWorldFacingVector(ownSubLevel);
        Vector3d otherFacing = other.getWorldFacingVector(otherSubLevel);
        if (this.isHingedHead() || other.isHingedHead()) {
            return this.isHingeEndpointAbleToFace(direction, ownSubLevel)
                    && other.isHingeEndpointAbleToFace(direction.negate(new Vector3d()), otherSubLevel);
        }

        return ownFacing.dot(direction) >= ALIGNMENT_DOT_THRESHOLD
                && otherFacing.dot(direction) <= -ALIGNMENT_DOT_THRESHOLD
                && ownFacing.dot(otherFacing) <= -ALIGNMENT_DOT_THRESHOLD;
    }

    private boolean isHingeEndpointAbleToFace(Vector3d targetDirection, @Nullable SubLevel subLevel) {
        if (!this.isHingedHead()) {
            Vector3d facing = this.getWorldFacingVector(subLevel);
            return facing.dot(targetDirection) >= ALIGNMENT_DOT_THRESHOLD;
        }

        Vector3d axis = this.getWorldHingeAxis(subLevel);
        if (axis.lengthSquared() < 1.0E-8D) {
            return false;
        }
        Vector3d target = new Vector3d(targetDirection);
        if (target.lengthSquared() < 1.0E-8D) {
            return false;
        }
        axis.normalize();
        target.normalize();

        double alongAxis = Math.abs(target.dot(axis));
        if (alongAxis > HINGED_AXIS_ALIGNMENT_THRESHOLD) {
            return false;
        }

        return true;
    }

    @Nullable
    private HydraulicConnectionHeadBlockEntity resolveLinkedConnectionHead() {
        if (this.linkedPos == null) {
            this.clearCachedLinkedHead();
            return null;
        }

        HydraulicConnectionHeadBlockEntity cached = this.cachedLinkedHead;
        if (this.isCachedLinkedHeadValid(cached)) {
            return cached;
        }

        HydraulicConnectionHeadBlockEntity resolved = this.resolveReference(this.linkedPos, this.linkedSubLevelId);
        this.cacheLinkedHead(resolved);
        return resolved;
    }

    private JointBindingData.Selection linkedSelection() {
        return new JointBindingData.Selection(this.level.dimension().location(), Objects.requireNonNull(this.linkedPos),
                this.linkedSubLevelId, this.creativeLink);
    }

    private void validateLinkState(boolean remapReference) {
        if (this.level == null || this.level.isClientSide || this.linkedPos == null) {
            return;
        }

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other == null && remapReference) {
            Optional<JointBindingData.Selection> remapped = RecentMoveRemapper.remap(this.level, this.linkedSelection());
            if (remapped.isPresent() && !this.matchesSelection(remapped.get())) {
                this.applyLinkReference(remapped.get().pos(), remapped.get().subLevelId());
                other = this.resolveLinkedConnectionHead();
            }
        }

        if (other == null || !other.references(this)) {
            if (!remapReference) {
                return;
            }
            this.clearLinkInternal(false);
            return;
        }

        if (!this.isAllowedLinkPair(other)) {
            this.detachLink();
        }
    }

    @Nullable
    private HydraulicConnectionHeadBlockEntity resolveReference(BlockPos pos, @Nullable UUID subLevelId) {
        if (this.level == null) {
            return null;
        }

        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntity(this.level, pos, subLevelId);
        return blockEntity instanceof HydraulicConnectionHeadBlockEntity head ? head : null;
    }

    @Nullable
    private HydraulicConnectionHeadBlockEntity resolveReferenceFast(BlockPos pos, @Nullable UUID subLevelId) {
        if (this.level == null) {
            return null;
        }

        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntityFast(this.level, pos, subLevelId);
        return blockEntity instanceof HydraulicConnectionHeadBlockEntity head ? head : null;
    }

    private boolean isCachedLinkedHeadValid(@Nullable HydraulicConnectionHeadBlockEntity head) {
        return head != null
                && this.level != null
                && !head.isRemoved()
                && head.getLevel() == this.level
                && this.linkedPos != null
                && this.linkedPos.equals(this.cachedLinkedHeadPos)
                && this.linkedPos.equals(head.worldPosition)
                && Objects.equals(this.linkedSubLevelId, this.cachedLinkedHeadSubLevelId)
                && Objects.equals(this.linkedSubLevelId, head.getContainingSubLevelId());
    }

    private void cacheLinkedHead(@Nullable HydraulicConnectionHeadBlockEntity head) {
        this.cachedLinkedHead = head;
        this.cachedLinkedHeadPos = this.linkedPos;
        this.cachedLinkedHeadSubLevelId = this.linkedSubLevelId;
    }

    private void clearCachedLinkedHead() {
        this.cachedLinkedHead = null;
        this.cachedLinkedHeadPos = null;
        this.cachedLinkedHeadSubLevelId = null;
    }

    private boolean shouldControlConstraint(HydraulicConnectionHeadBlockEntity other) {
        UUID ownSubLevelId = this.getContainingSubLevelId();
        UUID otherSubLevelId = other.getContainingSubLevelId();
        if (ownSubLevelId == null) {
            return false;
        }
        if (otherSubLevelId == null) {
            return true;
        }
        if (ownSubLevelId.equals(otherSubLevelId)) {
            return false;
        }
        return SubLevelReferenceHelper.compareNullableUuids(ownSubLevelId, otherSubLevelId) < 0;
    }

    @Nullable
    private ServerSubLevel getContainingServerSubLevel() {
        if (this.level == null) {
            return null;
        }

        SubLevel subLevel = this.getContainingSubLevel();
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    @Nullable
    private SubLevel getContainingSubLevel() {
        return this.level == null ? null : Sable.HELPER.getContaining(this);
    }

    private boolean ensureHingeAssemblyForLink() {
        if (!this.isHingedHead()) {
            this.disassembleHingeAssembly();
            return true;
        }
        if (this.level == null || this.level.isClientSide) {
            return this.hingeSubLevelId != null && this.hingeLinkPos != null;
        }

        ServerSubLevel hingeSubLevel = this.getHingeServerSubLevel();
        if (hingeSubLevel == null || this.hingeLinkPos == null) {
            this.removeHingeConstraintHandle();
            this.hingeSubLevelId = null;
            this.hingeLinkPos = null;
            this.hingeConstraintAxis = null;
            hingeSubLevel = this.createHingeLinkSubLevel();
            if (hingeSubLevel == null) {
                return false;
            }
        }

        return this.attachOrRefreshHingeConstraint(hingeSubLevel);
    }

    @Nullable
    private ServerSubLevel createHingeLinkSubLevel() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        Pose3d pose = new Pose3d();
        pose.position().set(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D);

        ServerSubLevel hingeSubLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        ServerLevelPlot plot = hingeSubLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModBlocks.HYDRAULIC_HINGE_LINK.get().defaultBlockState(), 3);

        BlockPos plotCenter = plot.getCenterBlock();
        Vector3d position = JOMLConversion.atLowerCornerOf(this.worldPosition);
        org.joml.Vector3dc centerOfMass = hingeSubLevel.getMassTracker().getCenterOfMass();
        if (centerOfMass != null) {
            position.add(
                    centerOfMass.x() - plotCenter.getX(),
                    centerOfMass.y() - plotCenter.getY(),
                    centerOfMass.z() - plotCenter.getZ());
        } else {
            hingeSubLevel.logicalPose().rotationPoint().set(
                    plotCenter.getX() + 0.5D,
                    plotCenter.getY() + 0.5D,
                    plotCenter.getZ() + 0.5D);
        }
        hingeSubLevel.logicalPose().position().set(position);

        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        PhysicsPipeline pipeline = physicsSystem.getPipeline();
        SubLevel containing = this.getContainingSubLevel();
        if (containing != null) {
            hingeSubLevel.logicalPose().orientation().set(containing.logicalPose().orientation());
            SubLevelAssemblyHelper.kickFromContainingSubLevel(serverLevel, physicsSystem, pipeline, hingeSubLevel, containing);
        }
        pipeline.teleport(hingeSubLevel, hingeSubLevel.logicalPose().position(), hingeSubLevel.logicalPose().orientation());
        hingeSubLevel.updateLastPose();

        this.hingeSubLevelId = hingeSubLevel.getUniqueId();
        this.hingeLinkPos = plotCenter;
        this.invalidateRenderBoundingBox();
        this.setChanged();
        this.sendData();
        return hingeSubLevel;
    }

    private boolean attachOrRefreshHingeConstraint(ServerSubLevel hingeSubLevel) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.hingeLinkPos == null) {
            return false;
        }

        Direction.Axis currentAxis = HydraulicHingeHeadBlock.getHingeAxis(this.getBlockState());
        if (this.hingeConstraintHandle != null
                && this.hingeConstraintHandle.isValid()
                && this.hingeConstraintAxis == currentAxis) {
            this.refreshHingeMotor();
            return true;
        }

        this.removeHingeConstraintHandle();
        Vector3d parentAnchor = this.localCenterOf(this.worldPosition);
        Vector3d linkAnchor = this.localCenterOf(this.hingeLinkPos);
        Vector3d parentAxis = axisVector(currentAxis);
        Vector3d linkAxis = axisVector(currentAxis);
        this.hingeConstraintHandle = SubLevelContainer.getContainer(serverLevel)
                .physicsSystem()
                .getPipeline()
                .addConstraint(this.getContainingServerSubLevel(), hingeSubLevel,
                        new RotaryConstraintConfiguration(parentAnchor, linkAnchor, parentAxis, linkAxis));
        if (this.hingeConstraintHandle == null) {
            return false;
        }

        this.hingeConstraintHandle.setContactsEnabled(false);
        this.refreshHingeMotor();
        this.hingeConstraintAxis = currentAxis;
        return true;
    }

    private void refreshHingeMotor() {
        if (this.hingeConstraintHandle == null || !this.hingeConstraintHandle.isValid()) {
            return;
        }

        this.hingeConstraintHandle.setMotor(
                RotaryConstraintHandle.DEFAULT_AXIS,
                0.0D,
                0.0D,
                HINGE_FREE_SPIN_DAMPING,
                false,
                0.0D);
    }

    @Nullable
    private SubLevel getHingeSubLevel() {
        if (this.level == null || this.hingeSubLevelId == null) {
            return null;
        }
        return SubLevelContainer.getContainer(this.level).getSubLevel(this.hingeSubLevelId);
    }

    @Nullable
    private ServerSubLevel getHingeServerSubLevel() {
        SubLevel subLevel = this.getHingeSubLevel();
        return subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() ? serverSubLevel : null;
    }

    private void disassembleHingeAssembly() {
        this.removeHingeConstraintHandle();
        if (this.level != null && !this.level.isClientSide && this.hingeSubLevelId != null) {
            SubLevel subLevel = SubLevelContainer.getContainer(this.level).getSubLevel(this.hingeSubLevelId);
            if (subLevel != null && !subLevel.isRemoved()) {
                SubLevelContainer.getContainer(this.level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }

        if (this.hingeSubLevelId != null || this.hingeLinkPos != null) {
            this.hingeSubLevelId = null;
            this.hingeLinkPos = null;
            this.hingeConstraintAxis = null;
            this.invalidateRenderBoundingBox();
            this.setChanged();
            this.sendData();
        }
    }

    private void removeHingeConstraintHandle() {
        if (this.hingeConstraintHandle != null) {
            if (this.hingeConstraintHandle.isValid()) {
                this.hingeConstraintHandle.remove();
            }
            this.hingeConstraintHandle = null;
        }
        this.hingeConstraintAxis = null;
    }

    private void addDependency(List<SubLevel> dependencies, @Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || dependencies.contains(subLevel)) {
            return;
        }
        dependencies.add(subLevel);
    }

    private void applyLinkReference(BlockPos pos, @Nullable UUID subLevelId) {
        this.applyLinkReference(pos, subLevelId, this.renderOwner, this.hasRenderOwnerPreference, this.creativeLink);
    }

    private void applyLinkReference(BlockPos pos, @Nullable UUID subLevelId, boolean renderOwner, boolean hasRenderOwnerPreference) {
        this.applyLinkReference(pos, subLevelId, renderOwner, hasRenderOwnerPreference, this.creativeLink);
    }

    private void applyLinkReference(BlockPos pos, @Nullable UUID subLevelId, boolean renderOwner,
                                    boolean hasRenderOwnerPreference, boolean creativeLink) {
        if (this.linkedPos != null
                && this.linkedPos.equals(pos)
                && Objects.equals(this.linkedSubLevelId, subLevelId)
                && this.renderOwner == renderOwner
                && this.hasRenderOwnerPreference == hasRenderOwnerPreference
                && this.creativeLink == creativeLink) {
            return;
        }

        this.linkedPos = pos.immutable();
        this.linkedSubLevelId = subLevelId;
        this.renderOwner = renderOwner;
        this.hasRenderOwnerPreference = hasRenderOwnerPreference;
        this.creativeLink = creativeLink;
        this.clearCachedLinkedHead();
        this.snappedConnectionRollOffset = Double.NaN;
        this.invalidateRenderBoundingBox();
        this.setChanged();
        this.sendData();
    }

    private boolean isCoordinateRenderOwnerComparedTo(HydraulicConnectionHeadBlockEntity other) {
        BlockPos ownPos = this.getBlockPos();
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

        return SubLevelReferenceHelper.compareNullableUuids(this.getContainingSubLevelId(), other.getContainingSubLevelId()) <= 0;
    }

    private void initializeExpectedLength(HydraulicConnectionHeadBlockEntity other) {
        double currentLength = this.currentRodControlDistanceTo(other);
        if (!Double.isFinite(currentLength)) {
            currentLength = other.currentRodControlDistanceTo(this);
        }
        if (!Double.isFinite(currentLength)) {
            currentLength = Math.sqrt(this.distanceSquaredTo(other));
        }

        int currentLengthTenths = clampExpectedLengthTenths((int) Math.round(currentLength * 10.0D));
        this.setExpectedLengthTenths(currentLengthTenths);
        other.setExpectedLengthTenths(currentLengthTenths);
        double currentLengthBlocks = currentLengthTenths / 10.0D;
        this.effectiveExpectedLengthBlocks = currentLengthBlocks;
        other.effectiveExpectedLengthBlocks = currentLengthBlocks;
        this.expectedLengthTransitionPending = false;
        other.expectedLengthTransitionPending = false;
        this.effectiveReturnForce = 0.0D;
        other.effectiveReturnForce = 0.0D;
        this.redstoneMinLengthTenths = MIN_EXPECTED_LENGTH_TENTHS;
        this.redstoneMaxLengthTenths = MAX_EXPECTED_LENGTH_TENTHS;
        other.redstoneMinLengthTenths = MIN_EXPECTED_LENGTH_TENTHS;
        other.redstoneMaxLengthTenths = MAX_EXPECTED_LENGTH_TENTHS;
    }

    private void clearLinkInternal(boolean updateOther) {
        BlockPos oldPos = this.linkedPos;
        UUID oldSubLevelId = this.linkedSubLevelId;
        HydraulicConnectionHeadBlockEntity linkedOther = null;
        if (updateOther && this.level != null && oldPos != null) {
            HydraulicConnectionHeadBlockEntity other = this.resolveReference(oldPos, oldSubLevelId);
            if (other != null && other.references(this)) {
                linkedOther = other;
                linkedOther.removeConstraintHandle();
            }
        }

        this.removeConstraintHandle();
        this.disassembleHingeAssembly();
        this.linkedPos = null;
        this.linkedSubLevelId = null;
        this.creativeLink = false;
        this.clearCachedLinkedHead();
        this.hasRenderOwnerPreference = false;
        this.renderOwner = false;
        this.expectedLengthApproachMultiplier = 1.0D;
        this.snappedConnectionRollOffset = Double.NaN;
        this.invalidateRenderBoundingBox();
        this.setChanged();
        this.sendData();

        if (linkedOther != null) {
            linkedOther.clearLinkInternal(false);
        }
    }

    private boolean isPreservingLinkForSubLevelMove() {
        long preserveUntil = this.preserveLinkForSubLevelMoveUntil;
        if (preserveUntil <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() <= preserveUntil) {
            return true;
        }
        this.preserveLinkForSubLevelMoveUntil = 0L;
        return false;
    }

    private void removeConstraintHandle() {
        if (this.constraintHandle != null) {
            if (this.constraintHandle.isValid()) {
                this.constraintHandle.remove();
            }
            this.constraintHandle = null;
        }
    }

    private void detachLink(boolean dropRod) {
        if (this.linkedPos == null) {
            return;
        }

        if (dropRod) {
            this.dropLinkRod();
        }
        this.clearLinkInternal(true);
    }

    private void dropLinkRod() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        Block.popResource(this.level, this.worldPosition, new ItemStack(
                this.creativeLink ? ModItems.CREATIVE_HYDRAULIC_ROD.get() : ModItems.HYDRAULIC_ROD.get()));
    }

    private Vector3d localCenterOf(BlockPos pos) {
        return JOMLConversion.atCenterOf(pos);
    }

    private Vector3d toWorldPosition(@Nullable SubLevel subLevel, Vector3d localPosition) {
        return subLevel == null ? localPosition : subLevel.logicalPose().transformPosition(localPosition, new Vector3d());
    }

    private Vector3d getWorldFacingVector(@Nullable SubLevel subLevel) {
        Vector3d vector = this.getLocalFacingVector();
        if (subLevel != null) {
            subLevel.logicalPose().transformNormal(vector, vector);
        }
        if (vector.lengthSquared() > 1.0E-8D) {
            vector.normalize();
        }
        return vector;
    }

    private Vector3d getLocalFacingVector() {
        Direction facing = this.getBlockState().getValue(HydraulicConnectionHeadBlock.FACING);
        return new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private Vector3d getWorldHingeAxis(@Nullable SubLevel subLevel) {
        Vector3d vector = axisVector(HydraulicHingeHeadBlock.getHingeAxis(this.getBlockState()));
        if (subLevel != null) {
            subLevel.logicalPose().transformNormal(vector, vector);
        }
        if (vector.lengthSquared() > 1.0E-8D) {
            vector.normalize();
        }
        return vector;
    }

    private static Vector3d axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    private Quaterniond frameOrientation(Vector3d axis) {
        Vector3d normalized = new Vector3d(axis);
        if (normalized.lengthSquared() > 1.0E-8D) {
            normalized.normalize();
        }
        return new Quaterniond().rotationTo(new Vector3d(0.0D, 1.0D, 0.0D), normalized);
    }

    private double snapConnectionRollOffset(@Nullable SubLevel ownSubLevel, @Nullable SubLevel otherSubLevel,
                                            Quaterniond ownLocalFrame, Quaterniond otherLocalFrame) {
        Quaterniond ownWorldFrame = this.toWorldFrame(ownSubLevel, ownLocalFrame);
        Quaterniond otherWorldFrame = this.toWorldFrame(otherSubLevel, otherLocalFrame);
        Vector3d axis = ownWorldFrame.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        if (axis.lengthSquared() < MIN_ROLL_REFERENCE_LENGTH_SQUARED) {
            return 0.0D;
        }
        axis.normalize();

        Vector3d ownReference = ownWorldFrame.transform(new Vector3d(1.0D, 0.0D, 0.0D));
        Vector3d otherReference = otherWorldFrame.transform(new Vector3d(1.0D, 0.0D, 0.0D));
        if (!projectOntoPlane(ownReference, axis) || !projectOntoPlane(otherReference, axis)) {
            return 0.0D;
        }

        double angle = Math.atan2(new Vector3d(otherReference).cross(ownReference).dot(axis), otherReference.dot(ownReference));
        return Math.round(angle / QUARTER_TURN_RADIANS) * QUARTER_TURN_RADIANS;
    }

    private Quaterniond toWorldFrame(@Nullable SubLevel subLevel, Quaterniond localFrame) {
        Quaterniond worldFrame = subLevel != null
                ? new Quaterniond(subLevel.logicalPose().orientation())
                : new Quaterniond();
        return worldFrame.mul(localFrame);
    }

    private static boolean projectOntoPlane(Vector3d vector, Vector3d normal) {
        vector.sub(new Vector3d(normal).mul(vector.dot(normal)));
        if (vector.lengthSquared() < MIN_ROLL_REFERENCE_LENGTH_SQUARED) {
            return false;
        }
        vector.normalize();
        return true;
    }

    private static boolean normalizeIfUsable(Vector3d vector) {
        if (vector.lengthSquared() < MIN_ROLL_REFERENCE_LENGTH_SQUARED) {
            return false;
        }
        vector.normalize();
        return true;
    }

    private double distanceSquaredTo(HydraulicConnectionHeadBlockEntity other) {
        Vec3 ownCenter = Vec3.atCenterOf(this.worldPosition);
        Vec3 otherCenter = Vec3.atCenterOf(other.worldPosition);
        return Sable.HELPER.distanceSquaredWithSubLevels(this.level, ownCenter, otherCenter);
    }

    private double currentRodControlDistanceTo(HydraulicConnectionHeadBlockEntity other) {
        ServerSubLevel ownSubLevel = this.getRodConstraintServerSubLevel(this.getContainingServerSubLevel());
        if (ownSubLevel == null) {
            return Double.NaN;
        }

        ServerSubLevel otherSubLevel = other.getRodConstraintServerSubLevel(other.getContainingServerSubLevel());
        Vector3d ownWorld = this.toWorldPosition(ownSubLevel, this.getRodConstraintLocalAnchor());
        Vector3d otherWorld = other.toWorldPosition(otherSubLevel, other.getRodConstraintLocalAnchor());
        Vector3d connection = otherWorld.sub(ownWorld, new Vector3d());
        double actualDistance = connection.length();
        if (!Double.isFinite(actualDistance) || actualDistance < MIN_PHYSICS_DISTANCE) {
            return Double.NaN;
        }

        Vector3d direction = this.getLengthControlDirection(ownSubLevel, ownWorld, otherWorld);
        if (!normalizeIfUsable(direction)) {
            return actualDistance;
        }

        double projectedDistance = connection.dot(direction);
        return Double.isFinite(projectedDistance) && projectedDistance > MIN_PHYSICS_DISTANCE
                ? projectedDistance
                : actualDistance;
    }

    public void setStretchResistance(int value) {
        this.applySettingsLocal(value, this.freeMode, this.expectedLengthTenths, this.returnForce, true);
    }

    public void setFreeMode(boolean value) {
        this.applySettingsLocal(this.stretchResistance, value, this.expectedLengthTenths, this.returnForce, true);
    }

    public void setExpectedLengthTenths(int value) {
        this.applySettingsLocal(this.stretchResistance, this.freeMode, value, this.returnForce, true);
    }

    public void setRedstoneLengthRangeAndMirror(int minLengthTenths, int maxLengthTenths) {
        this.applyRedstoneLengthRangeLocal(minLengthTenths, maxLengthTenths, true);

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other != null && other.references(this)) {
            other.applyRedstoneLengthRangeLocal(minLengthTenths, maxLengthTenths, true);
        }
    }

    public void setReturnForce(int value) {
        this.applySettingsLocal(this.stretchResistance, this.freeMode, this.expectedLengthTenths, value, true);
    }

    public int getStretchResistance() {
        return this.stretchResistance;
    }

    public boolean isFreeMode() {
        return this.freeMode;
    }

    public int getExpectedLengthTenths() {
        return this.expectedLengthTenths;
    }

    public int getRedstoneMinLengthTenths() {
        return this.redstoneMinLengthTenths;
    }

    public int getRedstoneMaxLengthTenths() {
        return this.redstoneMaxLengthTenths;
    }

    public int getReturnForce() {
        return this.returnForce;
    }

    public boolean isExpectedLengthControlledByRegulator() {
        if (this.level == null || !this.hasLink()) {
            return false;
        }
        if (this.hasControllingRegulator()) {
            return true;
        }

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        return other != null && other.references(this) && other.hasControllingRegulator();
    }

    private boolean hasControllingRegulator() {
        if (this.level == null) {
            return false;
        }

        BlockState state = this.getBlockState();
        if (!state.hasProperty(HydraulicConnectionHeadBlock.FACING)) {
            return false;
        }

        Direction facing = state.getValue(HydraulicConnectionHeadBlock.FACING);
        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntity(
                this.level,
                this.worldPosition.relative(facing.getOpposite()),
                this.getContainingSubLevelId());
        return blockEntity instanceof HydraulicRegulatorBlockEntity regulator && regulator.controlsHead(this);
    }

    public int getExpectedLengthTenthsForRedstoneSignal(int signal) {
        int clampedSignal = Mth.clamp(signal, 0, 15);
        return this.redstoneMinLengthTenths
                + Math.round((this.redstoneMaxLengthTenths - this.redstoneMinLengthTenths) * (clampedSignal / 15.0F));
    }

    public void applySyncedSettings(int stretchResistance, boolean freeMode, int expectedLengthTenths, int returnForce,
                                    int redstoneMinLengthTenths, int redstoneMaxLengthTenths) {
        this.applySettingsLocal(stretchResistance, freeMode, expectedLengthTenths, returnForce, false);
        this.applyRedstoneLengthRangeLocal(redstoneMinLengthTenths, redstoneMaxLengthTenths, false);
    }

    public void setSettingsAndMirror(int stretchResistance, boolean freeMode, int expectedLengthTenths, int returnForce) {
        this.setSettingsAndMirror(stretchResistance, freeMode, expectedLengthTenths, returnForce, 1.0D);
    }

    public void setSettingsAndMirror(int stretchResistance, boolean freeMode, int expectedLengthTenths, int returnForce,
                                     double expectedLengthApproachMultiplier) {
        this.applySettingsLocal(stretchResistance, freeMode, expectedLengthTenths, returnForce, true);
        this.applyExpectedLengthApproachMultiplierLocal(expectedLengthApproachMultiplier);

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other != null && other.references(this)) {
            other.applySettingsLocal(stretchResistance, freeMode, expectedLengthTenths, returnForce, true);
            other.applyExpectedLengthApproachMultiplierLocal(expectedLengthApproachMultiplier);
        }
    }

    public void setExpectedLengthApproachMultiplierAndMirror(double value) {
        this.applyExpectedLengthApproachMultiplierLocal(value);

        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other != null && other.references(this)) {
            other.applyExpectedLengthApproachMultiplierLocal(value);
        }
    }

    private void applySettingsLocal(int stretchResistance, boolean freeMode, int expectedLengthTenths, int returnForce, boolean notify) {
        boolean wasFreeMode = this.freeMode;
        boolean effectiveFreeMode = this.creativeLink ? false : freeMode;
        int clampedStretchResistance = this.creativeLink ? 0 : clampStretchResistance(stretchResistance);
        int clampedExpectedLengthTenths = clampExpectedLengthTenths(expectedLengthTenths);
        int clampedReturnForce = this.creativeLink ? 0 : clampReturnForce(returnForce);
        boolean creativeTargetChanged = this.creativeLink
                && this.linkedPos != null
                && this.expectedLengthTenths != clampedExpectedLengthTenths;
        boolean changed = this.stretchResistance != clampedStretchResistance
                || this.freeMode != effectiveFreeMode
                || this.expectedLengthTenths != clampedExpectedLengthTenths
                || this.returnForce != clampedReturnForce;

        this.stretchResistance = clampedStretchResistance;
        this.freeMode = effectiveFreeMode;
        this.expectedLengthTenths = clampedExpectedLengthTenths;
        this.returnForce = clampedReturnForce;
        if (wasFreeMode && !effectiveFreeMode) {
            this.expectedLengthTransitionPending = true;
            this.effectiveReturnForce = 0.0D;
        } else if (effectiveFreeMode) {
            this.effectiveReturnForce = 0.0D;
        }
        if (creativeTargetChanged) {
            this.scheduleCreativeServoWake();
        }
        if (notify && changed) {
            this.setChanged();
            this.sendData();
        }
    }

    private void applyExpectedLengthApproachMultiplierLocal(double value) {
        this.expectedLengthApproachMultiplier = clampExpectedLengthApproachMultiplier(value);
    }

    private void applyRedstoneLengthRangeLocal(int minLengthTenths, int maxLengthTenths, boolean notify) {
        int clampedMin = clampExpectedLengthTenths(minLengthTenths);
        int clampedMax = clampExpectedLengthTenths(maxLengthTenths);
        if (clampedMin > clampedMax) {
            int swapped = clampedMin;
            clampedMin = clampedMax;
            clampedMax = swapped;
        }

        boolean changed = this.redstoneMinLengthTenths != clampedMin
                || this.redstoneMaxLengthTenths != clampedMax;
        this.redstoneMinLengthTenths = clampedMin;
        this.redstoneMaxLengthTenths = clampedMax;
        if (notify && changed) {
            this.setChanged();
            this.sendData();
        }
    }

    private void normalizeRedstoneLengthRange() {
        this.applyRedstoneLengthRangeLocal(this.redstoneMinLengthTenths, this.redstoneMaxLengthTenths, false);
    }

    public static int clampStretchResistance(int value) {
        return Math.max(0, Math.min(MAX_STRETCH_RESISTANCE_VALUE, value));
    }

    public static int clampReturnForce(int value) {
        return Math.max(0, Math.min(MAX_RETURN_FORCE_VALUE, value));
    }

    public static int clampExpectedLengthTenths(int value) {
        return Math.max(MIN_EXPECTED_LENGTH_TENTHS, Math.min(MAX_EXPECTED_LENGTH_TENTHS, value));
    }

    public static double clampExpectedLengthApproachMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(MIN_EXPECTED_LENGTH_APPROACH_MULTIPLIER,
                Math.min(MAX_EXPECTED_LENGTH_APPROACH_MULTIPLIER, value));
    }

    public static int getMaxSettingValue() {
        return MAX_RETURN_FORCE_VALUE;
    }

    public static int getMaxStretchResistanceValue() {
        return MAX_STRETCH_RESISTANCE_VALUE;
    }

    public static int getMaxStretchResistanceLevel() {
        return 20;
    }

    public static int getStretchResistanceWarningLevel() {
        return 10;
    }

    public static int stretchResistanceFromLevel(int level) {
        int clampedLevel = Math.max(0, Math.min(getMaxStretchResistanceLevel(), level));
        if (clampedLevel <= 0) {
            return 0;
        }
        int warningLevel = getStretchResistanceWarningLevel();
        if (clampedLevel <= warningLevel) {
            double normalized = clampedLevel / (double) warningLevel;
            return clampStretchResistance((int) Math.round(1024.0D * normalized * normalized));
        }

        double normalized = (clampedLevel - warningLevel) / (double) (getMaxStretchResistanceLevel() - warningLevel);
        double multiplier = Math.pow(MAX_STRETCH_RESISTANCE_VALUE / 1024.0D, normalized);
        return clampStretchResistance((int) Math.round(1024.0D * multiplier));
    }

    public static int stretchResistanceToLevel(int resistance) {
        int clampedResistance = clampStretchResistance(resistance);
        if (clampedResistance <= 0) {
            return 0;
        }
        int warningLevel = getStretchResistanceWarningLevel();
        if (clampedResistance <= 1024) {
            double normalized = Math.sqrt(clampedResistance / 1024.0D);
            return Math.max(0, Math.min(warningLevel, (int) Math.round(normalized * warningLevel)));
        }

        double normalized = Math.log(clampedResistance / 1024.0D)
                / Math.log(MAX_STRETCH_RESISTANCE_VALUE / 1024.0D);
        double level = warningLevel + Math.max(0.0D, normalized) * (getMaxStretchResistanceLevel() - warningLevel);
        return Math.max(warningLevel, Math.min(getMaxStretchResistanceLevel(), (int) Math.round(level)));
    }

    public static int getMaxReturnForceValue() {
        return MAX_RETURN_FORCE_VALUE;
    }

    public static int getMaxReturnForceLevel() {
        return 20;
    }

    public static int returnForceFromLevel(int level) {
        int clampedLevel = Math.max(0, Math.min(getMaxReturnForceLevel(), level));
        if (clampedLevel <= 0) {
            return 0;
        }
        double normalized = clampedLevel / (double) getMaxReturnForceLevel();
        return clampReturnForce((int) Math.round(MAX_RETURN_FORCE_VALUE * normalized * normalized));
    }

    public static int returnForceToLevel(int force) {
        int clampedForce = clampReturnForce(force);
        if (clampedForce <= 0) {
            return 0;
        }
        double normalized = Math.sqrt(clampedForce / (double) MAX_RETURN_FORCE_VALUE);
        return Math.max(0, Math.min(getMaxReturnForceLevel(), (int) Math.round(normalized * getMaxReturnForceLevel())));
    }

    public static int getMinExpectedLengthTenths() {
        return MIN_EXPECTED_LENGTH_TENTHS;
    }

    public static int getMaxExpectedLengthTenths() {
        return MAX_EXPECTED_LENGTH_TENTHS;
    }

    public static double getMaxLinkLength() {
        return BREAK_LINK_LENGTH;
    }

    private static double clampEffectiveExpectedLength(double value) {
        if (!Double.isFinite(value)) {
            return MIN_LINK_LENGTH;
        }
        return Math.max(0.0D, Math.min(BREAK_LINK_LENGTH, value));
    }

    private static double clampEffectiveReturnForce(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(MAX_RETURN_FORCE_VALUE, value));
    }

    public static String formatPlainValue(int value) {
        return Integer.toString(Math.max(0, value));
    }

    public static String formatTenths(int value) {
        int clamped = clampExpectedLengthTenths(value);
        return clamped % 10 == 0
                ? Integer.toString(clamped / 10)
                : String.format(java.util.Locale.ROOT, "%.1f", clamped / 10.0D);
    }

    public static double calculateExpectedReturnForce(int returnForce, double deviation) {
        return calculateExpectedReturnForce((double) clampReturnForce(returnForce), deviation);
    }

    private static double calculateExpectedReturnForce(double returnForce, double deviation) {
        double clampedReturnForce = clampEffectiveReturnForce(returnForce);
        double magnitude = Math.abs(deviation);
        if (clampedReturnForce <= 0 || magnitude <= MIN_PHYSICS_DISTANCE) {
            return 0.0D;
        }
        double curvedMagnitude = Math.expm1(magnitude * RETURN_FORCE_CURVE);
        return Math.signum(deviation) * clampedReturnForce * RETURN_FORCE_PER_UNIT * curvedMagnitude;
    }

    private void applyLengthLimits(HydraulicConnectionHeadBlockEntity other,
                                   ServerSubLevel ownSubLevel, RigidBodyHandle ownHandle,
                                   Vector3d ownLocal, Vector3d ownWorld,
                                   @Nullable ServerSubLevel otherSubLevel, Vector3d otherLocal, Vector3d otherWorld,
                                   Vector3d worldDirection, double timeStep) {
        if (timeStep <= 0.0D) {
            return;
        }

        Vector3d connection = otherWorld.sub(ownWorld, new Vector3d());
        double actualDistance = connection.length();
        this.updateLinkStrainEffect(actualDistance);
        if (actualDistance > BREAK_LINK_LENGTH) {
            this.breakOverstretchedLink();
            return;
        }

        double distance = connection.dot(worldDirection);
        Vector3d correctionDirection = new Vector3d(worldDirection);
        if (this.creativeLink) {
            double effectiveExpectedLength = this.updateEffectiveExpectedLength(distance, timeStep);
            this.refreshRodDampingMotor();
            this.wakeCreativeServoBodiesIfNeeded(distance, effectiveExpectedLength, ownSubLevel, otherSubLevel);
            this.enforceCreativeLengthLimits(distance, correctionDirection, ownSubLevel, ownHandle,
                    ownLocal, otherSubLevel, otherLocal, timeStep);
            return;
        }

        double springImpulseMagnitude = 0.0D;
        if (!this.freeMode) {
            double effectiveExpectedLength = this.updateEffectiveExpectedLength(distance, timeStep);
            double effectiveReturnForce = this.updateEffectiveReturnForce(timeStep);
            springImpulseMagnitude += calculateExpectedLengthReturnImpulse(
                    distance, effectiveExpectedLength, effectiveReturnForce, timeStep);
        }

        springImpulseMagnitude = clampImpulse(springImpulseMagnitude, MAX_COMBINED_LENGTH_CONTROL_IMPULSE);
        if (Math.abs(springImpulseMagnitude) > 1.0E-8D) {
            Vector3d lengthControl = new Vector3d(correctionDirection).mul(springImpulseMagnitude);
            this.applyLengthImpulse(ownSubLevel, ownHandle, ownLocal, otherSubLevel, otherLocal, lengthControl);
        }

        double correctionSign;
        double excess;
        if (distance < MIN_LINK_LENGTH) {
            correctionSign = -1.0D;
            excess = MIN_LINK_LENGTH - distance;
        } else if (distance > MAX_LINK_LENGTH) {
            correctionSign = 1.0D;
            excess = distance - MAX_LINK_LENGTH;
        } else {
            return;
        }

        double springMagnitude = LENGTH_LIMIT_STIFFNESS * Math.expm1(excess * LENGTH_LIMIT_CURVE);
        double impulseMagnitude = springMagnitude * timeStep;
        if (impulseMagnitude <= 0.0D) {
            return;
        }
        impulseMagnitude = Math.min(impulseMagnitude, MAX_LENGTH_LIMIT_IMPULSE);

        Vector3d worldImpulse = correctionDirection.mul(impulseMagnitude * correctionSign);
        this.applyLengthImpulse(ownSubLevel, ownHandle, ownLocal, otherSubLevel, otherLocal, worldImpulse);
    }

    private void enforceCreativeLengthLimits(double distance, Vector3d correctionDirection,
                                             ServerSubLevel ownSubLevel, RigidBodyHandle ownHandle,
                                             Vector3d ownLocal, @Nullable ServerSubLevel otherSubLevel,
                                             Vector3d otherLocal, double timeStep) {
        double correctionSign;
        double excess;
        if (distance < MIN_LINK_LENGTH) {
            correctionSign = -1.0D;
            excess = MIN_LINK_LENGTH - distance;
        } else if (distance > MAX_LINK_LENGTH) {
            correctionSign = 1.0D;
            excess = distance - MAX_LINK_LENGTH;
        } else {
            return;
        }

        double springMagnitude = LENGTH_LIMIT_STIFFNESS * Math.expm1(excess * LENGTH_LIMIT_CURVE);
        double impulseMagnitude = Math.min(springMagnitude * timeStep, MAX_LENGTH_LIMIT_IMPULSE);
        if (impulseMagnitude <= 0.0D) {
            return;
        }

        Vector3d worldImpulse = new Vector3d(correctionDirection).mul(impulseMagnitude * correctionSign);
        this.applyLengthImpulse(ownSubLevel, ownHandle, ownLocal, otherSubLevel, otherLocal, worldImpulse);
    }

    private void wakeCreativeServoBodiesIfNeeded(double distance, double effectiveExpectedLength,
                                                 ServerSubLevel ownSubLevel,
                                                 @Nullable ServerSubLevel otherSubLevel) {
        if (this.level == null || !this.creativeLink) {
            return;
        }

        long gameTime = this.level.getGameTime();
        if (gameTime > this.creativeServoWakeUntilGameTime) {
            return;
        }

        double targetDistance = this.expectedLengthTenths / 10.0D;
        boolean targetStillApproaching = Math.abs(effectiveExpectedLength - targetDistance) > CREATIVE_LENGTH_WAKE_EPSILON;
        boolean servoStillPulling = Math.abs(distance - effectiveExpectedLength) > CREATIVE_LENGTH_WAKE_EPSILON;
        if (!targetStillApproaching && !servoStillPulling) {
            this.creativeServoWakeUntilGameTime = Long.MIN_VALUE;
            return;
        }

        this.wakeCreativeServoBodies(ownSubLevel, otherSubLevel);
    }

    private void scheduleCreativeServoWake() {
        if (this.level == null || this.level.isClientSide || !this.creativeLink) {
            return;
        }

        this.creativeServoWakeUntilGameTime = this.level.getGameTime() + CREATIVE_LENGTH_WAKE_TICKS;
        ServerSubLevel ownSubLevel = this.getRodConstraintServerSubLevel(this.getContainingServerSubLevel());
        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        ServerSubLevel otherSubLevel = other != null
                ? other.getRodConstraintServerSubLevel(other.getContainingServerSubLevel())
                : null;
        this.wakeCreativeServoBodies(ownSubLevel, otherSubLevel);
    }

    private void wakeCreativeServoBodies(@Nullable ServerSubLevel ownSubLevel,
                                         @Nullable ServerSubLevel otherSubLevel) {
        if (this.level == null || this.level.isClientSide || !this.creativeLink) {
            return;
        }

        long gameTime = this.level.getGameTime();
        if (this.lastCreativeServoWakeGameTime == gameTime) {
            return;
        }
        this.lastCreativeServoWakeGameTime = gameTime;

        PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(this.level).getPipeline();
        if (ownSubLevel != null && !ownSubLevel.isRemoved()) {
            pipeline.wakeUp(ownSubLevel);
        }
        if (otherSubLevel != null && !otherSubLevel.isRemoved()) {
            pipeline.wakeUp(otherSubLevel);
        }
    }

    private void breakOverstretchedLink() {
        this.detachLink(true);
    }

    public float getLinkStrainEffect() {
        return this.linkStrainEffect;
    }

    private void updateLinkStrainEffect(double actualDistance) {
        boolean strained = actualDistance >= LINK_STRAIN_LENGTH;
        this.setLinkStrainState(strained);
        HydraulicConnectionHeadBlockEntity other = this.resolveLinkedConnectionHead();
        if (other != null && other.references(this)) {
            other.setLinkStrainState(strained);
        }
    }

    private void triggerLinkStrainEffect(boolean strained) {
        this.setLinkStrainState(strained);
    }

    private void setLinkStrainState(boolean strained) {
        if (this.linkWasStrained == strained) {
            return;
        }

        this.linkWasStrained = strained;
        this.linkStrainEffect = strained ? 1.0F : -1.0F;
        this.linkStrainEffectDelay = strained ? 0 : LINK_STRAIN_RECOVERY_TICKS;
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
            this.sendData();
        }
    }

    private void tickLinkStrainEffect() {
        if (this.linkWasStrained) {
            this.linkStrainEffect = 1.0F;
            return;
        }
        if (this.linkStrainEffectDelay > 0) {
            this.linkStrainEffectDelay--;
            return;
        }
        if (this.linkStrainEffect == 0.0F) {
            return;
        }

        this.linkStrainEffect -= this.linkStrainEffect * 0.1F;
        if (Math.abs(this.linkStrainEffect) < 1.0F / 128.0F) {
            this.linkStrainEffect = 0.0F;
        }
    }

    private double calculateExpectedLengthReturnImpulse(double distance, double targetDistance,
                                                       double effectiveReturnForce, double timeStep) {
        if (effectiveReturnForce <= 0.0D) {
            return 0.0D;
        }

        double deviation = distance - targetDistance;
        if (Math.abs(deviation) <= MIN_PHYSICS_DISTANCE) {
            return 0.0D;
        }

        double restoringForce = calculateExpectedReturnForce(effectiveReturnForce, deviation);
        double impulseMagnitude = restoringForce * timeStep;
        if (Math.abs(impulseMagnitude) <= 1.0E-8D) {
            return 0.0D;
        }
        return clampImpulse(impulseMagnitude, MAX_EXPECTED_RETURN_IMPULSE);
    }

    private double updateEffectiveExpectedLength(double actualDistance, double timeStep) {
        if (this.expectedLengthTransitionPending || !Double.isFinite(this.effectiveExpectedLengthBlocks)) {
            this.effectiveExpectedLengthBlocks = actualDistance;
            this.expectedLengthTransitionPending = false;
            return this.effectiveExpectedLengthBlocks;
        }

        double targetDistance = this.expectedLengthTenths / 10.0D;
        double approachRate = this.creativeLink ? CREATIVE_LENGTH_APPROACH_RATE : EXPECTED_LENGTH_APPROACH_RATE;
        double maxStep = Math.max(0.0D, timeStep) * approachRate * this.expectedLengthApproachMultiplier;
        this.effectiveExpectedLengthBlocks = approach(this.effectiveExpectedLengthBlocks, targetDistance, maxStep);
        return this.effectiveExpectedLengthBlocks;
    }

    private double updateEffectiveReturnForce(double timeStep) {
        if (this.freeMode) {
            this.effectiveReturnForce = 0.0D;
            return 0.0D;
        }

        if (!Double.isFinite(this.effectiveReturnForce)) {
            this.effectiveReturnForce = 0.0D;
        }

        double targetForce = clampReturnForce(this.returnForce);
        double maxStep = Math.max(0.0D, timeStep) * RETURN_FORCE_APPROACH_RATE;
        this.effectiveReturnForce = clampEffectiveReturnForce(approach(this.effectiveReturnForce, targetForce, maxStep));
        return this.effectiveReturnForce;
    }

    private static double approach(double current, double target, double maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }

    private static double clampImpulse(double impulse, double maxMagnitude) {
        if (impulse > maxMagnitude) {
            return maxMagnitude;
        }
        if (impulse < -maxMagnitude) {
            return -maxMagnitude;
        }
        return impulse;
    }

    private static Vector3d clampVector(Vector3d vector, double maxMagnitude) {
        double lengthSquared = vector.lengthSquared();
        if (lengthSquared > maxMagnitude * maxMagnitude) {
            vector.mul(maxMagnitude / Math.sqrt(lengthSquared));
        }
        return vector;
    }

    private void applyLengthVelocityChange(RigidBodyHandle ownHandle, @Nullable ServerSubLevel otherSubLevel,
                                           Vector3d worldVelocityDelta) {
        ownHandle.addLinearAndAngularVelocity(worldVelocityDelta, new Vector3d());

        if (otherSubLevel == null) {
            return;
        }

        RigidBodyHandle otherHandle = RigidBodyHandle.of(otherSubLevel);
        if (otherHandle == null || !otherHandle.isValid()) {
            return;
        }

        otherHandle.addLinearAndAngularVelocity(worldVelocityDelta.negate(new Vector3d()), new Vector3d());
    }

    private void applyLengthImpulse(ServerSubLevel ownSubLevel, RigidBodyHandle ownHandle, Vector3d ownLocal,
                                    @Nullable ServerSubLevel otherSubLevel, Vector3d otherLocal,
                                    Vector3d worldImpulse) {
        Vector3d ownImpulse = ownSubLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
        this.lengthForceTotal.applyImpulseAtPoint(ownSubLevel, ownLocal, ownImpulse);
        ownHandle.applyForcesAndReset(this.lengthForceTotal);

        if (otherSubLevel == null) {
            return;
        }

        RigidBodyHandle otherHandle = RigidBodyHandle.of(otherSubLevel);
        if (otherHandle == null) {
            return;
        }

        Vector3d partnerImpulse = otherSubLevel.logicalPose().transformNormalInverse(worldImpulse.negate(new Vector3d()), new Vector3d());
        this.partnerLengthForceTotal.applyImpulseAtPoint(otherSubLevel, otherLocal, partnerImpulse);
        otherHandle.applyForcesAndReset(this.partnerLengthForceTotal);
    }

    private static void syncSelectionToClient(Player player, @Nullable JointBindingData.Selection selection) {
        if (player instanceof ServerPlayer serverPlayer) {
            SyncHydraulicSelectionPayload.send(serverPlayer, selection);
        }
    }

    public enum LinkResult {
        SUCCESS("linked", true, true),
        ALREADY_LINKED("linked", true, false),
        TARGET_UNAVAILABLE("target_missing", true, false),
        WRONG_DIMENSION("wrong_dimension", false, false),
        TOO_FAR("too_far", false, false),
        NOT_ALIGNED("not_aligned", false, false),
        SELF("same_joint", false, false),
        SAME_STRUCTURE("same_structure", false, false);

        private final String keySuffix;
        private final boolean clearsSelection;
        private final boolean consumesRod;

        LinkResult(String keySuffix, boolean clearsSelection, boolean consumesRod) {
            this.keySuffix = keySuffix;
            this.clearsSelection = clearsSelection;
            this.consumesRod = consumesRod;
        }

        public Component message() {
            return Component.translatable("message.aeronautics_utility_objects." + this.keySuffix);
        }

        public boolean clearsSelection() {
            return this.clearsSelection;
        }

        public boolean consumesRod() {
            return this.consumesRod;
        }
    }
}
