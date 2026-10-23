package triangle.black.ArmorBullet;

import java.util.HashSet;
import java.util.Set;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;

/**
 * 内容详情对话框（替换 Vars.ui.content 用）。
 *
 * <p>目的：让<b>原版炮塔</b>（如 spectre，类为 ItemTurret、无法改类 / 无法覆写 setStats）在炮台详情页也能
 * 显示 ArmorReactiveBulletType 的自定义横向伤害表。
 *
 * <p>机制：ContentInfoDialog.show(content) 会用 {@code content.computeStats()} 建好整个统计表（含原版
 * StatValues.ammo 渲染的弹药行）后放入 ScrollPane 再挂到 cont。本类覆写 show()，在 super.show 之后：
 * <ol>
 *   <li>仅处理 ItemTurret 及其子类（覆盖原版与模组炮塔），并<b>跳过 TriArmorTurret</b>——后者已在 setStats
 *       内于弹药统计中正确渲染护甲伤害表，重复注入会造成两次显示；</li>
 *   <li>确认 ammoTypes 中存在 ArmorReactiveBulletType，否则保持原版详情页原样；</li>
 *   <li>遍历 cont 子节点找到承载统计表的 ScrollPane（不只取 first，更稳健），经 {@code getWidget()} 拿到
 *       Stats 表；</li>
 *   <li>定位“弹药(Stat.ammo)”统计的 inset 子表，把自定义护甲面板<b>追加到该弹药统计内部</b>（即“该弹药的
 *       详情位置”），并<b>移除原版 StatValues.ammo 渲染的、属于护甲反应弹药的原版 grayPanel</b>，避免同一弹药
 *       在原版行与自定义面板间重复显示；若找不到弹药 inset 则兜底追加到统计表末尾。</li>
 * </ol>
 *
 * <p>整段注入包在 try/catch 中并记录日志，避免任一异常破坏详情页显示或静默吞掉。
 *
 * <p>替换方式：triangle.java 中经 {@code Events.run(Trigger.update, ...)} 惰性自愈地把
 * {@code Vars.ui.content} 赋为本类实例（不再依赖 ClientLoadEvent 时机）。
 */
public class TriContentInfoDialog extends ContentInfoDialog {

    public TriContentInfoDialog(){
        super();
    }

    @Override
    public void show(UnlockableContent content){
        try{
            super.show(content);
        }catch(Throwable t){
            Log.err("TriContentInfoDialog: super.show failed", t);
            return;
        }
        try{
            inject(content);
        }catch(Throwable t){
            Log.err("TriContentInfoDialog: injection failed" + (content == null ? "" : " for " + content.name), t);
        }
    }

    /**
     * 对含护甲反应弹药的原版炮塔，把自定义护甲面板注入到“弹药”统计行内部，并移除原版对应的 grayPanel，
     * 避免同一弹药在原版行与自定义面板之间重复显示。
     * 全部操作都是幂等的：每次 show 都会重建统计表，故不会叠加。
     */
    private void inject(UnlockableContent content){
        if(!(content instanceof ItemTurret turret)) return;
        // TriArmorTurret 已在 setStats 内正确渲染护甲伤害表，跳过避免重复显示
        if(turret instanceof TriArmorTurret) return;

        // 收集护甲反应弹药的物品显示名（供识别原版 grayPanel 以移除重复行）；同时确认存在护甲反应弹药
        Set<String> armorNames = new HashSet<>();
        boolean hasArmor = false;
        for(var e : turret.ammoTypes){
            if(e.value instanceof ArmorReactiveBulletType){
                hasArmor = true;
                armorNames.add(e.key.localizedName);
            }
        }
        if(!hasArmor) return;

        // 遍历 cont 子节点找承载统计表的 ScrollPane（比只取 first 更稳健）
        ScrollPane pane = null;
        for(Element child : cont.getChildren()){
            if(child instanceof ScrollPane p){ pane = p; break; }
        }
        if(pane == null || !(pane.getWidget() instanceof Table statsTable)) return;

        // 优先注入到“弹药”统计行内部（紧跟该弹药的原版弹药行之后，即“该弹药的详情位置”）
        Table ammoInset = findAmmoInset(statsTable);
        if(ammoInset != null){
            // 移除弹药统计行里属于护甲反应弹药的原版 grayPanel，改由下方注入的完整自定义面板（含条件数值行+伤害表）统一显示
            removeVanillaArmorPanels(ammoInset, armorNames);
            TriArmorStats.displayArmor(ammoInset, turret.ammoTypes, turret.name, true);
        }else{
            // 兜底：找不到弹药统计行时追加到统计表末尾
            TriArmorStats.displayArmor(statsTable, turret.ammoTypes, turret.name, true);
        }
    }

    /**
     * 从弹药统计行中移除原版 StatValues.ammo 渲染的、属于护甲反应弹药的 grayPanel（表），
     * 避免与新注入的完整自定义面板重复。识别依据：面板内存在其物品显示名（localizedName）的 Label。
     */
    private static void removeVanillaArmorPanels(Table ammoInset, Set<String> armorNames){
        var children = ammoInset.getChildren();
        // 倒序遍历移除：被移除项后的索引不受影响
        for(int i = children.size - 1; i >= 0; i--){
            Element child = children.get(i);
            if(child instanceof Table panel && isArmorPanel(panel, armorNames)){
                panel.remove();
            }
        }
    }

    /**
     * 判断某个 grayPanel 是否属于护甲反应弹药：递归遍历面板子树（含嵌套的标题子表），
     * 任一 Label 文本（去掉颜色标记后）命中护甲弹药物品显示名即视为匹配。
     * 原版 StatValues.ammo 的物品名称 Label 嵌在标题子表内，故必须递归，不能只看直接子节点。
     */
    private static boolean isArmorPanel(Table panel, Set<String> armorNames){
        return matchesName(panel, armorNames);
    }

    /** 递归：节点自身若是 Label 且文本命中护甲名则 true；否则下钻其子节点（Group/Table）。 */
    private static boolean matchesName(Element root, Set<String> armorNames){
        if(root instanceof Label label){
            String text = label.getText().toString();
            if(text.startsWith("[") && text.indexOf(']') != -1){
                text = text.substring(text.indexOf(']') + 1);
            }
            if(armorNames.contains(text.trim())) return true;
        }
        if(root instanceof Group group){
            for(Element child : group.getChildren()){
                if(matchesName(child, armorNames)) return true;
            }
        }
        return false;
    }

    /**
     * 在统计表中定位“弹药(Stat.ammo)”统计行的 inset 子表。
     *
     * <p>ContentInfoDialog 中每个统计是一条 {@code table.table(inset -> {...})} 的子表，其内第一个 Label 文本为
     * {@code "[lightgray]" + stat.localized() + ":[] "}。此处去掉开头的颜色标记后，按“本地化弹药名 + ':'”前缀匹配，
     * 可唯一命中 Stat.ammo 行而避开 Stat.ammoCapacity / Stat.ammoUse（其名不以“弹药:”开头）。
     *
     * @return 弹药统计的 inset 子表；未找到返回 null
     */
    private static Table findAmmoInset(Table statsTable){
        String ammoPrefix = Stat.ammo.localized() + ":";
        for(Element child : statsTable.getChildren()){
            if(!(child instanceof Table inset)) continue;
            for(Element c : inset.getChildren()){
                if(c instanceof Label label){
                    String text = label.getText().toString();
                    // 去掉开头的颜色标记（如 [lightgray]）
                    if(text.startsWith("[") && text.indexOf(']') != -1){
                        text = text.substring(text.indexOf(']') + 1);
                    }
                    if(text.startsWith(ammoPrefix)){
                        return inset;
                    }
                }
            }
        }
        return null;
    }
}
