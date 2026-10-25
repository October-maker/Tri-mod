package triangle.black;

import arc.*;
import arc.scene.style.TextureRegionDrawable;
import mindustry.*;
import mindustry.game.EventType.*;

/**
 * 三角模组的设置逻辑集中入口：仅在游戏设置界面新增“三角”分类页
 * （含“启动时显示欢迎界面”开关，与 TriWelcome 共用 STARTUP_DIALOG_KEY，状态保持同步）。
 * 启动欢迎弹窗（入场动画 + 欢迎内容）由 TriWelcome 负责，与此处解耦。
 */
public class TriSetting{

    /** Tmail 动画速度挡位（1~5，默认3）。 */
    public static final String TMAIL_SPEED_KEY = "tri.tmail.animSpeed";

    /** 是否已向设置界面添加“三角”设置页（防止 ClientLoadEvent 重复触发导致重复添加）。 */
    private static boolean settingsAdded = false;

    private TriSetting(){}

    /** 1~5 挡位对应的动画倍率。 */
    public static float tmailAnimMultiplierForStep(int step){
        switch(Math.max(1, Math.min(5, step))){
            case 1: return 0.5f;
            case 2: return 0.75f;
            case 4: return 1.25f;
            case 5: return 1.5f;
            default: return 1f;
        }
    }

    /** 当前 Tmail 动画倍率。 */
    public static float tmailAnimMultiplier(){
        return tmailAnimMultiplierForStep(Core.settings.getInt(TMAIL_SPEED_KEY, 3));
    }

    /**
     * 注册“三角”设置页。
     * 在 {@code triangle} 主类构造中调用一次即可。
     */
    public static void init(){
        // 在游戏设置界面新增“三角”设置页，含“启动时显示欢迎界面”开关，可随时重新打开弹窗
        Events.on(ClientLoadEvent.class, e -> {
            if(settingsAdded) return;
            settingsAdded = true;
            // 设置→三角 分类：图标置于分类标题前（与原版“游戏”等分类前的齿轮图标排版一致）
            Vars.ui.settings.addCategory(Core.bundle.get("triSettings"),
                new TextureRegionDrawable(TriDraw.tex("TriSetting")), t -> {
                // checkPref 的第一参既是“设置存储键”又是“bundle 显示文案键”。
                // STARTUP_DIALOG_KEY(=tri.startupDialogToggle) 即 bundle 键，故显示文本自动本地化；
                // 且与弹窗内勾选框读写同一个键，状态保持同步（键为跨语言稳定的标识符）。
                t.checkPref(TriWelcome.STARTUP_DIALOG_KEY, true);
                // Tmail 动画速度：5 挡滑条，默认第 3 挡（1.0x）
                t.sliderPref(TMAIL_SPEED_KEY, 3, 1, 5, 1,
                    value -> String.format(java.util.Locale.ROOT, "%.2f×",
                        tmailAnimMultiplierForStep(value)));
            });
        });
    }
}
