package triangle.black.projection;

import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.Stat;
import triangle.black.projection.num.PJCoreNumber;

import static arc.util.io.CRC.table;

public class TriPJCore extends CoreBlock {

    static {
        PJCoreNumber.get();
    }

    public TriPJCore(String name) {
        super(name);
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

    public class TriPJCoreBuild extends CoreBuild {

        @Override
        public void created() {
            super.created();
            PJCoreNumber.refresh();
        }

        @Override
        public void afterDestroyed() {
            super.afterDestroyed();
            PJCoreNumber.refresh();
        }

        int timer = 0;

        @Override
        public void updateTile() {
            timer++;
            if (timer >= 60) {
                PJCoreNumber.refresh();
                timer = 0;
            }
        }

//        public void build(Building build, Table table) {
//            table.update(() -> {
//                table.clear();
//                table.left();
//
//                table.table(cont -> {
//                    cont.add(String.valueOf(PJCoreNumber.get())).padRight(8).left();;
//                });
//            });
//        }
    }
}
