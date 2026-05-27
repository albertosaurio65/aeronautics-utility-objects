package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockItem;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockItem;
import com.enxv.aerouniversaljoint.content.HydraulicRegulatorBlockItem;
import com.enxv.aerouniversaljoint.content.HydraulicRodItem;
import com.enxv.aerouniversaljoint.content.UniversalJointBlockItem;
import com.enxv.aerouniversaljoint.content.UniversalJointRodItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroUniversalJointMod.MOD_ID);

    public static final DeferredItem<UniversalJointBlockItem> UNIVERSAL_JOINT = ITEMS.register(
            "universal_joint",
            () -> new UniversalJointBlockItem(ModBlocks.UNIVERSAL_JOINT.get(), new Item.Properties()));

    public static final DeferredItem<UniversalJointBlockItem> BRASS_UNIVERSAL_JOINT = ITEMS.register(
            "brass_universal_joint",
            () -> new UniversalJointBlockItem(ModBlocks.BRASS_UNIVERSAL_JOINT.get(), new Item.Properties()));

    public static final DeferredItem<DampingStressBearingBlockItem> DAMPING_STRESS_BEARING = ITEMS.register(
            "damping_stress_bearing",
            () -> new DampingStressBearingBlockItem(ModBlocks.DAMPING_STRESS_BEARING.get(), new Item.Properties()));

    public static final DeferredItem<HydraulicConnectionHeadBlockItem> HYDRAULIC_CONNECTION_HEAD = ITEMS.register(
            "hydraulic_connection_head",
            () -> new HydraulicConnectionHeadBlockItem(ModBlocks.HYDRAULIC_CONNECTION_HEAD.get(), new Item.Properties()));

    public static final DeferredItem<HydraulicConnectionHeadBlockItem> HYDRAULIC_HINGE_HEAD = ITEMS.register(
            "hydraulic_hinge_head",
            () -> new HydraulicConnectionHeadBlockItem(ModBlocks.HYDRAULIC_HINGE_HEAD.get(), new Item.Properties(),
                    "tooltip.aeronautics_utility_objects.hydraulic_hinge_head_hint",
                    "tooltip.aeronautics_utility_objects.hydraulic_hinge_head_range"));

    public static final DeferredItem<HydraulicRegulatorBlockItem> HYDRAULIC_REGULATOR = ITEMS.register(
            "hydraulic_regulator",
            () -> new HydraulicRegulatorBlockItem(ModBlocks.HYDRAULIC_REGULATOR.get(), new Item.Properties()));

    public static final DeferredItem<UniversalJointRodItem> UNIVERSAL_JOINT_ROD = ITEMS.register(
            "universal_joint_rod",
            () -> new UniversalJointRodItem(new Item.Properties().stacksTo(16), true));

    public static final DeferredItem<UniversalJointRodItem> ANDESITE_UNIVERSAL_JOINT_ROD = ITEMS.register(
            "universal_joint_rod2",
            () -> new UniversalJointRodItem(new Item.Properties().stacksTo(16), false));

    public static final DeferredItem<HydraulicRodItem> HYDRAULIC_ROD = ITEMS.register(
            "hydraulic_rod",
            () -> new HydraulicRodItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<HydraulicRodItem> CREATIVE_HYDRAULIC_ROD = ITEMS.register(
            "creative_hydraulic_rod",
            () -> new HydraulicRodItem(new Item.Properties().stacksTo(16), true));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

