package triangle.black;

import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;

/**
 * 内容详情对话框（替换 Vars.ui.content 用）。
 *
 * <p>目的：让<b>原版炮塔</b>（如 spectre，类为 ItemTurret、无法改类 / 无法覆写 setStats）在炮台详情页也能
 * 显示 ArmorReactiveBulletType 的自定义横向伤害表。
 *
 * <p>机制：ContentInfoDialog.show(content) 会用 {@code content.computeStats()} 建好整个统计表（含原版
 * StatValues.ammo 渲染的弹药行）后放入 ScrollPane 再挂到 cont。本类覆写 show()，在 super.show 之后：
 * <ol>
 *   <li>若当前显示的方块是 ItemTurret（含其子类，覆盖原版与模组炮塔）且其 ammoTypes 中存在
 *       ArmorReactiveBulletType，则继续；</li>
 *   <li>从 cont（arc Dialog 的 public final 内容表）取第一个子节点 ScrollPane，经 {@code getWidget()} 拿到
 *       承载统计结果的 Table；</li>
 *   <li>对该 Table 追加 {@link TriArmorStats#displayArmor}（compact=true，只补伤害表，避免与原版弹药行重复）。
 *       </li>
 * </ol>
 *
 * <p>替换方式：在 triangle.java 的 ClientLoadEvent 中把 {@code Vars.ui.content} 赋为本类实例。
 */
public class TriContentInfoDialog extends ContentInfoDialog {

    public TriContentInfoDialog(){
        super();
    }

    @Override
    public void show(UnlockableContent content){
        super.show(content);

        // 仅对炮塔（ItemTurret 及其子类，覆盖原版与模组炮塔）做注入
        if(!(content instanceof ItemTurret turret)) return;

        // 确认存在护甲反应弹药（没有则不注入，保持原版详情页原样）
        boolean hasArmor = false;
        for(var e : turret.ammoTypes){
            if(e.value instanceof ArmorReactiveBulletType){ hasArmor = true; break; }
        }
        if(!hasArmor) return;

        // cont 的第一个子节点是 ScrollPane，其 widget 就是承载 setStats 结果的统计表
        // （ContentInfoDialog.show 中先 cont.clear() 再 cont.add(pane)）
        if(!cont.getChildren().isEmpty()
            && cont.getChildren().first() instanceof ScrollPane pane
            && pane.getWidget() instanceof Table table){
            // 追加“随装甲变化的伤害表”（compact=true：原版弹药行已显示名称/说明/条件数值，只补伤害表）
            TriArmorStats.displayArmor(table, turret.ammoTypes, turret.name, true);
        }
    }
}
