package triangle.black;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Label;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;
import triangle.triangle;

/**
 * 三角模组统一绘制 / UI 工具集。
 *
 * <p>汇集多个界面共用的复杂绘制方法：
 * <ul>
 *   <li>模组 UI 图标纹理加载：Mindustry 打包模组 sprites 时，atlas 键为
 *       "&lt;modName&gt;-&lt;文件名无扩展名&gt;"（如 mod.hjson 的 name 为 "triangle" 时，
 *       sprites/ui/Tmail.png 的键为 "triangle-Tmail"）。本类依次尝试：直接文件名键 → ui/ 路径键 →
 *       模组名前缀键（引擎标准）→ 从模组文件直接读取（root/file 多候选路径），
 *       成功后以引擎标准键注册进 atlas。</li>
 *   <li>白色描边圆角背景：把任意 Drawable 包装为带 #FFFFFF 圆角描边的版本，按 base 缓存复用。</li>
 *   <li>打字机 Label：挂在宿主 update 上按字符/秒刷新文本。</li>
 * </ul>
 */
public class TriDraw{

    /** 文件兜底加载的缓存（atlas 收录的不在此缓存，由 atlas 统一管理）。 */
    private static final ObjectMap<String, TextureRegion> fileCache = new ObjectMap<>();
    /** 模组名前缀（含末尾 "-"），惰性取模组 meta.name。 */
    private static String modPrefix;

    private TriDraw(){}

    /** 获取模组 sprites/ui/ 下的图标；name 为文件名（不含扩展名，如 "Tmail"）。 */
    public static TextureRegion tex(String name){
        // 1) 直接文件名键（原版 atlas 键形式）
        if(Core.atlas.has(name)) return Core.atlas.find(name);
        // 2) ui/ 路径键
        if(Core.atlas.has("ui/" + name)) return Core.atlas.find("ui/" + name);
        // 3) 模组名前缀键：引擎打包模组 sprites 的标准键 = "<modName>-<file>"
        String prefixed = prefix() + name;
        if(Core.atlas.has(prefixed)) return Core.atlas.find(prefixed);

        TextureRegion cached = fileCache.get(name);
        if(cached != null) return cached;

        // 4) 兜底：从模组 jar/目录直接读取 png，成功后以引擎标准键注册进 atlas
        try{
            TextureRegion region = loadFile(name);
            if(region != null){
                fileCache.put(name, region);
                try{ Core.atlas.addRegion(prefixed, region); }catch(Throwable ignored){}
                return region;
            }
        }catch(Throwable ignored){
            // 文件缺失或解析失败：回退白色占位，不崩溃
        }
        return Core.atlas.find("white");
    }

    /** 模组名前缀（mod.hjson 的 name 字段 + "-"）；异常时兜底 "triangle-"。 */
    private static String prefix(){
        if(modPrefix == null){
            try{
                LoadedMod mod = Vars.mods.getMod(triangle.class);
                modPrefix = mod != null && mod.meta != null ? mod.meta.name + "-" : "triangle-";
            }catch(Throwable ignored){
                modPrefix = "triangle-";
            }
        }
        return modPrefix;
    }

    /** 从模组资源中读取 png；依次尝试 root / file 两种句柄下的候选路径。 */
    private static TextureRegion loadFile(String name){
        LoadedMod mod = Vars.mods.getMod(triangle.class);
        if(mod == null) return null;

        Fi[] candidates = {
            mod.root.child("sprites/ui/" + name + ".png"),
            mod.file.child("sprites/ui/" + name + ".png"),
            mod.root.child("ui/" + name + ".png"),
            mod.file.child("ui/" + name + ".png"),
        };
        for(Fi f : candidates){
            if(f.exists()){
                Texture tex = new Texture(new Pixmap(f));
                return new TextureRegion(tex);
            }
        }
        // 兜底：允许 sprites/ui/ 下的子目录资源（如 sprites/ui/Tmail/TmailLogo.png）
        for(Fi base : new Fi[]{mod.root, mod.file}){
            try{
                Fi ui = base.child("sprites/ui");
                if(!ui.exists()) continue;
                Fi found = ui.findAll(f -> f.name().equals(name + ".png")).first();
                if(found != null){
                    Texture tex = new Texture(new Pixmap(found));
                    return new TextureRegion(tex);
                }
            }catch(Throwable ignored){
                // 压缩包句柄可能不支持递归遍历，继续尝试下一个来源
            }
        }
        return null;
    }

    /** 将 Drawable 包装为带白色（#FFFFFF）描边的版本，按 base 缓存复用，避免每次 new 匿名对象。 */
    private static final ObjectMap<Drawable, Drawable> outlinedCache = new ObjectMap<>();

    public static Drawable whiteOutlined(Drawable base){
        Drawable cached = outlinedCache.get(base);
        if(cached == null){
            cached = new Drawable(){
                @Override
                public void draw(float x, float y, float width, float height){
                    base.draw(x, y, width, height);
                    stroke(x, y, width, height);
                }

                @Override
                public void draw(float x, float y, float width, float height, float color, float u1, float v1, float u2, float v2){
                    base.draw(x, y, width, height, color, u1, v1, u2, v2);
                    stroke(x, y, width, height);
                }

                private void stroke(float x, float y, float width, float height){
                    Draw.color(Color.white);
                    Lines.stroke(2.5f);
                    roundRectStroke(x, y, width, height, 8f);
                    Draw.reset();
                }

                @Override public float getLeftWidth(){ return base.getLeftWidth(); }
                @Override public void setLeftWidth(float leftWidth){ base.setLeftWidth(leftWidth); }
                @Override public float getRightWidth(){ return base.getRightWidth(); }
                @Override public void setRightWidth(float rightWidth){ base.setRightWidth(rightWidth); }
                @Override public float getTopHeight(){ return base.getTopHeight(); }
                @Override public void setTopHeight(float topHeight){ base.setTopHeight(topHeight); }
                @Override public float getBottomHeight(){ return base.getBottomHeight(); }
                @Override public void setBottomHeight(float bottomHeight){ base.setBottomHeight(bottomHeight); }
                @Override public float getMinWidth(){ return base.getMinWidth(); }
                @Override public void setMinWidth(float minWidth){ base.setMinWidth(minWidth); }
                @Override public float getMinHeight(){ return base.getMinHeight(); }
                @Override public void setMinHeight(float minHeight){ base.setMinHeight(minHeight); }
            };
            outlinedCache.put(base, cached);
        }
        return cached;
    }

    /** 绘制圆角矩形描边（四个角用弧线采样，直边由相邻弧线端点自然连成）。 */
    private static void roundRectStroke(float x, float y, float w, float h, float r){
        r = Math.min(r, Math.min(w, h) / 2f);
        int segs = 8; // 每个角四分一圆弧的采样段数
        FloatSeq pts = new FloatSeq();
        // 顺时针依次画出四个圆角
        addArc(pts, x + w - r, y + r, r, 270f, 360f, segs); // 右下角
        addArc(pts, x + w - r, y + h - r, r, 0f, 90f, segs); // 右上角
        addArc(pts, x + r, y + h - r, r, 90f, 180f, segs); // 左上角
        addArc(pts, x + r, y + r, r, 180f, 270f, segs); // 左下角
        Lines.polyline(pts, true);
    }

    /** 向点集追加一段圆弧上的采样点（角度为度，0° 指向 +x，顺时针增加）。 */
    private static void addArc(FloatSeq pts, float cx, float cy, float r, float a0, float a1, int segs){
        for(int i = 0; i <= segs; i++){
            float a = Mathf.lerp(a0, a1, i / (float) segs);
            pts.add(cx + Mathf.cosDeg(a) * r);
            pts.add(cy + Mathf.sinDeg(a) * r);
        }
    }

    /** 打字机（简单版）：居中对齐，调用方自行加入布局。 */
    public static Label typewriter(Element host, String text, float speed){
        return typewriter(host, text, speed, Align.center, null, null);
    }

    /** 打字机：挂在宿主 update 上按字符/秒刷新文本。
     *  @param host 场景中的宿主（其 update 回调将被本方法接管；host 始终在场景中 update 才会执行）
     *  @param text 完整文本
     *  @param speed 字符/秒
     *  @param align 文本对齐（arc.util.Align）
     *  @param onFirstChar 首字出现时执行一次（如动态加入布局；可为 null）
     *  @param onFinish 播放完成时执行一次（如动态加入按钮；可为 null）
     *  @return 打字机 Label（初始为空文本，由调用方决定何时加入布局） */
    public static Label typewriter(Element host, String text, float speed, int align, Runnable onFirstChar, Runnable onFinish){
        Label typed = new Label("");
        typed.touchable = Touchable.disabled;
        typed.setWrap(true);
        typed.setAlignment(align);
        int[] last = {0};
        boolean[] firstDone = {false};
        boolean[] finishDone = {false};
        float start = Time.time;
        host.update(() -> {
            int shown = Math.min(text.length(), (int)((Time.time - start) * speed));
            if(shown != last[0]){
                last[0] = shown;
                typed.setText(text.substring(0, shown));
                if(shown > 0 && !firstDone[0]){
                    firstDone[0] = true;
                    if(onFirstChar != null) onFirstChar.run();
                }
            }
            if(shown >= text.length() && !finishDone[0]){
                finishDone[0] = true;
                if(onFinish != null) onFinish.run();
            }
        });
        return typed;
    }
}
