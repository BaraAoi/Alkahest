package net.alkahest.block.entity;
import java.util.*;
import net.minecraft.util.math.BlockPos;
/** External stress harness: actual PipeRouteTree with minimal coordinate stubs, not a Minecraft simulation. */
public class RouteFuzz {
    static final int N=6, SIZE=N*N*N;
    static int index(int x,int y,int z){return x+N*y+N*N*z;}
    static int x(int i){return i%N;} static int y(int i){return i/N%N;} static int z(int i){return i/(N*N);}
    static boolean adjacent(int a,int b){return Math.abs(x(a)-x(b))+Math.abs(y(a)-y(b))+Math.abs(z(a)-z(b))==1;}
    static int reference(boolean[] cells,int source,int target){
        int[] queue=new int[SIZE], distance=new int[SIZE]; Arrays.fill(distance,-1); int head=0,tail=0;
        for(int i=0;i<SIZE;i++) if(cells[i]&&adjacent(source,i)){queue[tail++]=i;distance[i]=1;}
        while(head<tail){int p=queue[head++]; if(adjacent(p,target))return distance[p];
            for(int j=0;j<SIZE;j++)if(cells[j]&&distance[j]<0&&adjacent(p,j)){queue[tail++]=j;distance[j]=distance[p]+1;}
        }return -1;
    }
    public static void main(String[] args)throws Exception{
        long cases=Long.parseLong(args[0]), count=0;
        if(cases<=0) throw new IllegalArgumentException("Case count must be positive");
        long seed=args.length>1 ? Long.decode(args[1]) : 0x5a17ab1e2026L;
        Random rnd=new Random(seed);
        while(count<cases){
            boolean[] cells=new boolean[SIZE]; int source=rnd.nextInt(SIZE),target;
            do target=rnd.nextInt(SIZE); while(target==source);
            double density=.05+rnd.nextDouble()*.9;
            for(int i=0;i<SIZE;i++) cells[i]=i!=source&&i!=target&&rnd.nextDouble()<density;
            BlockPos a=new BlockPos(x(source),y(source),z(source));
            BlockPos b=new BlockPos(x(target),y(target),z(target));
            var route=PipeRouteTree.build(a,p->p.getX()>=0&&p.getX()<N&&p.getY()>=0&&p.getY()<N&&p.getZ()>=0&&p.getZ()<N&&cells[index(p.getX(),p.getY(),p.getZ())]).routeTo(b);
            int expect=reference(cells,source,target);
            if((route.isEmpty()?-1:route.size())!=expect)throw new AssertionError("length case "+count+" expected "+expect+" actual "+route);
            for(int i=0;i<route.size();i++){
                BlockPos p=route.get(i);
                if(!cells[index(p.getX(),p.getY(),p.getZ())] || (i>0&&p.getManhattanDistance(route.get(i-1))!=1))throw new AssertionError("invalid path "+count);
            }
            if(!route.isEmpty()&&(route.getFirst().getManhattanDistance(b)!=1||route.getLast().getManhattanDistance(a)!=1))throw new AssertionError("endpoint "+count);
            count++;
            if(count%20000==0) System.out.println("Verified random 3D layouts: "+count);
        }
        System.out.println("PASS random 3D layouts: "+count+", seed="+seed);
    }
}
