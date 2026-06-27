package triangle.black.projection;

import arc.Core;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.Stat;
import triangle.black.projection.communication.PJComNetwork;
import triangle.black.projection.num.PJBit;
import triangle.black.projection.num.PJCoreNumber;

public class TriPJCore extends CoreBlock {

    static {
        PJCoreNumber.get();
    }

    public TriPJCore(String name) {
        super(name);
        buildCostMultiplier = 0;
    }

    @Override
    public boolean canBreak(Tile tile) {
        return Vars.state.teams.cores(tile.team()).size > 1;
    }

    @Override
    public boolean canReplace(Block other) {
        return other.alwaysReplace;
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        return PJCoreNumber.get() < 8;
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("PJCore",(TriPJCoreBuild e) -> new Bar(
                () -> Core.bundle.get("inBulidPJCore") + PJCoreNumber.get() + "/" + "8",
                () -> Color.valueOf("FFFFFF"),
                () -> (float) PJCoreNumber.get() / 8
        ));
        addBar("PJBit",(TriPJCoreBuild e) -> new Bar(
                () -> Core.bundle.get("PJBit") + PJBit.get() + "bits",
                () -> Color.valueOf("FFFFFF"),
                () -> 1
        ));
    }

    public class TriPJCoreBuild extends CoreBuild {

        @Override
        public void created() {
            super.created();
            PJBit.refresh();
        }

        @Override
        public void afterDestroyed() {
            super.afterDestroyed();
            PJBit.refresh();
        }

        int timer = 0;
        @Override
        public void updateTile() {
            timer++;
            if (timer >= PJComNetwork.getUpdateInterval()) {
                PJBit.refresh();
                timer = 0;
            }
        }
    }
}
