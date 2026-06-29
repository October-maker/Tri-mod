package triangle.black.projection;

import arc.Core;
import arc.graphics.Color;
import mindustry.gen.Building;
import mindustry.ui.Bar;
import mindustry.world.Block;
import triangle.black.projection.communication.PJComNetwork;
import triangle.black.projection.communication.PJNetManager;
import triangle.black.projection.communication.PJNetUpdate;
import triangle.black.projection.num.PJBit;
import triangle.black.projection.num.PJCoreNumber;

public class TriPJBlock extends Block {

    public TriPJBlock(String name){
        super(name);
        buildCostMultiplier = 0;
        destructible = true;
        update = true;
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

        public static float getBitCost() {
            return bitCost;
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
                PJNetUpdate.PJComUpdate(this);
                timer = 0;
            }
        }
    }
}
