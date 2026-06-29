package triangle.black.projection;

import mindustry.gen.Building;
import mindustry.world.Block;
import triangle.black.projection.communication.PJComNetwork;

public class TriPJBlock extends Block {

    public TriPJBlock(String name){
        super(name);
        buildCostMultiplier = 0;
    }

    int bitCost = 2;

    public class TriPJBlockBuilding extends Building {
        // 1. 定义网络 ID，默认值为 -1 表示未连接任何网络
        private int networkId = -1;

        // 2. 提供获取网络 ID 的方法
        public int getNetworkId() {
            return this.networkId;
        }

        // 3. 提供设置网络 ID 的方法
        public void setNetworkId(int id) {
            this.networkId = id;
        }

        // 4. 关键：处理建筑被破坏时的清理逻辑
//        @Override
//        public void onRemoved() {
//            super.onRemoved();
//            // 触发网络管理器，将该建筑从网络中移除
//            PJComNetwork.PJNetManager.onBuildingDestroyed(this);
//        }
    }

}
