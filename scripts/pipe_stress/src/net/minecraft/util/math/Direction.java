package net.minecraft.util.math;
public enum Direction {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
    final int x,y,z; Direction(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
}
