package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.ModItems;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlock;
import com.enxv.aerouniversaljoint.content.HydraulicHingeHeadBlock;
import com.enxv.aerouniversaljoint.content.HydraulicRegulatorBlock;
import com.enxv.aerouniversaljoint.content.JointVariant;
import com.enxv.aerouniversaljoint.content.UniversalJointBlock;
import com.enxv.aerouniversaljoint.client.HydraulicConnectionHeadSettingsRenderer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.ponder.SmoothMovementUtils;
import dev.simulated_team.simulated.ponder.instructions.CustomAnimateWorldSectionInstruction;
import dev.simulated_team.simulated.ponder.instructions.RedstoneSignalInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class HydraulicRodScenes {
    private HydraulicRodScenes() {
    }

    public static void hydraulicRodProperties(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "hydraulic_rod_properties", "Pneumatic rod properties");
        var world = scene.world();
        var overlay = scene.overlay();
        var select = util.select();
        var vector = util.vector();

        BlockPos leftHead = new BlockPos(3, 2, 3);
        BlockPos rightHead = new BlockPos(6, 2, 3);
        BlockState leftHeadState = hydraulicHead(Direction.EAST);
        BlockState rightHeadState = hydraulicHead(Direction.WEST);

        placeHydraulicRig(scene, util, leftHead, rightHead, leftHeadState, rightHeadState);
        Selection leftStructure = select.fromTo(2, 1, 2, 3, 1, 4)
                .add(select.position(2, 2, 3))
                .add(select.position(leftHead));
        Selection rightStructure = select.fromTo(6, 1, 2, 7, 1, 4)
                .add(select.position(7, 2, 3))
                .add(select.position(rightHead));
        ElementLink<WorldSectionElement> leftSection = world.showIndependentSection(leftStructure, Direction.DOWN);
        scene.idle(6);
        ElementLink<WorldSectionElement> rightSection = world.showIndependentSection(rightStructure, Direction.DOWN);
        scene.idle(8);

        scene.addInstruction(new CreateHydraulicRodInstruction(10, Direction.DOWN, new HydraulicRodElement(
                leftSection, rightSection, Vec3.atCenterOf(leftHead), Vec3.atCenterOf(rightHead), leftHeadState, false)));
        scene.idle(8);

        overlay.showControls(vector.centerOf(leftHead), Pointing.RIGHT, 32)
                .withItem(new ItemStack(ModItems.HYDRAULIC_ROD.get()))
                .rightClick();
        overlay.showControls(vector.centerOf(rightHead), Pointing.LEFT, 32)
                .withItem(new ItemStack(ModItems.HYDRAULIC_ROD.get()))
                .rightClick();
        scene.idle(38);
        overlay.showText(55)
                .sharedText("hydraulic_rod_connect_after_assembly")
                .pointAt(vector.centerOf(rightHead))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(leftHead), Vec3.atCenterOf(rightHead).add(1.45D, 0.0D, 0.0D), 65);
        overlay.showText(55)
                .sharedText("hydraulic_rod_stretch_along_connection")
                .pointAt(Vec3.atCenterOf(rightHead).add(1.1D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(rightSection,
                new Vec3(1.45D, 0.0D, 0.0D), 55, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(62);

        overlay.showText(70)
                .sharedText("hydraulic_rod_return_force")
                .pointAt(Vec3.atCenterOf(rightHead).add(0.55D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        overlay.showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(rightHead).add(1.45D, 0.0D, 0.0D),
                Vec3.atCenterOf(rightHead), 72);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(rightSection,
                new Vec3(-1.45D, 0.0D, 0.0D), 72, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(84);
        scene.markAsFinished();
    }

    public static void hydraulicRodSettings(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "hydraulic_rod_settings", "Configuring pneumatic rods");
        scene.scaleSceneView(0.56F);
        scene.setSceneOffsetY(-2.6F);
        var world = scene.world();
        var overlay = scene.overlay();
        var effects = scene.effects();
        var select = util.select();
        var vector = util.vector();

        BlockPos bottomHead = new BlockPos(4, 2, 3);
        BlockPos topHead = new BlockPos(4, 5, 3);
        BlockPos regulator = new BlockPos(4, 1, 3);
        BlockPos shaftA = new BlockPos(4, 1, 2);
        BlockPos shaftB = new BlockPos(4, 1, 1);
        BlockPos rangeWire = new BlockPos(3, 1, 3);
        BlockPos lever = new BlockPos(2, 1, 3);
        BlockState bottomHeadState = hydraulicHead(Direction.UP);
        BlockState topHeadState = hydraulicHead(Direction.DOWN);
        Vec3 settingTextTarget = Vec3.atCenterOf(bottomHead).add(-2.0D, 2.6D, 0.0D);

        world.setBlocks(select.fromTo(3, 1, 2, 5, 1, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(bottomHead, bottomHeadState, false);

        world.setBlock(topHead, topHeadState, false);
        world.setBlocks(select.fromTo(3, 6, 2, 5, 6, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        Selection goldLoad = select.fromTo(3, 12, 2, 5, 13, 4);

        ElementLink<WorldSectionElement> bottomSection = world.showIndependentSection(
                select.fromTo(3, 1, 2, 5, 1, 4)
                        .add(select.position(bottomHead)),
                Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> topSection = world.showIndependentSection(
                select.position(topHead)
                        .add(select.fromTo(3, 6, 2, 5, 8, 4)),
                Direction.DOWN);
        scene.idle(8);
        scene.addInstruction(new CreateHydraulicRodInstruction(new HydraulicRodElement(
                bottomSection, topSection, Vec3.atCenterOf(bottomHead), Vec3.atCenterOf(topHead), bottomHeadState, false)));
        scene.idle(10);

        HydraulicConnectionHeadSettingsRenderer.DisplayState lowResistanceState = settingsState(2, false, 30, 8, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState highResistanceState = settingsState(16, false, 30, 8, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState expectedThreeState = settingsState(8, false, 30, 8, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState expectedEightState = settingsState(8, false, 80, 8, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState weakReturnState = settingsState(8, false, 80, 2, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState strongReturnState = settingsState(8, false, 80, 18, 10, 150);
        HydraulicConnectionHeadSettingsRenderer.DisplayState wideRangeState = settingsState(8, false, 80, 18, 10, 150, true);
        HydraulicConnectionHeadSettingsRenderer.DisplayState narrowRangeState = settingsState(8, false, 20, 18, 10, 20, true);

        HydraulicSettingsOverlayElement settingsOverlay = new HydraulicSettingsOverlayElement(lowResistanceState);
        scene.addInstruction(new CreateHydraulicSettingsOverlayInstruction(1800, settingsOverlay));

        overlay.showText(70)
                .sharedText("hydraulic_rod_open_gui")
                .pointAt(vector.centerOf(bottomHead))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(86);

        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay, lowResistanceState, 0));
        overlay.showText(82)
                .sharedText("hydraulic_rod_stretch_resistance_setting")
                .pointAt(settingTextTarget)
                .placeNearTarget()
                .attachKeyFrame();
        overlay.showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(topHead),
                Vec3.atCenterOf(topHead).add(0.0D, 1.6D, 0.0D), 52);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 1.6D, 0.0D), 18, SmoothMovementUtils.quadraticRiseOut()));
        scene.idle(26);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, -1.6D, 0.0D), 20, SmoothMovementUtils.quadraticRiseInOut()));
        scene.idle(34);
        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                lowResistanceState, highResistanceState, 68));
        overlay.showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(topHead),
                Vec3.atCenterOf(topHead).add(0.0D, 1.6D, 0.0D), 96);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 1.6D, 0.0D), 74, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(84);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, -1.6D, 0.0D), 74, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(88);

        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                highResistanceState, expectedThreeState, 0));
        overlay.showText(92)
                .sharedText("hydraulic_rod_expected_length")
                .pointAt(settingTextTarget)
                .placeNearTarget()
                .attachKeyFrame();
        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(bottomHead),
                Vec3.atCenterOf(topHead), 64);
        scene.idle(70);
        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                expectedThreeState, expectedEightState, 90));
        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(bottomHead),
                Vec3.atCenterOf(topHead).add(0.0D, 5.0D, 0.0D), 102);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 5.0D, 0.0D), 90, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(112);

        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                expectedEightState, weakReturnState, 0));
        world.setBlocks(goldLoad, Blocks.GOLD_BLOCK.defaultBlockState(), false);
        ElementLink<WorldSectionElement> goldSection = world.showIndependentSection(goldLoad, Direction.DOWN);
        scene.idle(10);
        overlay.showText(92)
                .sharedText("hydraulic_rod_return_force_setting")
                .pointAt(settingTextTarget)
                .placeNearTarget()
                .attachKeyFrame();
        overlay.showBigLine(PonderPalette.RED, Vec3.atCenterOf(topHead).add(0.0D, 5.0D, 0.0D),
                Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D), 92);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, -6.0D, 0.0D), 90, SmoothMovementUtils.quadraticRiseInOut()));
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(goldSection,
                new Vec3(0.0D, -6.0D, 0.0D), 90, SmoothMovementUtils.quadraticRiseInOut()));
        scene.idle(104);
        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                weakReturnState, strongReturnState, 72));
        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D),
                Vec3.atCenterOf(topHead).add(0.0D, 5.0D, 0.0D), 108);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 6.0D, 0.0D), 104, SmoothMovementUtils.cubicSmoothing()));
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(goldSection,
                new Vec3(0.0D, 6.0D, 0.0D), 104, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(120);
        world.hideIndependentSection(goldSection, Direction.UP);
        scene.idle(12);

        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                strongReturnState, wideRangeState, 0));
        world.hideIndependentSection(bottomSection, Direction.DOWN);
        scene.idle(12);
        world.setBlocks(select.fromTo(3, 1, 2, 5, 1, 4), Blocks.AIR.defaultBlockState(), false);
        world.setBlock(bottomHead, bottomHeadState, false);
        world.setBlock(regulator, regulatorState(Direction.UP, false), false);
        world.setBlock(shaftA, AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), false);
        world.setBlock(shaftB, AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), false);
        world.setBlock(rangeWire, Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(BlockStateProperties.POWER, 0), false);
        world.setBlock(lever, leverState(false), false);
        world.showIndependentSection(
                select.position(bottomHead)
                        .add(select.position(regulator))
                        .add(select.position(shaftA))
                        .add(select.position(shaftB))
                        .add(select.position(rangeWire))
                        .add(select.position(lever)),
                Direction.DOWN);
        scene.idle(18);
        Selection regulatorPower = select.position(rangeWire).add(select.position(regulator));
        Selection regulatorKinetics = select.position(regulator).add(select.position(shaftA)).add(select.position(shaftB));
        world.setKineticSpeed(regulatorKinetics, 24.0F);
        effects.rotationSpeedIndicator(shaftA);
        overlay.showText(102)
                .sharedText("hydraulic_rod_redstone_range_setting")
                .pointAt(vector.centerOf(regulator))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(38);
        world.setBlock(lever, leverState(false), false);
        world.setBlock(regulator, regulatorState(Direction.UP, false), false);
        scene.addInstruction(new RedstoneSignalInstruction(regulatorPower, 0));
        effects.indicateRedstone(rangeWire);
        overlay.showBigLine(PonderPalette.RED, Vec3.atCenterOf(topHead).add(0.0D, 5.0D, 0.0D),
                Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D), 86);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, -6.0D, 0.0D), 84, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(96);
        world.setKineticSpeed(regulatorKinetics, 192.0F);
        effects.rotationSpeedIndicator(shaftA);
        world.setBlock(lever, leverState(true), false);
        world.setBlock(regulator, regulatorState(Direction.UP, true), false);
        scene.addInstruction(new RedstoneSignalInstruction(regulatorPower, 15));
        effects.indicateRedstone(rangeWire);
        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D),
                Vec3.atCenterOf(topHead).add(0.0D, 12.0D, 0.0D), 124);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 13.0D, 0.0D), 78, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(94);

        scene.addInstruction(new SetHydraulicSettingsOverlayInstruction(settingsOverlay,
                wideRangeState, narrowRangeState, 90));
        world.setBlock(lever, leverState(false), false);
        world.setBlock(regulator, regulatorState(Direction.UP, false), false);
        scene.addInstruction(new RedstoneSignalInstruction(regulatorPower, 0));
        effects.indicateRedstone(rangeWire);
        overlay.showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(topHead).add(0.0D, 12.0D, 0.0D),
                Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D), 96);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, -13.0D, 0.0D), 96, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(110);
        world.setBlock(lever, leverState(true), false);
        world.setBlock(regulator, regulatorState(Direction.UP, true), false);
        scene.addInstruction(new RedstoneSignalInstruction(regulatorPower, 15));
        effects.indicateRedstone(rangeWire);
        overlay.showBigLine(PonderPalette.GREEN, Vec3.atCenterOf(topHead).add(0.0D, -1.0D, 0.0D),
                Vec3.atCenterOf(topHead), 44);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(topSection,
                new Vec3(0.0D, 1.0D, 0.0D), 42, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(62);
        scene.markAsFinished();
    }

    public static void hydraulicRegulatorControl(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "hydraulic_regulator_control", "Pneumatic regulator control");
        var world = scene.world();
        var overlay = scene.overlay();
        var effects = scene.effects();
        var select = util.select();
        var vector = util.vector();

        BlockPos leftHead = new BlockPos(4, 2, 3);
        BlockPos rightHead = new BlockPos(7, 2, 3);
        BlockPos regulator = new BlockPos(3, 2, 3);
        BlockPos shaftA = new BlockPos(3, 2, 2);
        BlockPos shaftB = new BlockPos(3, 2, 1);
        BlockPos wire = new BlockPos(3, 1, 4);
        BlockState leftHeadState = hydraulicHead(Direction.EAST);
        BlockState rightHeadState = hydraulicHead(Direction.WEST);

        world.setBlocks(select.layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        world.setBlocks(select.fromTo(3, 1, 2, 4, 1, 4), AllBlocks.BRASS_CASING.getDefaultState(), false);
        world.setBlocks(select.fromTo(7, 1, 2, 8, 1, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(8, 2, 3), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(regulator, ModBlocks.HYDRAULIC_REGULATOR.get().defaultBlockState()
                .setValue(HydraulicRegulatorBlock.FACING, Direction.EAST)
                .setValue(HydraulicRegulatorBlock.AXIS_ALONG_FIRST_COORDINATE, false)
                .setValue(HydraulicRegulatorBlock.POWERED, false), false);
        world.setBlock(shaftA, AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), false);
        world.setBlock(shaftB, AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), false);
        world.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(BlockStateProperties.POWER, 0), false);
        world.setBlock(leftHead, leftHeadState, false);
        world.setBlock(rightHead, rightHeadState, false);

        Selection leftStructure = select.fromTo(3, 1, 2, 4, 1, 4)
                .add(select.position(regulator))
                .add(select.position(shaftA))
                .add(select.position(shaftB))
                .add(select.position(wire))
                .add(select.position(leftHead));
        Selection rightStructure = select.fromTo(7, 1, 2, 8, 1, 4)
                .add(select.position(8, 2, 3))
                .add(select.position(rightHead));
        ElementLink<WorldSectionElement> leftSection = world.showIndependentSection(leftStructure, Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> rightSection = world.showIndependentSection(rightStructure, Direction.DOWN);
        scene.idle(8);
        scene.addInstruction(new CreateHydraulicRodInstruction(new HydraulicRodElement(
                leftSection, rightSection, Vec3.atCenterOf(leftHead), Vec3.atCenterOf(rightHead), leftHeadState, false)));
        scene.idle(10);

        world.setKineticSpeed(select.position(regulator).add(select.position(shaftA)).add(select.position(shaftB)), 16.0F);
        overlay.showText(55)
                .sharedText("regulator_place_behind_head")
                .pointAt(vector.centerOf(regulator))
                .placeNearTarget()
                .attachKeyFrame();
        effects.rotationSpeedIndicator(shaftA);
        scene.idle(64);

        scene.addInstruction(new RedstoneSignalInstruction(select.position(wire).add(select.position(regulator)), 15));
        effects.indicateRedstone(wire);
        overlay.showText(60)
                .sharedText("regulator_redstone_signal")
                .pointAt(vector.centerOf(wire))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(rightSection,
                new Vec3(1.35D, 0.0D, 0.0D), 72, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(78);

        world.setKineticSpeed(select.position(regulator).add(select.position(shaftA)).add(select.position(shaftB)), 256.0F);
        effects.rotationSpeedIndicator(shaftA);
        overlay.showText(55)
                .sharedText("regulator_speed_controls_transition")
                .pointAt(vector.centerOf(shaftA))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(new RedstoneSignalInstruction(select.position(wire).add(select.position(regulator)), 0));
        effects.indicateRedstone(wire);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(rightSection,
                new Vec3(-1.35D, 0.0D, 0.0D), 26, SmoothMovementUtils.quadraticRiseInOut()));
        scene.idle(42);

        overlay.showText(55)
                .sharedText("regulator_requires_speed")
                .pointAt(vector.centerOf(regulator))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);
        scene.markAsFinished();
    }

    public static void hydraulicHingeHead(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "hydraulic_hinge_head", "Pneumatic hinge heads");
        var world = scene.world();
        var overlay = scene.overlay();
        var select = util.select();
        var vector = util.vector();

        BlockPos leftHead = new BlockPos(3, 2, 3);
        BlockPos rightHead = new BlockPos(6, 2, 3);
        BlockState leftHeadState = hingeHead(Direction.EAST, Direction.Axis.Z);
        BlockState rightHeadState = hingeHead(Direction.WEST, Direction.Axis.Z);

        placeHydraulicRig(scene, util, leftHead, rightHead, leftHeadState, rightHeadState);
        Selection leftStructure = select.fromTo(2, 1, 2, 3, 1, 4)
                .add(select.position(2, 2, 3))
                .add(select.position(leftHead));
        Selection rightStructure = select.fromTo(6, 1, 2, 7, 1, 4)
                .add(select.position(7, 2, 3))
                .add(select.position(rightHead));
        ElementLink<WorldSectionElement> leftSection = world.showIndependentSection(leftStructure, Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> rightSection = world.showIndependentSection(rightStructure, Direction.DOWN);
        world.configureCenterOfRotation(rightSection, Vec3.atCenterOf(rightHead));
        scene.idle(8);
        scene.addInstruction(new CreateHydraulicRodInstruction(new HydraulicRodElement(
                new SectionAnchor(leftSection, Vec3.atCenterOf(leftHead), Vec3.atCenterOf(leftHead)),
                new SectionAnchor(rightSection, Vec3.atCenterOf(rightHead), Vec3.atCenterOf(rightHead)),
                leftHeadState, false)));
        scene.idle(10);

        overlay.showText(55)
                .sharedText("hinge_axis_setting")
                .pointAt(vector.centerOf(rightHead))
                .placeNearTarget()
                .attachKeyFrame();
        overlay.showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(rightHead).add(0.0D, 0.0D, -1.1D),
                Vec3.atCenterOf(rightHead).add(0.0D, 0.0D, 1.1D), 55);
        scene.idle(64);

        overlay.showText(55)
                .sharedText("hinge_allows_axis_motion")
                .pointAt(Vec3.atCenterOf(rightHead).add(0.8D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rightSection,
                new Vec3(0.0D, 0.0D, 34.0D), 46, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(54);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rightSection,
                new Vec3(0.0D, 0.0D, -68.0D), 72, SmoothMovementUtils.quadraticRiseInOut()));
        scene.idle(80);

        overlay.showText(55)
                .sharedText("hinge_rod_controls_length")
                .pointAt(vector.centerOf(rightHead))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rightSection,
                new Vec3(0.0D, 0.0D, 34.0D), 42, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(54);
        scene.markAsFinished();
    }

    public static void universalJointVariants(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "universal_joint_variants", "Universal joint variants");
        var world = scene.world();
        var overlay = scene.overlay();
        var select = util.select();
        var vector = util.vector();

        BlockPos brassLeft = new BlockPos(2, 2, 2);
        BlockPos brassRight = new BlockPos(5, 2, 2);
        BlockPos andesiteLeft = new BlockPos(2, 2, 5);
        BlockPos andesiteRight = new BlockPos(7, 2, 5);
        BlockState brassLeftState = universalJoint(Direction.EAST);
        BlockState brassRightState = universalJoint(Direction.WEST);
        BlockState andesiteLeftState = universalJoint(Direction.EAST);
        BlockState andesiteRightState = universalJoint(Direction.WEST);

        world.setBlocks(select.layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        world.setBlocks(select.fromTo(1, 1, 1, 2, 1, 3), AllBlocks.BRASS_CASING.getDefaultState(), false);
        world.setBlocks(select.fromTo(5, 1, 1, 6, 1, 3), AllBlocks.BRASS_CASING.getDefaultState(), false);
        world.setBlocks(select.fromTo(1, 1, 4, 2, 1, 6), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlocks(select.fromTo(7, 1, 4, 8, 1, 6), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(1, 2, 2), AllBlocks.BRASS_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(6, 2, 2), AllBlocks.BRASS_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(1, 2, 5), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(8, 2, 5), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(brassLeft, brassLeftState, false);
        world.setBlock(brassRight, brassRightState, false);
        world.setBlock(andesiteLeft, andesiteLeftState, false);
        world.setBlock(andesiteRight, andesiteRightState, false);

        ElementLink<WorldSectionElement> brassLeftSection = world.showIndependentSection(
                select.fromTo(1, 1, 1, 2, 1, 3)
                        .add(select.position(1, 2, 2))
                        .add(select.position(brassLeft)), Direction.DOWN);
        ElementLink<WorldSectionElement> brassRightSection = world.showIndependentSection(
                select.fromTo(5, 1, 1, 6, 1, 3)
                        .add(select.position(6, 2, 2))
                        .add(select.position(brassRight)), Direction.DOWN);
        scene.idle(5);
        ElementLink<WorldSectionElement> andesiteLeftSection = world.showIndependentSection(
                select.fromTo(1, 1, 4, 2, 1, 6)
                        .add(select.position(1, 2, 5))
                        .add(select.position(andesiteLeft)), Direction.DOWN);
        ElementLink<WorldSectionElement> andesiteRightSection = world.showIndependentSection(
                select.fromTo(7, 1, 4, 8, 1, 6)
                        .add(select.position(8, 2, 5))
                        .add(select.position(andesiteRight)), Direction.DOWN);
        scene.idle(8);
        UniversalJointLinkElement brassLink = new UniversalJointLinkElement(
                brassLeftSection, brassRightSection, Vec3.atCenterOf(brassLeft), Vec3.atCenterOf(brassRight),
                brassLeftState, JointVariant.BRASS);
        UniversalJointLinkElement andesiteLink = new UniversalJointLinkElement(
                andesiteLeftSection, andesiteRightSection, Vec3.atCenterOf(andesiteLeft), Vec3.atCenterOf(andesiteRight),
                andesiteLeftState, JointVariant.ANDESITE);
        scene.addInstruction(new CreateUniversalJointLinkInstruction(brassLink));
        scene.addInstruction(new CreateUniversalJointLinkInstruction(andesiteLink));
        scene.idle(12);

        overlay.showControls(vector.centerOf(brassLeft), Pointing.RIGHT, 45)
                .withItem(new ItemStack(ModItems.UNIVERSAL_JOINT_ROD.get()))
                .rightClick();
        overlay.showControls(vector.centerOf(andesiteLeft), Pointing.RIGHT, 45)
                .withItem(new ItemStack(ModItems.ANDESITE_UNIVERSAL_JOINT_ROD.get()))
                .rightClick();
        overlay.showText(55)
                .sharedText("universal_joint_connect")
                .pointAt(vector.centerOf(andesiteRight))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(64);

        overlay.showText(60)
                .sharedText("universal_joint_brass")
                .pointAt(vector.centerOf(brassRight).add(0.9D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(brassRightSection,
                new Vec3(2.0D, 0.0D, 0.0D), 48, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(54);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(brassRightSection,
                new Vec3(-2.0D, 0.0D, 0.0D), 42, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(42);

        overlay.showText(60)
                .sharedText("universal_joint_andesite")
                .pointAt(vector.centerOf(andesiteRight).add(0.9D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(andesiteRightSection,
                new Vec3(2.25D, 0.0D, 0.0D), 58, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(66);
        overlay.showText(60)
                .sharedText("universal_joint_compression_break")
                .pointAt(vector.centerOf(andesiteRight).add(-1.0D, 0.0D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(andesiteRightSection,
                new Vec3(-4.75D, 0.0D, 0.0D), 76, SmoothMovementUtils.quadraticRiseInOut()));
        scene.idle(84);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(andesiteRightSection,
                new Vec3(2.5D, 0.0D, 0.0D), 54, SmoothMovementUtils.cubicSmoothing()));
        scene.idle(52);

        overlay.showText(55)
                .sharedText("universal_joint_difference")
                .pointAt(vector.centerOf(andesiteLeft))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);
        scene.markAsFinished();
    }

    private static CreateSceneBuilder baseScene(SceneBuilder builder, SceneBuildingUtil util, String id, String fallbackTitle) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, fallbackTitle);
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.75F);
        scene.setSceneOffsetY(-1.0F);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        return scene;
    }

    private static void placeHydraulicRig(CreateSceneBuilder scene, SceneBuildingUtil util,
                                          BlockPos leftHead, BlockPos rightHead,
                                          BlockState leftHeadState, BlockState rightHeadState) {
        var world = scene.world();
        var select = util.select();
        world.setBlocks(select.fromTo(2, 1, 2, 3, 1, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlocks(select.fromTo(6, 1, 2, 7, 1, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(2, 2, 3), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(new BlockPos(7, 2, 3), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        world.setBlock(leftHead, leftHeadState, false);
        world.setBlock(rightHead, rightHeadState, false);
    }

    private static HydraulicConnectionHeadSettingsRenderer.DisplayState settingsState(float stretchLevel, boolean freeMode,
                                                                                       float expectedLengthTenths,
                                                                                       float returnForceLevel,
                                                                                       float redstoneMinLengthTenths,
                                                                                       float redstoneMaxLengthTenths) {
        return settingsState(stretchLevel, freeMode, expectedLengthTenths, returnForceLevel,
                redstoneMinLengthTenths, redstoneMaxLengthTenths, false);
    }

    private static HydraulicConnectionHeadSettingsRenderer.DisplayState settingsState(float stretchLevel, boolean freeMode,
                                                                                       float expectedLengthTenths,
                                                                                       float returnForceLevel,
                                                                                       float redstoneMinLengthTenths,
                                                                                       float redstoneMaxLengthTenths,
                                                                                       boolean expectedLengthControlled) {
        return new HydraulicConnectionHeadSettingsRenderer.DisplayState(
                stretchLevel,
                freeMode,
                expectedLengthTenths,
                returnForceLevel,
                redstoneMinLengthTenths,
                redstoneMaxLengthTenths,
                expectedLengthControlled,
                false,
                false);
    }

    private static BlockState hydraulicHead(Direction facing) {
        return ModBlocks.HYDRAULIC_CONNECTION_HEAD.get().defaultBlockState()
                .setValue(HydraulicConnectionHeadBlock.FACING, facing);
    }

    private static BlockState leverState(boolean powered) {
        return Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST)
                .setValue(BlockStateProperties.POWERED, powered);
    }

    private static BlockState regulatorState(Direction facing, boolean powered) {
        return ModBlocks.HYDRAULIC_REGULATOR.get().defaultBlockState()
                .setValue(HydraulicRegulatorBlock.FACING, facing)
                .setValue(HydraulicRegulatorBlock.AXIS_ALONG_FIRST_COORDINATE, false)
                .setValue(HydraulicRegulatorBlock.POWERED, powered);
    }

    private static BlockState hingeHead(Direction facing, Direction.Axis axis) {
        return ModBlocks.HYDRAULIC_HINGE_HEAD.get().defaultBlockState()
                .setValue(HydraulicConnectionHeadBlock.FACING, facing)
                .setValue(HydraulicHingeHeadBlock.HINGE_AXIS, axis);
    }

    private static BlockState universalJoint(Direction facing) {
        return ModBlocks.BRASS_UNIVERSAL_JOINT.get()
                .defaultBlockState()
                .setValue(UniversalJointBlock.FACING, facing);
    }
}
