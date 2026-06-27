package triangle.black.projection;

import mindustry.gen.Building;
import mindustry.world.Block;

public class TriPJBlock extends Block {

    public TriPJBlock(String name){
        super(name);
        buildCostMultiplier = 0;
    }

    int bitCost = 2;

    public class TriPJBlockBuilding extends Building {}

}
