package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.ponder.SmoothMovementUtils;
import dev.simulated_team.simulated.ponder.instructions.CustomAnimateWorldSectionInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.scene.SelectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class KineticConversionBearingScenes {
    private KineticConversionBearingScenes() {
    }

    public static void conversionOutput(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = baseScene(builder, util, "kinetic_conversion_bearing_output", "Kinetic conversion bearing");
        var world = scene.world();
        var overlay = scene.overlay();
        var effects = scene.effects();
        var select = util.select();
        var vector = util.vector();

        BlockPos bearing = new BlockPos(4, 2, 3);
        Selection outputDrive = outputDrive(select);
        Selection rotor = rotor(select);

        placeBearingRig(scene, util, false);
        world.showSection(select.position(bearing), Direction.DOWN);
        scene.idle(6);
        world.showSection(outputDrive, Direction.WEST);
        scene.idle(8);
        ElementLink<WorldSectionElement> rotorSection = world.showIndependentSection(rotor, Direction.EAST);
        world.configureCenterOfRotation(rotorSection, Vec3.atCenterOf(bearing));
        scene.idle(10);

        overlay.showControls(vector.centerOf(bearing), Pointing.RIGHT, 45)
                .rightClick();
        overlay.showText(65)
                .sharedText("damping_bearing_assemble")
                .pointAt(vector.centerOf(bearing))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        world.modifyBlock(bearing, state -> state.setValue(SwivelBearingBlock.ASSEMBLED, true), false);
        overlay.showBigLine(PonderPalette.INPUT, Vec3.atCenterOf(new BlockPos(6, 2, 3)), Vec3.atCenterOf(bearing), 76);
        overlay.showBigLine(PonderPalette.OUTPUT, Vec3.atCenterOf(bearing), Vec3.atCenterOf(new BlockPos(1, 2, 3)), 76);
        overlay.showText(70)
                .sharedText("damping_bearing_reads_physics")
                .pointAt(Vec3.atCenterOf(new BlockPos(6, 2, 3)))
                .placeNearTarget()
                .attachKeyFrame();
        world.setKineticSpeed(outputDrive, 96.0F);
        effects.rotationSpeedIndicator(new BlockPos(2, 2, 3));
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rotorSection,
                new Vec3(360.0D, 0.0D, 0.0D), 92, SmoothMovementUtils.linear()));
        scene.idle(102);

        overlay.showText(70)
                .sharedText("damping_bearing_rear_output")
                .pointAt(vector.centerOf(new BlockPos(2, 2, 3)))
                .placeNearTarget()
                .attachKeyFrame();
        effects.rotationSpeedIndicator(new BlockPos(1, 2, 3));
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rotorSection,
                new Vec3(420.0D, 0.0D, 0.0D), 96, SmoothMovementUtils.linear()));
        scene.idle(108);

        overlay.showControls(vector.centerOf(bearing), Pointing.DOWN, 54)
                .whileSneaking()
                .rightClick();
        overlay.showText(78)
                .sharedText("damping_bearing_resistance")
                .pointAt(vector.centerOf(bearing))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(28);
        world.modifyBlockEntity(bearing, DampingStressBearingBlockEntity.class, be -> be.setResistanceValue(160));
        effects.indicateSuccess(bearing);
        scene.idle(62);

        overlay.showText(80)
                .sharedText("damping_bearing_damped_bearing")
                .pointAt(vector.centerOf(new BlockPos(2, 2, 3)))
                .placeNearTarget()
                .attachKeyFrame();
        world.setKineticSpeed(outputDrive, 72.0F);
        effects.rotationSpeedIndicator(new BlockPos(2, 2, 3));
        scene.addInstruction(CustomAnimateWorldSectionInstruction.rotate(rotorSection,
                new Vec3(260.0D, 0.0D, 0.0D), 108, SmoothMovementUtils.quinticSmoothing()));
        scene.idle(118);
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

    private static void placeBearingRig(CreateSceneBuilder scene, SceneBuildingUtil util, boolean assembled) {
        var world = scene.world();
        var select = util.select();
        world.setBlock(new BlockPos(4, 2, 3), bearingState(assembled), false);
        world.setBlocks(outputDrive(select), shaft(Direction.Axis.X), false);

        world.setBlocks(rotor(select), Blocks.IRON_BLOCK.defaultBlockState(), false);
    }

    private static Selection outputDrive(SelectionUtil select) {
        return select.fromTo(1, 2, 3, 3, 2, 3);
    }

    private static Selection rotor(SelectionUtil select) {
        return select.fromTo(5, 1, 2, 7, 3, 4);
    }

    private static BlockState bearingState(boolean assembled) {
        return ModBlocks.DAMPING_STRESS_BEARING.get()
                .defaultBlockState()
                .setValue(SwivelBearingBlock.FACING, Direction.EAST)
                .setValue(SwivelBearingBlock.ASSEMBLED, assembled);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, axis);
    }
}
