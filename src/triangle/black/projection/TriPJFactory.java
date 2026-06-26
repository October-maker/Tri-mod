package triangle.black.projection;

import arc.Core;
import arc.graphics.Color;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import triangle.black.projection.num.PJCoreNumber;
import triangle.black.projection.num.PJFactoryNumber;

public class TriPJFactory extends Block {

    static {
        PJFactoryNumber.get();
    }

    public TriPJFactory(String name){
        super(name);
        buildCostMultiplier = 0;
    }

    public int FacMaxbuild() {
        if (PJCoreNumber.get() < 3) {
            return PJCoreNumber.get() * 5;
        }else{
            return 20;
        }
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        return PJFactoryNumber.get() < FacMaxbuild();
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("PJFactory",(TriPJFactory.TriPJFactoryBuild e) -> new Bar(
                () -> Core.bundle.get("inBulidPJFactory") + PJFactoryNumber.get() + "/" + FacMaxbuild(),
                () -> Color.valueOf("FFFFFF"),
                () -> (float) PJFactoryNumber.get() / FacMaxbuild()
        ));
    }

    public class TriPJFactoryBuild extends Building{

        @Override
        public void created() {
            super.created();
            PJFactoryNumber.refresh();
        }

        @Override
        public void afterDestroyed() {
            super.afterDestroyed();
            PJFactoryNumber.refresh();
        }

        int timer = 0;
        @Override
        public void updateTile() {
            timer++;
            if (timer >= 60) {
                PJFactoryNumber.refresh();
                timer = 0;
            }
        }
    }
}
