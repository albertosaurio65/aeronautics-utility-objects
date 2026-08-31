package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModItems;
import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.KineticEffectHandlerAccess;
import com.enxv.aerouniversaljoint.access.KineticVisualEffectAccess;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class UniversalJointBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor,
        DetachedKineticSafetyGuard, KineticVisualEffectAccess, MenuProvider, SubLevelLinkedEndpoint {
    private static final String TAG_LINKED_POS = "LinkedPos";
    private static final String TAG_LINKED_SUB_LEVEL = "LinkedSubLevel";
    private static final String TAG_TRANSMISSION_AXIS_ALIGNED = "TransmissionAxisAligned";
    private static final String TAG_LINKED_REST_LENGTH = "LinkedRestLength";
    private static final String TAG_LINKED_VARIANT = "LinkedVariant";
    private static final String TAG_LINK_STRAIN_EFFECT = "LinkStrainEffect";
    private static final String TAG_LINK_STRAINED = "LinkStrained";

    private static final double MIN_PHYSICS_DISTANCE = 1.0E-4D;
    private static final int SERVER_LINK_VALIDATION_INTERVAL = 10;
    private static final int LINK_STRAIN_RECOVERY_TICKS = 12;

    @Nullable
    private BlockPos linkedPos;
    @Nullable
    private UUID linkedSubLevelId;
    @Nullable
    private JointVariant linkedVariant;
    private double linkedRestLength = Double.NaN;

    private final ForceTotal elasticForceTotal = new ForceTotal();
    private final ForceTotal partnerElasticForceTotal = new ForceTotal();
    private int serverValidationCountdown = SERVER_LINK_VALIDATION_INTERVAL;
    @Nullable
    private Boolean lastTransmissionSource;
    @Nullable
    private Boolean lastObservedAxisAlignment;
    @Nullable
    private Boolean transmissionAxisAligned;
    private boolean detachingForBlockRemoval;
    private boolean preservingLinkForSubLevelMove;
    private int linkStrainEffectDelay;
    private float linkStrainEffect;
    private boolean linkWasStrained;

    public UniversalJointBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.UNIVERSAL_JOINT.get(), pos, state);
    }

    public UniversalJointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public JointVariant getVariant() {
        return JointVariant.fromState(this.getBlockState());
    }

    public JointVariant getLinkVariant() {
        return this.linkedVariant != null ? this.linkedVariant : this.getVariant();
    }

    @Override
    public void tick() {
        if (this.level == null) {
            return;
        }

        super.tick();
        this.aeronautics$tickLinkStrainEffect();

        if (this.level.isClientSide || this.linkedPos == null) {
            return;
        }

        this.refreshLinkIfTransmissionSourceChanged();

        if (--this.serverValidationCountdown <= 0) {
            this.serverValidationCountdown = SERVER_LINK_VALIDATION_INTERVAL;
            this.validateLinkState(false);
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
        this.validateLinkState(true);
    }

    @Override
    public void remove() {
        boolean preservingForSubLevelMove = this.preservingLinkForSubLevelMove;
        this.preservingLinkForSubLevelMove = false;
        if (this.level != null && !this.level.isClientSide && this.linkedPos != null) {
            if (preservingForSubLevelMove) {
                this.clearLinkInternal(false);
            } else if (this.detachingForBlockRemoval) {
                this.clearLinkInternal(true);
            } else {
                this.detachLink(true);
            }
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
        if (Double.isFinite(this.linkedRestLength)) {
            tag.putDouble(TAG_LINKED_REST_LENGTH, this.linkedRestLength);
        }
        if (this.linkedVariant != null) {
            tag.putString(TAG_LINKED_VARIANT, this.linkedVariant.getSerializedName());
        }
        if (this.transmissionAxisAligned != null) {
            tag.putBoolean(TAG_TRANSMISSION_AXIS_ALIGNED, this.transmissionAxisAligned);
        }
        if (this.linkStrainEffect != 0.0F) {
            tag.putFloat(TAG_LINK_STRAIN_EFFECT, this.linkStrainEffect);
        }
        if (this.linkWasStrained) {
            tag.putBoolean(TAG_LINK_STRAINED, true);
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        BlockPos oldLinkedPos = this.linkedPos;
        UUID oldLinkedSubLevelId = this.linkedSubLevelId;
        this.linkedPos = tag.contains(TAG_LINKED_POS, Tag.TAG_INT_ARRAY)
                ? NbtUtils.readBlockPos(tag, TAG_LINKED_POS).orElse(null)
                : null;
        this.linkedSubLevelId = tag.hasUUID(TAG_LINKED_SUB_LEVEL) ? tag.getUUID(TAG_LINKED_SUB_LEVEL) : null;
        this.linkedRestLength = tag.contains(TAG_LINKED_REST_LENGTH, Tag.TAG_DOUBLE)
                ? sanitizeRestLength(tag.getDouble(TAG_LINKED_REST_LENGTH))
                : Double.NaN;
        this.linkedVariant = tag.contains(TAG_LINKED_VARIANT, Tag.TAG_STRING)
                ? JointVariant.byName(tag.getString(TAG_LINKED_VARIANT))
                : null;
        this.transmissionAxisAligned = tag.contains(TAG_TRANSMISSION_AXIS_ALIGNED)
                ? tag.getBoolean(TAG_TRANSMISSION_AXIS_ALIGNED)
                : null;
        this.linkStrainEffect = tag.contains(TAG_LINK_STRAIN_EFFECT, Tag.TAG_FLOAT)
                ? tag.getFloat(TAG_LINK_STRAIN_EFFECT)
                : 0.0F;
        this.linkWasStrained = tag.contains(TAG_LINK_STRAINED, Tag.TAG_BYTE)
                ? tag.getBoolean(TAG_LINK_STRAINED)
                : this.linkStrainEffect > 0.0F;
        this.linkStrainEffectDelay = this.linkWasStrained || this.linkStrainEffect >= 0.0F
                ? 0
                : LINK_STRAIN_RECOVERY_TICKS;
        super.read(tag, registries, clientPacket);

        if (!Objects.equals(oldLinkedPos, this.linkedPos) || !Objects.equals(oldLinkedSubLevelId, this.linkedSubLevelId)) {
            this.clearObservedTransmissionState();
            this.invalidateRenderBoundingBox();
        }
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        super.addPropagationLocations(block, state, neighbours);

        if (this.linkedPos == null || !this.isReferenceReady()) {
            return neighbours;
        }

        UniversalJointBlockEntity other = this.resolveLinkedJoint();
        if (other == null || !other.references(this)) {
            return neighbours;
        }

        if (this.isTransmissionSourceFor(other)) {
            neighbours.add(this.linkedPos);
        }

        return neighbours;
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        if (target instanceof UniversalJointBlockEntity other && this.references(other) && other.references(this)) {
            if (this.isTransmissionSourceFor(other)) {
                return this.getRotationAxisAlignmentModifier(other);
            } else {
                return 0.0f;
            }
        }

        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!this.getLinkVariant().isElastic() || this.level == null || this.level.isClientSide) {
            return;
        }

        if (!Objects.equals(this.getContainingSubLevelId(), subLevel.getUniqueId())) {
            return;
        }

        UniversalJointBlockEntity other = this.resolveLinkedJoint();
        if (other == null || !other.references(this) || !this.shouldControlElasticLink(other)) {
            return;
        }

        ServerSubLevel otherSubLevel = other.getContainingSubLevelId() != null
                ? (ServerSubLevel) SubLevelContainer.getContainer(this.level).getSubLevel(other.getContainingSubLevelId())
                : null;
        if (other.getContainingSubLevelId() != null && otherSubLevel == null) {
            return;
        }

        if (otherSubLevel == subLevel) {
            return;
        }

        Vector3d ownLocal = this.localCenterOf(this.worldPosition);
        Vector3d otherLocal = this.localCenterOf(other.worldPosition);
        Vector3d ownWorld = this.toWorldPosition(subLevel, ownLocal);
        Vector3d otherWorld = this.toWorldPosition(otherSubLevel, otherLocal);
        Vector3d delta = otherWorld.sub(ownWorld, new Vector3d());
        double distance = delta.length();
        if (distance < MIN_PHYSICS_DISTANCE) {
            return;
        }

        JointVariant variant = this.getLinkVariant();
        double restLength = this.getEffectiveRestLength(distance);
        this.updateLinkStrainEffect(variant, distance, restLength);
        if (this.isBeyondElasticDisconnectRange(variant, distance, restLength)) {
            this.detachLink();
            return;
        }

        Vector3d direction = delta.div(distance, new Vector3d());
        if (variant == JointVariant.ANDESITE) {
            this.applyAndesiteElasticity(subLevel, handle, ownLocal, otherSubLevel, otherLocal, direction, distance, restLength, timeStep);
            return;
        }

        double overshoot = distance - variant.getSoftRange();
        if (overshoot <= 0.0D) {
            return;
        }

        Vector3d ownVelocity = Sable.HELPER.getVelocity(this.level, ownLocal, new Vector3d());
        Vector3d otherVelocity = Sable.HELPER.getVelocity(this.level, otherLocal, new Vector3d());
        double separatingSpeed = Math.max(0.0D, otherVelocity.sub(ownVelocity, new Vector3d()).dot(direction));
        double endpointProgress = Math.min(1.0D, overshoot / Math.max(MIN_PHYSICS_DISTANCE,
                variant.getDisconnectRange() - variant.getSoftRange()));
        double endpointMultiplier = 1.0D
                + (AeroUniversalJointConfig.brassJointEndpointPullMultiplier() - 1.0D)
                * endpointProgress * endpointProgress;
        double springMagnitude = AeroUniversalJointConfig.brassJointPullStiffness()
                * Math.expm1(overshoot * AeroUniversalJointConfig.brassJointPullCurve())
                * endpointMultiplier;
        double dampingMagnitude = separatingSpeed * (AeroUniversalJointConfig.brassJointPullDamping()
                + overshoot * AeroUniversalJointConfig.brassJointPullDampingGain());
        double impulseMagnitude = (springMagnitude + dampingMagnitude) * timeStep;
        if (impulseMagnitude <= 0.0D) {
            return;
        }

        this.applyElasticImpulse(
                subLevel,
                handle,
                ownLocal,
                otherSubLevel,
                otherLocal,
                direction.mul(impulseMagnitude, new Vector3d()));
    }

    public ItemInteractionResult handleHeldRodItem(Player player, net.minecraft.world.InteractionHand hand, boolean brassRod) {
        if (this.level == null) {
            return ItemInteractionResult.SUCCESS;
        }

        if (this.level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        Optional<PendingRodSelections.PendingSelection> selection = PendingRodSelections.readPending(player);
        if (player.isShiftKeyDown() && selection.isPresent()) {
            PendingRodSelections.clear(player);
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.selection_cleared"), true);
            return ItemInteractionResult.SUCCESS;
        }

        if (selection.isEmpty()) {
            JointBindingData.Selection newSelection = new JointBindingData.Selection(
                    this.level.dimension().location(),
                    this.worldPosition,
                    this.getContainingSubLevelId());
            PendingRodSelections.write(player, newSelection, brassRod);
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.endpoint_selected"), true);
            return ItemInteractionResult.SUCCESS;
        }

        if (selection.get().brassRod() != brassRod) {
            PendingRodSelections.clear(player);
            player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.selection_cleared"), true);
            return ItemInteractionResult.SUCCESS;
        }

        JointBindingData.Selection storedSelection = selection.get().selection();
        JointBindingData.Selection stored = this.resolveReferenceFast(storedSelection.pos(), storedSelection.subLevelId()) != null
                ? storedSelection
                : RecentMoveRemapper.remap(this.level, storedSelection).orElse(storedSelection);
        LinkResult result = this.linkToSelection(stored, brassRod);
        if (result.clearsSelection()) {
            PendingRodSelections.clear(player);
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

    public LinkResult linkToSelection(JointBindingData.Selection selection, boolean brassRod) {
        if (this.level == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (!this.level.dimension().location().equals(selection.dimensionId())) {
            return LinkResult.WRONG_DIMENSION;
        }

        UniversalJointBlockEntity other = this.resolveReference(selection.pos(), selection.subLevelId());
        if (other == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }

        return this.createMutualLink(other, brassRod);
    }

    public LinkResult createMutualLink(UniversalJointBlockEntity other, boolean brassRod) {
        if (other == this) {
            return LinkResult.SELF;
        }

        if (this.level == null || other.level == null || !this.level.dimension().equals(other.level.dimension())) {
            return LinkResult.WRONG_DIMENSION;
        }

        JointVariant rodVariant = brassRod ? JointVariant.BRASS : JointVariant.ANDESITE;
        if (!rodVariant.isWithinLinkRange(this.distanceSquaredTo(other))) {
            return LinkResult.TOO_FAR;
        }

        if (this.references(other) && other.references(this)) {
            return LinkResult.ALREADY_LINKED;
        }

        this.clearLinkInternal(true);
        other.clearLinkInternal(true);

        double restLength = Math.sqrt(this.distanceSquaredTo(other));
        this.applyLinkReference(other.worldPosition, other.getContainingSubLevelId(), false);
        other.applyLinkReference(this.worldPosition, this.getContainingSubLevelId(), false);
        this.linkedVariant = rodVariant;
        other.linkedVariant = rodVariant;
        this.linkedRestLength = restLength;
        other.linkedRestLength = restLength;
        this.transmissionAxisAligned = this.hasMatchingWorldRotationAxis(other);
        other.transmissionAxisAligned = this.transmissionAxisAligned;
        this.refreshKineticsClearingSource();
        other.refreshKineticsClearingSource();
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
        this.preservingLinkForSubLevelMove = true;
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

    @Nullable
    public UniversalJointBlockEntity getLoadedLinkedJoint() {
        return this.resolveLinkedJoint();
    }

    @Override
    public @Nullable SubLevelLinkedEndpoint getLoadedLinkedEndpoint() {
        return this.getLoadedLinkedJoint();
    }

    public void updateReferenceTo(BlockPos newPos, @Nullable UUID newSubLevelId) {
        if (this.linkedPos == null) {
            return;
        }
        this.applyLinkReference(newPos, newSubLevelId, true);
    }

    public void remapLinkedReferenceAfterSubLevelMove() {
        if (this.level == null || this.linkedPos == null) {
            return;
        }

        Optional<JointBindingData.Selection> remapped = RecentMoveRemapper.remap(this.level, this.linkedSelection());
        if (remapped.isPresent() && !this.matchesSelection(remapped.get())) {
            this.applyLinkReference(remapped.get().pos(), remapped.get().subLevelId(), true);
        }
    }

    public boolean matchesSelection(JointBindingData.Selection selection) {
        return this.level != null
                && this.level.dimension().location().equals(selection.dimensionId())
                && this.worldPosition.equals(selection.pos())
                && Objects.equals(this.getContainingSubLevelId(), selection.subLevelId());
    }

    public LinkResult previewLinkToSelection(JointBindingData.Selection selection, boolean brassRod) {
        if (this.level == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (!this.level.dimension().location().equals(selection.dimensionId())) {
            return LinkResult.WRONG_DIMENSION;
        }

        UniversalJointBlockEntity other = this.resolveReference(selection.pos(), selection.subLevelId());
        if (other == null) {
            return LinkResult.TARGET_UNAVAILABLE;
        }
        if (other == this) {
            return LinkResult.SELF;
        }
        if (other.level == null || !this.level.dimension().equals(other.level.dimension())) {
            return LinkResult.WRONG_DIMENSION;
        }

        JointVariant rodVariant = brassRod ? JointVariant.BRASS : JointVariant.ANDESITE;
        if (!rodVariant.isWithinLinkRange(this.distanceSquaredTo(other))) {
            return LinkResult.TOO_FAR;
        }
        if (this.references(other) && other.references(this)) {
            return LinkResult.ALREADY_LINKED;
        }
        return LinkResult.SUCCESS;
    }

    public boolean references(UniversalJointBlockEntity other) {
        return this.linkedPos != null
                && this.linkedPos.equals(other.worldPosition)
                && Objects.equals(this.linkedSubLevelId, other.getContainingSubLevelId());
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

        UUID dependencySubLevelId = this.linkedSubLevelId != null
                ? this.linkedSubLevelId
                : SubLevelReferenceHelper.findContainingSubLevelId(this.level, this.linkedPos);
        if (dependencySubLevelId == null) {
            return null;
        }

        SubLevel subLevel = SubLevelContainer.getContainer(this.level).getSubLevel(dependencySubLevelId);
        return subLevel != null ? List.of(subLevel) : null;
    }

    @Nullable
    private UniversalJointBlockEntity resolveLinkedJoint() {
        return this.linkedPos == null ? null : this.resolveReference(this.linkedPos, this.linkedSubLevelId);
    }

    private JointBindingData.Selection linkedSelection() {
        return new JointBindingData.Selection(this.level.dimension().location(), Objects.requireNonNull(this.linkedPos), this.linkedSubLevelId);
    }

    private void validateLinkState(boolean remapReference) {
        if (this.level == null || this.level.isClientSide || this.linkedPos == null) {
            return;
        }

        boolean referenceReady = this.isReferenceReady();
        UniversalJointBlockEntity other = referenceReady ? this.resolveLinkedJoint() : null;
        if (other == null && remapReference) {
            Optional<JointBindingData.Selection> remapped = RecentMoveRemapper.remap(this.level, this.linkedSelection());
            if (remapped.isPresent() && !this.matchesSelection(remapped.get())) {
                this.applyLinkReference(remapped.get().pos(), remapped.get().subLevelId(), true);
                referenceReady = this.isReferenceReady();
                other = referenceReady ? this.resolveLinkedJoint() : null;
            }
        }

        if (!referenceReady) {
            return;
        }

        if (other == null || !other.references(this)) {
            if (!remapReference) {
                return;
            }
            this.clearLinkInternal(false);
            this.refreshKineticsClearingSource();
            return;
        }

        double distanceSquared = this.distanceSquaredTo(other);
        double distance = Math.sqrt(distanceSquared);
        JointVariant variant = this.getLinkVariant();
        if (this.isBeyondElasticDisconnectRange(variant, distance, this.getEffectiveRestLength(distance))) {
            this.detachLink();
        }
    }

    @Nullable
    private UniversalJointBlockEntity resolveReference(BlockPos pos, @Nullable UUID subLevelId) {
        if (this.level == null) {
            return null;
        }

        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntity(this.level, pos, subLevelId);
        return blockEntity instanceof UniversalJointBlockEntity joint ? joint : null;
    }

    @Nullable
    private UniversalJointBlockEntity resolveReferenceFast(BlockPos pos, @Nullable UUID subLevelId) {
        if (this.level == null) {
            return null;
        }

        BlockEntity blockEntity = SubLevelReferenceHelper.resolveBlockEntityFast(this.level, pos, subLevelId);
        return blockEntity instanceof UniversalJointBlockEntity joint ? joint : null;
    }

    private boolean isReferenceReady() {
        if (this.level == null || this.linkedPos == null) {
            return false;
        }
        if (this.resolveReferenceFast(this.linkedPos, this.linkedSubLevelId) != null) {
            return true;
        }
        if (this.linkedSubLevelId == null) {
            return false;
        }
        return SubLevelContainer.getContainer(this.level).getSubLevel(this.linkedSubLevelId) != null;
    }

    private void applyLinkReference(BlockPos pos, @Nullable UUID subLevelId, boolean refresh) {
        this.linkedPos = pos.immutable();
        this.linkedSubLevelId = subLevelId;
        this.clearObservedTransmissionState();
        this.invalidateRenderBoundingBox();
        if (refresh) {
            this.refreshKineticsClearingSource();
        } else {
            this.setChanged();
            this.sendData();
        }
    }

    private void clearLinkInternal(boolean updateOther) {
        BlockPos oldPos = this.linkedPos;
        UUID oldSubLevelId = this.linkedSubLevelId;

        this.linkedPos = null;
        this.linkedSubLevelId = null;
        this.linkedVariant = null;
        this.linkedRestLength = Double.NaN;
        this.transmissionAxisAligned = null;
        this.clearObservedTransmissionState();
        this.invalidateRenderBoundingBox();

        if (updateOther && this.level != null && oldPos != null) {
            UniversalJointBlockEntity other = this.resolveReference(oldPos, oldSubLevelId);
            if (other != null && other.references(this)) {
                other.clearLinkInternal(false);
                other.refreshKineticsClearingSource();
            }
        }
    }

    private void refreshKinetics() {
        this.refreshKinetics(false);
    }

    private void refreshKineticsClearingSource() {
        this.refreshKinetics(true);
    }

    private void refreshKinetics(boolean clearSource) {
        this.setChanged();
        this.sendData();

        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.detachKinetics();
        if (clearSource) {
            this.removeSource();
        }
        this.attachKinetics();
    }

    private void detachLink(boolean dropRod) {
        if (this.linkedPos == null) {
            return;
        }

        JointVariant droppedVariant = this.getLinkVariant();
        this.clearLinkInternal(true);
        if (dropRod) {
            this.dropLinkRod(droppedVariant);
        }
        this.refreshKineticsClearingSource();
    }

    private void dropLinkRod(JointVariant droppedVariant) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        Block.popResource(this.level, this.worldPosition, new ItemStack(droppedVariant == JointVariant.ANDESITE
                ? ModItems.ANDESITE_UNIVERSAL_JOINT_ROD.get()
                : ModItems.UNIVERSAL_JOINT_ROD.get()));
    }

    private boolean shouldControlElasticLink(UniversalJointBlockEntity other) {
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

    private boolean isTransmissionSourceFor(UniversalJointBlockEntity other) {
        TransmissionSourceDecision decision = this.resolveTransmissionSource(other);
        if (decision != TransmissionSourceDecision.UNKNOWN) {
            return decision == TransmissionSourceDecision.THIS;
        }
        if (this.lastTransmissionSource != null && other.lastTransmissionSource != null
                && this.lastTransmissionSource != other.lastTransmissionSource) {
            return this.lastTransmissionSource;
        }
        if (this.lastTransmissionSource != null) {
            return this.lastTransmissionSource;
        }
        if (other.lastTransmissionSource != null) {
            return !other.lastTransmissionSource;
        }
        return this.compareTransmissionOrder(other) < 0;
    }

    private TransmissionSourceDecision resolveTransmissionSource(UniversalJointBlockEntity other) {
        boolean thisReceivesFromOther = this.sourcePointsTo(other);
        boolean otherReceivesFromThis = other.sourcePointsTo(this);

        if (otherReceivesFromThis && !thisReceivesFromOther) {
            return TransmissionSourceDecision.THIS;
        }
        if (thisReceivesFromOther && !otherReceivesFromThis) {
            return TransmissionSourceDecision.OTHER;
        }

        boolean thisHasExternalInput = this.hasExternalKineticInput(other);
        boolean otherHasExternalInput = other.hasExternalKineticInput(this);
        if (thisHasExternalInput && !otherHasExternalInput) {
            return TransmissionSourceDecision.THIS;
        }
        if (otherHasExternalInput && !thisHasExternalInput) {
            return TransmissionSourceDecision.OTHER;
        }

        return TransmissionSourceDecision.UNKNOWN;
    }

    private boolean sourcePointsTo(UniversalJointBlockEntity other) {
        return this.source != null && this.source.equals(other.worldPosition);
    }

    private boolean hasExternalKineticInput(UniversalJointBlockEntity linkedJoint) {
        return this.source != null
                && !this.source.equals(linkedJoint.worldPosition)
                && Math.abs(this.getTheoreticalSpeed()) > 1.0E-4F;
    }

    private int compareTransmissionOrder(UniversalJointBlockEntity other) {
        int blockCompare = this.worldPosition.compareTo(other.worldPosition);
        if (blockCompare != 0) {
            return blockCompare;
        }

        return SubLevelReferenceHelper.compareNullableUuids(this.getContainingSubLevelId(), other.getContainingSubLevelId());
    }

    private void refreshLinkIfTransmissionSourceChanged() {
        if (!this.isReferenceReady()) {
            this.clearObservedTransmissionState();
            return;
        }

        UniversalJointBlockEntity other = this.resolveLinkedJoint();
        if (other == null || !other.references(this)) {
            this.clearObservedTransmissionState();
            return;
        }

        boolean transmissionSource = this.isTransmissionSourceFor(other);
        if (!this.hasTransmissionObservationChanged(other, transmissionSource)) {
            return;
        }

        this.rememberTransmissionState(other, transmissionSource);
        this.refreshLinkedKinetics(other);
        this.rememberTransmissionState(other, this.isTransmissionSourceFor(other));
    }

    private boolean hasTransmissionObservationChanged(UniversalJointBlockEntity other, boolean transmissionSource) {
        return this.lastTransmissionSource == null
                || this.lastTransmissionSource != transmissionSource
                || this.lastObservedAxisAlignment == null
                || this.lastObservedAxisAlignment != this.transmissionAxisAligned(other);
    }

    private void rememberTransmissionState(UniversalJointBlockEntity other, boolean thisTransmissionSource) {
        boolean matchingAxis = this.transmissionAxisAligned != null
                ? this.transmissionAxisAligned
                : this.hasMatchingWorldRotationAxis(other);
        this.transmissionAxisAligned = matchingAxis;
        other.transmissionAxisAligned = matchingAxis;
        this.lastTransmissionSource = thisTransmissionSource;
        this.lastObservedAxisAlignment = matchingAxis;

        other.lastTransmissionSource = !thisTransmissionSource;
        other.lastObservedAxisAlignment = matchingAxis;
    }

    private void refreshLinkedKinetics(UniversalJointBlockEntity other) {
        UniversalJointBlockEntity source = this.isTransmissionSourceFor(other) ? this : other;
        UniversalJointBlockEntity target = source == this ? other : this;

        target.refreshKinetics();
        source.refreshKinetics();
        source.rememberTransmissionState(target, true);
    }

    private void clearObservedTransmissionState() {
        this.lastTransmissionSource = null;
        this.lastObservedAxisAlignment = null;
    }

    private void applyElasticImpulse(ServerSubLevel ownSubLevel, RigidBodyHandle ownHandle, Vector3d ownLocal,
                                     @Nullable ServerSubLevel otherSubLevel, Vector3d otherLocal, Vector3d worldImpulse) {
        Vector3d ownImpulse = ownSubLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
        this.elasticForceTotal.applyImpulseAtPoint(ownSubLevel, ownLocal, ownImpulse);
        ownHandle.applyForcesAndReset(this.elasticForceTotal);

        if (otherSubLevel != null) {
            RigidBodyHandle otherHandle = RigidBodyHandle.of(otherSubLevel);
            Vector3d partnerImpulse = otherSubLevel.logicalPose().transformNormalInverse(worldImpulse.negate(new Vector3d()), new Vector3d());
            this.partnerElasticForceTotal.applyImpulseAtPoint(otherSubLevel, otherLocal, partnerImpulse);
            otherHandle.applyForcesAndReset(this.partnerElasticForceTotal);
        }
    }

    private void applyAndesiteElasticity(ServerSubLevel ownSubLevel, RigidBodyHandle ownHandle, Vector3d ownLocal,
                                         @Nullable ServerSubLevel otherSubLevel, Vector3d otherLocal,
                                         Vector3d direction, double distance, double restLength, double timeStep) {
        double deviation = distance - restLength;
        double magnitude = Math.abs(deviation);
        double softRange = JointVariant.ANDESITE.getSoftRange();
        if (magnitude <= softRange) {
            return;
        }

        double maxForceRange = Math.max(softRange + MIN_PHYSICS_DISTANCE, JointVariant.ANDESITE.getMaxForceRange());
        double normalized = (magnitude - softRange) / (maxForceRange - softRange);
        double force = AeroUniversalJointConfig.andesiteJointPullStiffness() * normalized * normalized;
        double signedForce = force * Math.signum(deviation);

        Vector3d ownVelocity = Sable.HELPER.getVelocity(this.level, ownLocal, new Vector3d());
        Vector3d otherVelocity = Sable.HELPER.getVelocity(this.level, otherLocal, new Vector3d());
        double relativeSpeed = otherVelocity.sub(ownVelocity, new Vector3d()).dot(direction);
        double damping = AeroUniversalJointConfig.andesiteJointPullDamping() * relativeSpeed;
        double impulseMagnitude = (signedForce + damping) * timeStep;
        if (Math.abs(impulseMagnitude) <= 1.0E-8D) {
            return;
        }

        this.applyElasticImpulse(ownSubLevel, ownHandle, ownLocal, otherSubLevel, otherLocal,
                direction.mul(impulseMagnitude, new Vector3d()));
    }

    private Vector3d localCenterOf(BlockPos pos) {
        return JOMLConversion.atCenterOf(pos);
    }

    private Vector3d toWorldPosition(@Nullable ServerSubLevel subLevel, Vector3d localPosition) {
        return subLevel == null ? localPosition : subLevel.logicalPose().transformPosition(localPosition, new Vector3d());
    }

    private float getRotationAxisAlignmentModifier(UniversalJointBlockEntity other) {
        return this.transmissionAxisAligned(other) ? 1.0F : -1.0F;
    }

    private boolean hasMatchingWorldRotationAxis(UniversalJointBlockEntity other) {
        return this.getWorldPositiveRotationAxis().dot(other.getWorldPositiveRotationAxis()) >= 0.0D;
    }

    private boolean transmissionAxisAligned(UniversalJointBlockEntity other) {
        if (this.transmissionAxisAligned != null) {
            return this.transmissionAxisAligned;
        }
        boolean aligned = this.hasMatchingWorldRotationAxis(other);
        this.transmissionAxisAligned = aligned;
        return aligned;
    }

    private Vector3d getWorldPositiveRotationAxis() {
        Direction.Axis axis = ((IRotate) this.getBlockState().getBlock()).getRotationAxis(this.getBlockState());
        Vector3d localAxis = positiveAxis(axis);
        if (this.level == null) {
            return localAxis;
        }

        SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing != null) {
            containing.logicalPose().transformNormal(localAxis, localAxis);
            if (localAxis.lengthSquared() > 1.0E-8D) {
                localAxis.normalize();
            }
        }

        return localAxis;
    }

    private static Vector3d positiveAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    private double distanceSquaredTo(UniversalJointBlockEntity other) {
        Vec3 ownCenter = Vec3.atCenterOf(this.worldPosition);
        Vec3 otherCenter = Vec3.atCenterOf(other.worldPosition);
        return Sable.HELPER.distanceSquaredWithSubLevels(this.level, ownCenter, otherCenter);
    }

    private double getEffectiveRestLength(double currentDistance) {
        return Double.isFinite(this.linkedRestLength) && this.linkedRestLength > MIN_PHYSICS_DISTANCE
                ? this.linkedRestLength
                : currentDistance;
    }

    private boolean isBeyondElasticDisconnectRange(JointVariant variant, double distance, double restLength) {
        double measured = variant == JointVariant.ANDESITE ? Math.abs(distance - restLength) : distance;
        return measured > variant.getDisconnectRange();
    }

    private void updateLinkStrainEffect(JointVariant variant, double distance, double restLength) {
        double measured = variant == JointVariant.ANDESITE ? Math.abs(distance - restLength) : distance;
        boolean strained = measured >= variant.getDisconnectRange() * 0.85D;
        this.setLinkStrainState(strained);
        UniversalJointBlockEntity other = this.resolveLinkedJoint();
        if (other != null && other.references(this)) {
            other.setLinkStrainState(strained);
        }
    }

    @Override
    public void aeronautics$triggerLinkStrainEffect(boolean strained) {
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

    @Override
    public float aeronautics$getLinkStrainEffect() {
        float kineticEffect = this.effects instanceof KineticEffectHandlerAccess access
                ? access.aeronautics$getOverStressedEffect()
                : 0.0F;
        return Math.abs(kineticEffect) >= Math.abs(this.linkStrainEffect) ? kineticEffect : this.linkStrainEffect;
    }

    @Override
    public void aeronautics$tickLinkStrainEffect() {
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

    private static double sanitizeRestLength(double value) {
        return Double.isFinite(value) && value > MIN_PHYSICS_DISTANCE ? value : Double.NaN;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        AABB base = super.createRenderBoundingBox();
        if (this.linkedPos == null) {
            return base;
        }

        if (this.getContainingSubLevelId() != null || this.linkedSubLevelId != null) {
            JointVariant variant = this.getLinkVariant();
            double radius = variant.getLinkRange() + variant.getDisconnectRange() + 1.0D;
            double diameter = radius * 2.0D;
            return AABB.ofSize(Vec3.atCenterOf(this.worldPosition), diameter, diameter, diameter);
        }

        return base.minmax(AABB.ofSize(Vec3.atCenterOf(this.linkedPos), 1.0D, 1.0D, 1.0D)).inflate(1.0D);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (!isSpeedRatioFeatureEnabled()) {
            return null;
        }
        return new UniversalJointMenu(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    public float getSpeedRatio() {
        return 1.0F;
    }

    public static boolean isSpeedRatioFeatureEnabled() {
        return false;
    }

    public void setSpeedRatio(float ratio) {
    }

    public void applySyncedSpeedRatio(float ratio) {
    }

    public static float getMaxSpeedRatio() {
        return 4.0F;
    }

    public static float getMinSpeedRatio() {
        return -4.0F;
    }

    public static Component describeSpeedRatio(float ratio) {
        if (Math.abs(ratio) < 0.001F) {
            return Component.literal("0x");
        }
        return Component.literal(String.format("%.2fx", ratio));
    }

    public enum LinkResult {
        SUCCESS("linked", true, true),
        ALREADY_LINKED("linked", true, false),
        TARGET_UNAVAILABLE("target_missing", true, false),
        WRONG_DIMENSION("wrong_dimension", false, false),
        TOO_FAR("too_far", false, false),
        SELF("same_joint", false, false),
        MISMATCHED_VARIANT("same_type", false, false);

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

    private enum TransmissionSourceDecision {
        THIS,
        OTHER,
        UNKNOWN
    }
}
