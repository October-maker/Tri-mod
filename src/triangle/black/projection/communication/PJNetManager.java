package triangle.black.projection.communication;

import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.gen.Building;
import triangle.black.projection.TriPJBlock;

import static triangle.black.projection.communication.PJNetClear.netClear;

public class PJNetManager {
    private static final IntMap<PJComNetwork> networks = new IntMap<>();
    private static int nextNetworkId = 1;

    // 获取建筑所属网络（若无则创建新网络）
    public static PJComNetwork getNetworkFor(Building build) {
        // 1. 通过建筑缓存快速获取网络ID（避免重复搜索）
        int netId = TriPJBlock.TriPJBlockBuilding.getNetId();
        if (netId != -1 && networks.containsKey(netId)) {
            return networks.get(netId);
        }

        // 2. 未缓存则执行网络搜索（调用的 netClear）
        Seq<Building> connected = new Seq<>();
        netClear(connected, build.x, build.y, TriPJBlock.TriPJBlockBuilding.getPJRange()); // 搜索范围PJRange默认为32f像素

        // 3. 合并相邻网络（关键优化！）
        PJComNetwork merged = mergeNetworks(connected);
        merged.add(build);

        // 5. 为所有相连的建筑分配新的网络 ID
        for (Building b : connected) {
            if (b instanceof TriPJBlock.TriPJBlockBuilding) {
                TriPJBlock.TriPJBlockBuilding.setNetId(merged.id);
            }
        }
        TriPJBlock.TriPJBlockBuilding.setNetId(merged.id);

        return merged;
    }

    // 合并多个建筑所属的网络（避免碎片化）
    private static PJComNetwork mergeNetworks(Seq<Building> buildings) {
        PJComNetwork main = null;
        Seq<PJComNetwork> toMerge = new Seq<>();

        for (Building b : buildings) {
            PJComNetwork net = networks.get(TriPJBlock.TriPJBlockBuilding.getNetId());
            if (net != null) {
                if (main == null) main = net;
                else if (main != net) toMerge.add(net);
            }
        }

        // 合并所有子网络到主网络
        if (main == null) {
            main = new PJComNetwork(nextNetworkId++);
            networks.put(main.id, main);
        }

        for (PJComNetwork net : toMerge) {
            main.nodes.addAll(net.nodes);
            networks.remove(net.id);
        }

        return main;
    }

    // 建筑被破坏时清理网络
    public static void onBuildingDestroyed(Building build) {
        PJComNetwork net = networks.get(TriPJBlock.TriPJBlockBuilding.getNetId());
        if (net != null) {
            net.remove(build);
            // 网络为空则销毁
            if (net.nodes.isEmpty()) networks.remove(net.id);
        }
    }
}
