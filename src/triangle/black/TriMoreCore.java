package triangle.black;

import mindustry.Vars;
import mindustry.game.Team;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

public class TriMoreCore extends CoreBlock {
    public TriMoreCore(String name) {
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
        return Vars.state.teams.cores(team).size < 8;
    }
}
