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
import arc.scene.Action;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Label;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.util.Align;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;
import triangle.triangle;

/**
 * 三角模组统一绘制 / UI 工具集。
 *
 * <p>汇集多个界面共用的复杂绘制方法：
 * <ul>
 *   <li>模组 UI 纹理加载：大尺寸 UI 图统一放在 {@code assets/TriUI/} 下，不再进入
 *       {@code sprites/ui} 的 Atlas 页面，避免其参与 UI 图集/字体图集合并；本类直接
 *       从模组文件读取为独立 {@link Texture}。name 支持相对 {@code TriUI} 的路径，
 *       例如 "Tmail/TmailLogo"。</li>
 *   <li>白色描边圆角背景：把任意 Drawable 包装为带 #FFFFFF 圆角描边的版本，按 base 缓存复用。</li>
 *   <li>打字机 Label：挂在宿主 update 上按字符/秒刷新文本。</li>
 * </ul>
 */
public class TriDraw{

    /** 独立纹理缓存（不进入 atlas）。 */
    private static final ObjectMap<String, TextureRegion> fileCache = new ObjectMap<>();
    /** 模组名前缀（含末尾 "-"），惰性取模组 meta.name；仅用于旧资源的 atlas 回退。 */
    private static String modPrefix;

    private TriDraw(){}

    /** 获取模组 TriUI/ 下的贴图；name 为相对 TriUI 的路径（不含扩展名，如 "Tmail/TmailLogo"）。 */
    public static TextureRegion tex(String name){
        TextureRegion cached = fileCache.get(name);
        if(cached != null) return cached;

        // 优先从 assets/TriUI 直接读取独立纹理，避免大图进入 UI 图集/字体图集
        try{
            TextureRegion region = loadFile(name);
            if(region != null){
                fileCache.put(name, region);
                return region;
            }
        }catch(Throwable ignored){
            // 文件缺失或解析失败：继续走 atlas 回退，不崩溃
        }

        // 兼容回退：仍可能存在于 atlas 的旧资源/原版图标
        if(Core.atlas.has(name)) return Core.atlas.find(name);
        if(Core.atlas.has("ui/" + name)) return Core.atlas.find("ui/" + name);
        String prefixed = prefix() + name;
        if(Core.atlas.has(prefixed)) return Core.atlas.find(prefixed);
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

    /** 从模组 assets/TriUI/ 目录读取 png；依次尝试 root / file 两种句柄。 */
    private static TextureRegion loadFile(String name){
        LoadedMod mod = Vars.mods.getMod(triangle.class);
        if(mod == null) return null;

        // 打包后的 jar 会把 assets/ 的内容放到 jar 根目录，因此同时尝试：
        // - assets/TriUI/...（源码/目录模组）
        // - TriUI/...（jar 根目录）
        // 注意：ZipFi.child("A/B/C.png") 不会解析斜杠，必须逐级 child。
        Fi[] candidates = {
            resolve(mod.root, "assets/TriUI/" + name + ".png"),
            resolve(mod.file, "assets/TriUI/" + name + ".png"),
            resolve(mod.root, "TriUI/" + name + ".png"),
            resolve(mod.file, "TriUI/" + name + ".png"),
        };
        for(Fi f : candidates){
            if(f.exists()){
                return loadTexture(f);
            }
        }
        // 兜底：在 assets/TriUI/（或 TriUI/）中按相对路径/文件名递归查找
        for(Fi base : new Fi[]{mod.root, mod.file}){
            for(String rootName : new String[]{"assets/TriUI", "TriUI"}){
                try{
                    Fi ui = resolve(base, rootName);
                    if(!ui.exists()) continue;
                    String suffix = "/" + name.replace('\\', '/') + ".png";
                    Fi found = ui.findAll(f -> f.path().replace('\\', '/').endsWith(suffix)).first();
                    if(found != null){
                        return loadTexture(found);
                    }
                }catch(Throwable ignored){
                    // 压缩包句柄可能不支持递归遍历，继续尝试下一个来源
                }
            }
        }
        return null;
    }

    /** 从文件创建独立纹理；Pixmap 上传后立即释放，避免 native 内存泄漏。 */
    private static TextureRegion loadTexture(Fi file){
        Pixmap pixmap = new Pixmap(file);
        try{
            return new TextureRegion(new Texture(pixmap));
        }finally{
            pixmap.dispose();
        }
    }

    /** 释放本类创建的独立纹理（应用退出/模组卸载时调用）。 */
    public static void dispose(){
        for(TextureRegion region : fileCache.values()){
            if(region.texture != null) region.texture.dispose();
        }
        fileCache.clear();
    }

    /** 逐级拼接 Fi 路径，兼容 ZipFi 不解析带斜杠的 child 路径。 */
    private static Fi resolve(Fi base, String path){
        Fi current = base;
        for(String part : path.replace('\\', '/').split("/")){
            if(!part.isEmpty()){
                current = current.child(part);
            }
        }
        return current;
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

    /** 打字机：通过宿主 Action 播放，按字符/秒刷新文本；不会覆盖宿主已有 update。
     *  @param host 场景中的宿主（需持续 act；Action 会随宿主移除而停止）
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
        float charsPerSecond = Math.max(0f, speed);
        host.addAction(new Action(){
            float elapsed;
            int shown;
            boolean firstDone, finishDone;

            @Override
            public boolean act(float delta){
                elapsed += delta;
                int next = Math.min(text.length(), (int)(elapsed * charsPerSecond));
                if(next != shown){
                    shown = next;
                    typed.setText(text.substring(0, shown));
                    if(shown > 0 && !firstDone){
                        firstDone = true;
                        if(onFirstChar != null) onFirstChar.run();
                    }
                }
                if(shown >= text.length() && !finishDone){
                    finishDone = true;
                    if(onFinish != null) onFinish.run();
                }
                return shown >= text.length();
            }
        });
        return typed;
    }
}
