package triangle.black.projection.communication;

import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.gen.Building;
import triangle.black.projection.TriPJBlock;

public class PJComNetwork {
    public final int id;
    public final Seq<Building> nodes = new Seq<>();
    public float totalProduction = 0f;
    public float totalConsumption = 0f;

    public PJComNetwork(int id) {
        this.id = id;
    }

    // 添加建筑到网络（自动去重）
    public void add(Building build) {
        if (!nodes.contains(build)) nodes.add(build);
    }

    // 从网络移除建筑
    public void remove(Building build) {
        nodes.remove(build);
    }
}
