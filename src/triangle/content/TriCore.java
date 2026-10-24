package triangle.content;

import triangle.black.TriMoreCore;

/**
 * 三角模组核心方块的注册入口。
 *
 * <p>核心的尺寸、生命值、物品容量、建造需求等数值由
 * {@code assets/content/blocks/core/watchtowerCore.json} 按同名覆盖；
 * 这里只负责用自定义类型 {@link TriMoreCore} 创建实例，以启用核心数量与拆除限制。
 */
public class TriCore {

    /** 前哨核心：限制玩家可建造的核心数量，并禁止拆除队伍最后一个核心。 */
    public static TriMoreCore watchtowerCore;

    /** 创建核心方块（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        watchtowerCore = new TriMoreCore("watchtowerCore"){{}};
    }
}
