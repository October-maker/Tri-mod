package triangle.black.projection.communication;

import arc.util.Time;
import mindustry.gen.Building;
import triangle.black.projection.TriPJBlock;

public class PJNetUpdate {
    // 在电力节点的 update 方法中调用
    public static void PJComUpdate(Building build) {
        if (!(build instanceof TriPJBlock.TriPJBlockBuilding)) return;

        PJComNetwork net = PJNetManager.getNetworkFor(build);
        if (net == null) return;

        // 1. 重置消耗，而不是累加
        net.PJBitCost = 0f;

        // 2. 重新计算总消耗
        for (Building b : net.nodes) {
            if (b instanceof TriPJBlock.TriPJBlockBuilding) {
                // 假设 getBitCost 是每个建筑的消耗
                net.PJBitCost += ((TriPJBlock.TriPJBlockBuilding) b).getBitCost();
            }

        }
    }
}
