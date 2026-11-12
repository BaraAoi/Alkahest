package net.alkahest.block.entity;

import com.mojang.serialization.JsonOps;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PipeDirectionTest {
    private static final int[][] STEPS = {{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};

    @Test void identifiersAndCycleAreStable() {
        var value = PipeDirection.AUTO;
        for (int i=0; i<PipeDirection.values().length; i++) {
            var encoded=PipeDirection.CODEC.encodeStart(JsonOps.INSTANCE,value).getOrThrow();
            assertEquals(value.asString(), encoded.getAsString());
            assertEquals(value, PipeDirection.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
            value=value.next();
        }
        assertEquals(PipeDirection.AUTO,value);
        assertEquals(PipeDirection.AUTO,PipeDirection.fromId(""));
        assertEquals(PipeDirection.AUTO,PipeDirection.fromId("invalid"));
    }

    @Test void directedRoutesMatchIndependentBreadthFirstSearch() {
        var random=new Random(0x9edb1a8L);
        BlockPos source=new BlockPos(-1,0,1), target=new BlockPos(4,1,2);
        for (int trial=0;trial<10000;trial++) {
            Map<BlockPos,PipeDirection> cells=new HashMap<>();
            for(int x=0;x<4;x++) for(int y=0;y<2;y++) for(int z=0;z<4;z++) {
                if(random.nextDouble() < .72) cells.put(new BlockPos(x,y,z),
                    PipeDirection.values()[random.nextInt(PipeDirection.values().length)]);
            }
            var actual=PipeRouteTree.build(source,cells::containsKey,
                (from,to)->cells.get(from).permits(from,to)).routeTo(target);
            assertEquals(reference(cells,source,target), actual.isEmpty() ? -1 : actual.size(),"trial="+trial);
            if (!actual.isEmpty()) {
                assertEquals(1,actual.getLast().getManhattanDistance(source));
                assertEquals(1,actual.getFirst().getManhattanDistance(target));
                assertTrue(independentAllows(cells.get(actual.getFirst()),actual.getFirst(),target));
                for(int i=actual.size()-1;i>0;i--)
                    assertTrue(independentAllows(cells.get(actual.get(i)),actual.get(i),actual.get(i-1)));
            }
        }
    }

    private static int reference(Map<BlockPos,PipeDirection> cells, BlockPos source, BlockPos target) {
        Map<BlockPos,Integer> seen=new HashMap<>(); var queue=new ArrayDeque<BlockPos>();
        for(var p:cells.keySet()) if(p.getManhattanDistance(source)==1) { seen.put(p,1); queue.add(p); }
        while(!queue.isEmpty()) {
            var p=queue.remove(); int length=seen.get(p); var direction=cells.get(p);
            if(p.getManhattanDistance(target)==1 && independentAllows(direction,p,target)) return length;
            for(int[] d:STEPS) {
                var next=new BlockPos(p.getX()+d[0],p.getY()+d[1],p.getZ()+d[2]);
                if(cells.containsKey(next) && !seen.containsKey(next) && independentAllows(direction,p,next)) {
                    seen.put(next,length+1); queue.add(next);
                }
            }
        }
        return -1;
    }

    private static boolean independentAllows(PipeDirection direction, BlockPos a, BlockPos b) {
        int x=b.getX()-a.getX(), y=b.getY()-a.getY(), z=b.getZ()-a.getZ();
        return switch(direction) {
            case AUTO -> true;
            case DOWN -> y==-1 && x==0 && z==0;
            case UP -> y==1 && x==0 && z==0;
            case NORTH -> z==-1 && x==0 && y==0;
            case SOUTH -> z==1 && x==0 && y==0;
            case WEST -> x==-1 && y==0 && z==0;
            case EAST -> x==1 && y==0 && z==0;
        };
    }
}
