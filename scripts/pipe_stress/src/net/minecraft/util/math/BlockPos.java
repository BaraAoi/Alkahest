package net.minecraft.util.math;
public final class BlockPos {
    private final int x,y,z;
    public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
    public BlockPos offset(Direction d){return new BlockPos(x+d.x,y+d.y,z+d.z);}
    public int getManhattanDistance(BlockPos p){return Math.abs(x-p.x)+Math.abs(y-p.y)+Math.abs(z-p.z);}
    public boolean equals(Object o){return o instanceof BlockPos p && x==p.x && y==p.y && z==p.z;}
    public int hashCode(){return (x*31+y)*31+z;}
    public String toString(){return "("+x+","+y+","+z+")";}
}
