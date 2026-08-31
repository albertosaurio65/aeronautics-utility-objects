package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockEntity;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.HydraulicRegulatorBlockEntity;
import com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroUniversalJointMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UniversalJointBlockEntity>> UNIVERSAL_JOINT =
            BLOCK_ENTITIES.register(
                    "universal_joint",
                    () -> BlockEntityType.Builder.of(
                            UniversalJointBlockEntity::new,
                            ModBlocks.UNIVERSAL_JOINT.get(),
                            ModBlocks.BRASS_UNIVERSAL_JOINT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DampingStressBearingBlockEntity>> DAMPING_STRESS_BEARING =
            BLOCK_ENTITIES.register(
                    "damping_stress_bearing",
                    () -> BlockEntityType.Builder.of(
                            DampingStressBearingBlockEntity::new,
                            ModBlocks.DAMPING_STRESS_BEARING.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydraulicConnectionHeadBlockEntity>> HYDRAULIC_CONNECTION_HEAD =
            BLOCK_ENTITIES.register(
                    "hydraulic_connection_head",
                    () -> BlockEntityType.Builder.of(
                            HydraulicConnectionHeadBlockEntity::new,
                            ModBlocks.HYDRAULIC_CONNECTION_HEAD.get(),
                            ModBlocks.HYDRAULIC_HINGE_HEAD.get(),
                            ModBlocks.BRASS_HYDRAULIC_HINGE_HEAD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydraulicRegulatorBlockEntity>> HYDRAULIC_REGULATOR =
            BLOCK_ENTITIES.register(
                    "hydraulic_regulator",
                    () -> BlockEntityType.Builder.of(
                            HydraulicRegulatorBlockEntity::new,
                            ModBlocks.HYDRAULIC_REGULATOR.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}

