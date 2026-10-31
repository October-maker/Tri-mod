package triangle;

import arc.Events;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.DisposeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.mod.*;
import triangle.black.ArmorBullet.ArmorReactiveBulletType;
import triangle.black.ArmorBullet.HeshBulletType;
import triangle.black.ArmorBullet.TriArmorTurret;
import triangle.black.ArmorBullet.TriContentInfoDialog;
import triangle.black.PIBulletType;
import triangle.black.TriCIWS;
import triangle.black.TriDraw;
import triangle.black.TMail.TriGameMailButton;
import triangle.black.TMail.TriBlurBackground;
import triangle.black.TMail.TriInbox;
import triangle.black.TMail.TriModInfo;
import triangle.black.TMail.TriMainMenuButton;
import triangle.black.TMail.TriPlanetMailButton;
import triangle.black.TMail.TriSettingsDataButton;
import triangle.black.TriSetting;
import triangle.black.TriWelcome;
import triangle.content.*;
import tmi.RecipeEntryPoint;
import triangle.tmi.TriRecipeEntry;

/**
 * 三角模组主入口。
 *
 * <p>负责按依赖顺序加载各项内容，并向原版注册自定义子弹/炮塔类型；UI 入口（欢迎页、设置页、
 * 邮件按钮等）在构造函数中初始化。模组内容以 JSON 为主，Java 侧补充自定义逻辑与运行时修改。
 */
@RecipeEntryPoint(TriRecipeEntry.class)
public class triangle extends Mod{

    /**
     * 加载模组内容。
     *
     * <p>调用顺序即内容依赖顺序：物品/液体 → 工厂/核心/状态/炮塔/天气/单位 → 原版扩展 → 科技树 → 邮件。
     * 其中若干自定义类型先注册进 {@link ClassMap}，供 JSON 内容按类名解析。
     */
    @Override
    public void loadContent(){
        // 注册自定义子弹类型到 ClassMap，使其可在 JSON 内容（如炮塔）中使用：JSON 里写 "type": "ArmorReactiveBulletType"，引擎经 ClassMap 按类名解析并无参实例化。
        ClassMap.classes.put("ArmorReactiveBulletType", ArmorReactiveBulletType.class);
        ClassMap.classes.put("HeshBulletType", HeshBulletType.class);
        // 注册自定义炮塔类，用于在炮台详情页隐藏护甲反应弹药的默认伤害/穿甲并追加自定义横向伤害表。
        ClassMap.classes.put("TriArmorTurret", TriArmorTurret.class);
        // CIWS 近防炮：以敌方子弹为目标的 ItemTurret；近炸拦截弹作为其可搭配弹药。
        ClassMap.classes.put("TriCIWS", TriCIWS.class);
        ClassMap.classes.put("PIBulletType", PIBulletType.class);
        // 旧名称兼容别名（如旧 JSON/存档仍使用 ProximityInterceptorBulletType，可取消下一行注释）：
        // ClassMap.classes.put("ProximityInterceptorBulletType", PIBulletType.class);

        TriItems.load();
        TriLiquids.load();
        TriFactory.load();
        TriCore.load();
        TriStatus.load();
        TriTurret.load();
        TriWeather.load();
        TriUnits.load();
        ExSerBlocks.load();
        SerTriTechTree.load();
        TriInbox.registerDefaults();
        TriModInfo.init();
        Vars.renderer.minZoom =0.3f;
        Vars.renderer.maxZoom = 20.0f;
        float MiZ = Vars.renderer.minZoom;
        float MaZ = Vars.renderer.maxZoom;
        Log.info("Min Zoon = " + MiZ);
        Log.info("Max Zoon = " + MaZ);
        Log.info("Load some triangle content.");
    }

    /**
     * 初始化 UI 入口与运行时钩子。
     *
     * <p>此时仅能注册事件监听；涉及游戏 UI 的实际操作（如替换内容详情对话框）必须延迟到
     * 运行期，并做好空值保护。
     */
    public triangle(){
        // 启动欢迎弹窗（入场动画 + 欢迎内容）与设置页分别由 TriWelcome / TriSetting 负责
        TriWelcome.init();
        TriSetting.init();
        // 战役星球界面“收件箱”按钮
        TriPlanetMailButton.init();
        // 主菜单 TMail 入口（固定模组资讯）
        TriMainMenuButton.init();
        // 设置→数据页“重置战役信件”按钮
        TriSettingsDataButton.init();
        // 战役地图内“收到新邮件”提示（解锁时弹黑框 3 秒）
        TriInbox.hookNewMailToast();
        Events.on(DisposeEvent.class, e -> {
            TriDraw.dispose();
            TriBlurBackground.dispose();
        });
        TriGameMailButton.init();
        // 惰性自愈安装：每帧检查并把内容详情对话框替换为 TriContentInfoDialog，不再依赖 ClientLoadEvent 时机
        // （Vars.ui 可能尚未初始化，故带空保护；替换成功后 instanceof 判空使其退化为空操作）
        // 使原版炮塔（无法改类/覆写 setStats）也能在详情页对 ArmorReactiveBulletType 追加自定义横向伤害表。
        Events.run(Trigger.update, () -> {
            if(Vars.ui != null && Vars.ui.content != null && !(Vars.ui.content instanceof TriContentInfoDialog)){
                Vars.ui.content = new TriContentInfoDialog();
            }
        });
    }
}
