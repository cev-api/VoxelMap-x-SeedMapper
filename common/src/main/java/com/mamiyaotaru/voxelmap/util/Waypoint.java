package com.mamiyaotaru.voxelmap.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.TreeSet;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.Camera;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Waypoint implements Serializable, Comparable<Waypoint> {
    @Serial
    private static final long serialVersionUID = 8136790917447997951L;
    public String name;
    public String imageSuffix;
    public String world;
    public final TreeSet<DimensionContainer> dimensions;
    public int x;
    public int z;
    public int y;
    public String coordinateDimension;
    public boolean enabled;
    public boolean inWorld = true;
    public boolean inDimension = true;
    public float red;
    public float green;
    public float blue;
    public boolean showBeacon = false;

    public Waypoint(String name, int x, int z, int y, boolean enabled, float red, float green, float blue, String suffix, String world, TreeSet<DimensionContainer> dimensions) {
        this.name = name;
        this.x = x;
        this.z = z;
        this.y = y;
        this.enabled = enabled;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.imageSuffix = suffix.toLowerCase(Locale.ROOT);
        this.world = world;
        this.dimensions = dimensions;
        this.coordinateDimension = defaultCoordinateDimension(dimensions);
    }

    public int getUnifiedColor() {
        return getUnifiedColor(1.0f);
    }

    public int getUnifiedColor(float alpha) {
        return ARGB.colorFromFloat(alpha, red, green, blue);
    }

    public boolean isActive() {
        return this.enabled && this.inWorld && this.inDimension;
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public int getY() {
        return this.y;
    }

    public int getXInCurrentDimension() {
        return convertToCurrentDimension(this.x);
    }

    public int getZInCurrentDimension() {
        return convertToCurrentDimension(this.z);
    }

    public void setXFromCurrentDimension(int x) {
        this.x = convertFromCurrentDimension(x);
    }

    public void setZFromCurrentDimension(int z) {
        this.z = convertFromCurrentDimension(z);
    }

    private int convertToCurrentDimension(int coordinate) {
        double targetScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
        double sourceScale = getSourceCoordinateScale();
        if (sourceScale == targetScale) {
            return coordinate;
        }
        return Mth.floor(coordinate * sourceScale / targetScale);
    }

    private int convertFromCurrentDimension(int coordinate) {
        double currentScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
        double sourceScale = getSourceCoordinateScale();
        if (sourceScale == currentScale) {
            return coordinate;
        }
        return Mth.floor(coordinate * currentScale / sourceScale);
    }

    private double getSourceCoordinateScale() {
        String storageName = coordinateDimension;
        if (storageName == null || storageName.isBlank()) {
            storageName = defaultCoordinateDimension(this.dimensions);
        }
        if (storageName == null || storageName.isBlank()) {
            return 1.0D;
        }

        DimensionContainer sourceDimension = null;
        for (DimensionContainer dimension : this.dimensions) {
            if (storageName.equals(dimension.getStorageName())) {
                sourceDimension = dimension;
                break;
            }
        }
        if (sourceDimension == null) {
            sourceDimension = VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(storageName);
        }

        return sourceDimension == null || sourceDimension.type == null ? 1.0D : sourceDimension.type.coordinateScale();
    }

    private String defaultCoordinateDimension(TreeSet<DimensionContainer> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "";
        }
        return dimensions.first().getStorageName();
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int compareTo(Waypoint o) {
        double myDistance = this.getDistanceSqToEntity(VoxelConstants.getPlayer());
        double comparedDistance = o.getDistanceSqToEntity(VoxelConstants.getPlayer());
        return Double.compare(myDistance, comparedDistance);
    }

    public double getDistanceSqToEntity(Entity par1Entity) {
        double var2 = this.getXInCurrentDimension() + 0.5 - par1Entity.getX();
        double var4 = this.getY() + 0.5 - par1Entity.getY();
        double var6 = this.getZInCurrentDimension() + 0.5 - par1Entity.getZ();
        return var2 * var2 + var4 * var4 + var6 * var6;
    }

    public double getDistanceSqToCamera(Camera par1Entity) {
        Vec3 pos = par1Entity.position();
        double var2 = this.getXInCurrentDimension() + 0.5 - pos.x;
        double var4 = this.getY() + 0.5 - pos.y;
        double var6 = this.getZInCurrentDimension() + 0.5 - pos.z;
        return var2 * var2 + var4 * var4 + var6 * var6;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Waypoint otherWaypoint)) {
            return false;
        } else {
            return this.name.equals(otherWaypoint.name) && this.imageSuffix.equals(otherWaypoint.imageSuffix) && this.world.equals(otherWaypoint.world) && this.x == otherWaypoint.x && this.y == otherWaypoint.y && this.z == otherWaypoint.z && this.red == otherWaypoint.red && this.green == otherWaypoint.green && this.blue == otherWaypoint.blue && this.dimensions.equals(otherWaypoint.dimensions) && this.coordinateDimension.equals(otherWaypoint.coordinateDimension);
        }
    }
}
