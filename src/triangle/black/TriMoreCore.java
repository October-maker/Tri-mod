package triangle.black;

import mindustry.Vars;
import mindustry.game.Team;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * 带数量与拆除限制的核心方块。
 *
 * <p>相比原版 {@link CoreBlock}，额外约束：
 * <ul>
 *   <li>每队最多建造 8 个核心；</li>
 *   <li>队伍只剩 1 个核心时不可拆除，避免误操作直接失去全部基地；</li>
 *   <li>放置时只允许替换标记为 alwaysReplace 的方块。</li>
 * </ul>
 */
public class TriMoreCore extends CoreBlock {

    public TriMoreCore(String name) {
        super(name);
    }

    /** 仅当队伍仍有多个核心时允许拆除（最后一个核心不可拆）。 */
    @Override
    public boolean canBreak(Tile tile) {
        return Vars.state.teams.cores(tile.team()).size > 1;
    }

    /** 仅允许替换 alwaysReplace 方块，与原版核心的放置规则保持一致。 */
    @Override
    public boolean canReplace(Block other) {
        return other.alwaysReplace;
    }

    /** 每队核心数量上限为 8 个。 */
    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        return Vars.state.teams.cores(team).size < 8;
    }
}
