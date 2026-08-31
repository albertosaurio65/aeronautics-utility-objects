package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.ModItems;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class AeroUniversalJointPonderScenes {
    private AeroUniversalJointPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation simulatedContraptions = ResourceLocation.fromNamespaceAndPath("simulated",
                "physics_assembler/simulated_contraptions");

        helper.forComponents(
                        ModItems.HYDRAULIC_ROD.getId(),
                        ModItems.CREATIVE_HYDRAULIC_ROD.getId(),
                        ModItems.HYDRAULIC_CONNECTION_HEAD.getId())
                .addStoryBoard(simulatedContraptions, HydraulicRodScenes::hydraulicRodProperties)
                .addStoryBoard(simulatedContraptions, HydraulicRodScenes::hydraulicRodSettings);

        helper.forComponents(ModItems.HYDRAULIC_REGULATOR.getId())
                .addStoryBoard(simulatedContraptions, HydraulicRodScenes::hydraulicRegulatorControl);

        helper.forComponents(ModItems.HYDRAULIC_HINGE_HEAD.getId())
                .addStoryBoard(simulatedContraptions, HydraulicRodScenes::hydraulicHingeHead);

        helper.forComponents(ModItems.DAMPING_STRESS_BEARING.getId())
                .addStoryBoard(simulatedContraptions, KineticConversionBearingScenes::conversionOutput);

        helper.forComponents(
                        ModItems.UNIVERSAL_JOINT.getId(),
                        ModItems.BRASS_UNIVERSAL_JOINT.getId(),
                        ModItems.UNIVERSAL_JOINT_ROD.getId(),
                        ModItems.ANDESITE_UNIVERSAL_JOINT_ROD.getId())
                .addStoryBoard(simulatedContraptions, HydraulicRodScenes::universalJointVariants);
    }
}
