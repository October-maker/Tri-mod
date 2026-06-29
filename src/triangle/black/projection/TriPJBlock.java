package triangle.black.projection;

import mindustry.gen.Building;
import mindustry.world.Block;
import triangle.black.projection.communication.PJNetManager;
import triangle.black.projection.num.PJBit;

public class TriPJBlock extends Block {

    public TriPJBlock(String name){
        super(name);
        buildCostMultiplier = 0;
    }

    static int bitCost = 2;
    static float PJRange = 32f;

    public static class TriPJBlockBuilding extends Building {
        // 1. 定义网络 ID，默认值为 -1 表示未连接任何网络
        public static int networkId = -1;

        // 2. 提供获取网络 ID 的方法
        public static int getNetId() {
            return networkId;
        }

        // 3. 提供设置网络 ID 的方法
        public static void setNetId(int id) {
            networkId = id;
        }

        public static float getPJRange() {
            return PJRange;
        }

        @Override
        public void created() {
            super.created();
            PJNetManager.getNetworkFor(this);
        }

        @Override
        public void afterDestroyed() {
            super.afterDestroyed();
            PJNetManager.onBuildingDestroyed(this);
        }

        int timer = 0;
        @Override
        public void updateTile() {
            timer++;
            if (timer >= PJBit.getUpdateInterval()) {
                PJNetManager.getNetworkFor(this);
                timer = 0;
            }
        }
    }
}
