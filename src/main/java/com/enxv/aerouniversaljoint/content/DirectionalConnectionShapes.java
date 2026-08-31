package com.enxv.aerouniversaljoint.content;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class DirectionalConnectionShapes {
    private DirectionalConnectionShapes() {
    }

    static VoxelShape[] makeShapes(double shaftMinY, double shaftMaxY, double bodyMinY, double bodyMaxY,
                                   double flangeMinY, double flangeMaxY) {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length];
        for (Direction facing : Direction.values()) {
            shapes[facing.ordinal()] = Shapes.or(
                    rotateBox(facing, 5.5D, shaftMinY, 5.5D, 10.5D, shaftMaxY, 10.5D),
                    rotateBox(facing, 4.25D, bodyMinY, 4.25D, 11.75D, bodyMaxY, 11.75D),
                    rotateBox(facing, 4.0D, flangeMinY, 4.0D, 12.0D, flangeMaxY, 12.0D));
        }
        return shapes;
    }

    private static VoxelShape rotateBox(Direction facing, double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double[][] corners = {
                {minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, minZ}, {minX, maxY, maxZ},
                {maxX, minY, minZ}, {maxX, minY, maxZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}
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
