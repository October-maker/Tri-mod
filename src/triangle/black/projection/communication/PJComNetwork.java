package triangle.black.projection.communication;

import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import triangle.black.projection.TriPJBlock;

public class PJComNetwork {
    public Seq<Building> netClear(Seq<Building> BlockInNet, float x, float y, float range) {
        if(BlockInNet != null) {
            BlockInNet.clear();
        }

        float rangeSq = range * range;
        for (Building build : Groups.build) {
            if (!(build.block instanceof TriPJBlock)) continue;

            if (build.dst2(x, y) <= rangeSq) {
                if (BlockInNet != null) {
                    BlockInNet.add(build);
                }
            }
        }

        return BlockInNet;
    }
}
