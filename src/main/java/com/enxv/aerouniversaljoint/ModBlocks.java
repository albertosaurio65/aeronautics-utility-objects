package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.content.DampingStressBearingBlock;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlock;
import com.enxv.aerouniversaljoint.content.HydraulicHingeHeadBlock;
import com.enxv.aerouniversaljoint.content.HydraulicHingeLinkBlock;
import com.enxv.aerouniversaljoint.content.BrassHydraulicHingeHeadBlock;
import com.enxv.aerouniversaljoint.content.HydraulicRegulatorBlock;
import com.enxv.aerouniversaljoint.content.JointVariant;
import com.enxv.aerouniversaljoint.content.UniversalJointBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroUniversalJointMod.MOD_ID);

    public static final DeferredBlock<UniversalJointBlock> UNIVERSAL_JOINT = BLOCKS.register(
            "universal_joint",
            () -> new UniversalJointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), JointVariant.ANDESITE));

    public static final DeferredBlock<UniversalJointBlock> BRASS_UNIVERSAL_JOINT = BLOCKS.register(
            "brass_universal_joint",
            () -> new UniversalJointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), JointVariant.BRASS));

    public static final DeferredBlock<DampingStressBearingBlock> DAMPING_STRESS_BEARING = BLOCKS.register(
            "damping_stress_bearing",
            () -> new DampingStressBearingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<HydraulicConnectionHeadBlock> HYDRAULIC_CONNECTION_HEAD = BLOCKS.register(
            "hydraulic_connection_head",
            () -> new HydraulicConnectionHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<HydraulicHingeHeadBlock> HYDRAULIC_HINGE_HEAD = BLOCKS.register(
            "hydraulic_hinge_head",
            () -> new HydraulicHingeHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<BrassHydraulicHingeHeadBlock> BRASS_HYDRAULIC_HINGE_HEAD = BLOCKS.register(
            "brass_hydraulic_hinge_head",
            () -> new BrassHydraulicHingeHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<HydraulicHingeLinkBlock> HYDRAULIC_HINGE_LINK = BLOCKS.register(
            "hydraulic_hinge_link",
            () -> new HydraulicHingeLinkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<HydraulicRegulatorBlock> HYDRAULIC_REGULATOR = BLOCKS.register(
            "hydraulic_regulator",
            () -> new HydraulicRegulatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}

