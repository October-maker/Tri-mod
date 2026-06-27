package triangle.black.projection;

import mindustry.gen.Building;
import mindustry.world.Block;
import triangle.black.projection.num.PJBlockNumber;

public class TriPJBlock extends Block {

    static {
        PJBlockNumber.get();
    }

    public TriPJBlock(String name){
        super(name);
        buildCostMultiplier = 0;
    }

    public class TriPJBlockBuilding extends Building {}

//    public int FacMaxbuild() {
//        if (PJCoreNumber.get() < 3) {
//            return PJCoreNumber.get() * 5;
//        }else{
//            return 20;
//        }
//    }

//    @Override
//    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
//        return PJBlockNumber.get() < FacMaxbuild();
//    }

//    @Override
//    public void setBars() {
//        super.setBars();
//        addBar("PJFactory",(TriPJBlock.TriPJFactoryBuild e) -> new Bar(
//                () -> Core.bundle.get("inBulidPJFactory") + PJBlockNumber.get() + "/" + FacMaxbuild(),
//                () -> Color.valueOf("FFFFFF"),
//                () -> (float) PJBlockNumber.get() / FacMaxbuild()
//        ));
//    }

//    public class TriPJFactoryBuild extends Building{
//
//        @Override
//        public void created() {
//            super.created();
//            PJBlockNumber.refresh();
//        }
//
//        @Override
//        public void afterDestroyed() {
//            super.afterDestroyed();
//            PJBlockNumber.refresh();
//        }
//
//        int timer = 0;
//        @Override
//        public void updateTile() {
//            timer++;
//            if (timer >= 60) {
//                PJBlockNumber.refresh();
//                timer = 0;
//            }
//        }
//    }
}
