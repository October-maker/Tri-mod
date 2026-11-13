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
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.scene.ui.Label;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
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
 *   <li>制表线条：用 {@code assets/TriUI/TriLines/} 下 4 张 32×32 贴图循环平铺出直线（1+2）、
 *       点划线（1+3）、虚线（3），并按需在两端/转角用 4 号贴图包边（见 {@link #hLine}、{@link #vLine}）。</li>
 *   <li>打字机 Label：挂在宿主 update 上按字符/秒刷新文本。</li>
 * </ul>
 */
public class TriDraw{

    /** 独立纹理缓存（不进入 atlas）。 */
    private static final ObjectMap<String, TextureRegion> fileCache = new ObjectMap<>();
    /** 已确认不存在的纹理，避免反复递归扫描 jar/目录。 */
    private static final ObjectSet<String> missingCache = new ObjectSet<>();
    /** 模组名前缀（含末尾 "-"），惰性取模组 meta.name；仅用于旧资源的 atlas 回退。 */
    private static String modPrefix;

    private TriDraw(){}

    /** 获取模组 TriUI/ 下的贴图；name 为相对 TriUI 的路径（不含扩展名，如 "Tmail/TmailLogo"）。 */
    public static TextureRegion tex(String name){
        TextureRegion cached = fileCache.get(name);
        if(cached != null) return cached;
        if(missingCache.contains(name)) return Core.atlas.find("white");

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
        missingCache.add(name);
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
        Fi file = findUiFile(name);
        return file == null ? null : loadTexture(file);
    }

    /** 定位 assets/TriUI/ 下的 png 文件（不含扩展名，如 "TriLines/TriLine-1"）；找不到返回 null。 */
    private static Fi findUiFile(String name){
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
                return f;
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
                        return found;
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
        // TriLines 制表贴图同样是从文件独立加载的纹理，需要单独释放
        for(Texture texture : lineTextures){
            if(texture != null) texture.dispose();
        }
        java.util.Arrays.fill(lineTextures, null);
        java.util.Arrays.fill(lineContentHeights, 0f);
        for(int i = 0; i < lineTiles.length; i++) lineTiles[i] = null;
        lineCapMirror = null;
        lineDrawables.clear();
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

    // ============================ UI 制表线条 ============================

    /**
     * 线条图案：决定循环显示哪几张 TriLines 贴图。
     * 素材约定：1/2 号为长划、3 号为短点、4 号为端头包边。
     */
    public enum LineStyle{
        /** 直线：TriLine-1 与 TriLine-2 交替。 */
        STRAIGHT,
        /** 点划线：TriLine-1 与 TriLine-3 交替。 */
        DASH_DOT,
        /** 虚线：TriLine-3 循环。 */
        DOTTED
    }

    /** TriLines 贴图缓存，下标 0..3 对应 TriLine-1..4（保持 32×32 原图，不裁剪）。 */
    private static final TextureRegion[] lineTiles = new TextureRegion[4];
    /** TriLines 贴图对应的独立纹理，仅用于卸载时释放；走 atlas/white 回退时该槽保持 null。 */
    private static final Texture[] lineTextures = new Texture[4];
    /** 各贴图不透明内容的像素高度：贴图不裁剪，只在换算“线宽”时用到（见 loadLineTile）。 */
    private static final float[] lineContentHeights = new float[4];
    /** 4 号包边的水平镜像版本（线条末端收口用），懒创建。 */
    private static TextureRegion lineCapMirror;
    /** 已构建的线条 Drawable 缓存，避免每次排版都新建对象。 */
    private static final ObjectMap<String, Drawable> lineDrawables = new ObjectMap<>();

    /**
     * 横向线条（两端用 4 号贴图包边）；线宽取单元格高度。
     *
     * <p>用法：{@code table.image(TriDraw.hLine(LineStyle.DASH_DOT)).growX().height(10f);}
     */
    public static Drawable hLine(LineStyle style){
        return line(style, false, true, true, 0f);
    }

    /**
     * 横向线条，显式指定线宽（贴图按该线宽<b>等比缩放</b>，不随单元格高度拉伸）。
     *
     * <p>用法：{@code table.image(TriDraw.hLine(LineStyle.DASH_DOT, 6f)).growX().height(14f);}
     * 线体在单元格内垂直居中，多出来的高度自然成为上下留白。
     * 注意：线宽大于单元格高度时线体会溢出单元格，请让单元格至少与线宽等高。
     *
     * @param thickness 线宽（UI 单位）；&lt;= 0 时回退为“单元格高度即线宽”
     */
    public static Drawable hLine(LineStyle style, float thickness){
        return line(style, false, true, true, thickness);
    }

    /** 纵向线条（两端用 4 号贴图包边）；线宽取单元格宽度。 */
    public static Drawable vLine(LineStyle style){
        return line(style, true, true, true, 0f);
    }

    /** 纵向线条，显式指定线宽（贴图等比缩放）；线体在单元格内水平居中。 */
    public static Drawable vLine(LineStyle style, float thickness){
        return line(style, true, true, true, thickness);
    }

    /** 通用线条 Drawable（线宽取单元格在厚度方向的尺寸：横向取高度、纵向取宽度）。 */
    public static Drawable line(LineStyle style, boolean vertical, boolean capStart, boolean capEnd){
        return line(style, vertical, capStart, capEnd, 0f);
    }

    /**
     * 通用线条 Drawable。
     *
     * @param style     图案（直线 / 点划线 / 虚线）
     * @param vertical  纵向为 true：贴图整体旋转 90° 绘制
     * @param capStart  首端是否画 4 号包边（转角处若已单独放包边件，可关掉这一端）
     * @param capEnd    末端是否画 4 号包边
     * @param thickness 线宽（UI 单位）；&lt;= 0 表示取单元格在厚度方向的尺寸
     */
    public static Drawable line(LineStyle style, boolean vertical, boolean capStart, boolean capEnd, float thickness){
        String key = style.name() + "|" + vertical + "|" + capStart + "|" + capEnd + "|" + thickness;
        Drawable cached = lineDrawables.get(key);
        if(cached == null){
            cached = new LineDrawable(pattern(style), vertical, capStart, capEnd, Math.max(0f, thickness), patternUnit(style));
            lineDrawables.put(key, cached);
        }
        return cached;
    }

    /**
     * 图案的“内容高度”基准：取该图案所有贴图（含 4 号包边）中最大的不透明内容高度。
     * 线宽参数换算成等比缩放时以它为分母，这样可见线体厚度就等于传入的线宽。
     */
    private static float patternUnit(LineStyle style){
        float max = lineContentHeight(4);          // 包边也要参与，避免端头比线体粗
        switch(style){
            case DASH_DOT:
                max = Math.max(max, Math.max(lineContentHeight(1), lineContentHeight(3)));
                break;
            case DOTTED:
                max = Math.max(max, lineContentHeight(3));
                break;
            case STRAIGHT:
            default:
                max = Math.max(max, Math.max(lineContentHeight(1), lineContentHeight(2)));
                break;
        }
        return Math.max(1f, max);
    }

    /**
     * 新建一条“可逐帧控制显示长度”的线条，供开场“自左向右 / 自上而下延长”的动画使用。
     *
     * <p>与 {@link #line} 的缓存实例不同，这里每次都返回<b>独立实例</b>，进度互不干扰：
     * <pre>{@code
     * LineDrawable line = TriDraw.hLineReveal(LineStyle.STRAIGHT, 2f);
     * line.progress(0.5f); // 只显示左边一半
     * }</code></pre>
     *
     * @param thickness 线宽（UI 单位）；&lt;= 0 表示取单元格在厚度方向的尺寸
     */
    public static LineDrawable lineReveal(LineStyle style, boolean vertical, boolean capStart, boolean capEnd, float thickness){
        return new LineDrawable(pattern(style), vertical, capStart, capEnd, Math.max(0f, thickness), patternUnit(style));
    }

    /** 纵向 / 横向的可生长线条便捷版本（两端均带 4 号包边）。 */
    public static LineDrawable hLineReveal(LineStyle style, float thickness){
        return lineReveal(style, false, true, true, thickness);
    }

    /** 纵向可生长线条便捷版本（两端均带 4 号包边）。 */
    public static LineDrawable vLineReveal(LineStyle style, float thickness){
        return lineReveal(style, true, true, true, thickness);
    }


    /**
     * 单独绘制 4 号包边件，用于转角 / 接缝处补边；线宽取单元格短边。
     *
     * @param rotation 旋转角度（度）；0 为素材原方向
     */
    public static Drawable lineCap(float rotation){
        return lineCap(rotation, 0f);
    }

    /** 单独绘制 4 号包边件，显式指定线宽（&lt;= 0 时回退为单元格短边），贴图等比缩放。 */
    public static Drawable lineCap(float rotation, float thickness){
        return new BaseDrawable(){
            @Override
            public void draw(float x, float y, float width, float height){
                TextureRegion region = lineTile(4);
                // 线宽优先取参数，未指定时用单元格短边；以“内容高度”为分母做等比缩放
                float lineWidth = thickness > 0f ? thickness : Math.min(width, height);
                float scale = lineWidth / Math.max(1f, lineContentHeight(4));
                float capW = region.width * scale;
                float capH = region.height * scale;
                // 7 参 rect：以格子中心为原点旋转，旋转后仍保持居中
                Draw.rect(region, x + width / 2f, y + height / 2f, capW, capH, capW / 2f, capH / 2f, rotation);
            }
        };
    }

    /** 按图案类型返回循环贴图序列：1+2 交替 / 1+3 交替 / 3 循环。 */
    private static TextureRegion[] pattern(LineStyle style){
        switch(style){
            case DASH_DOT:
                return new TextureRegion[]{lineTile(1), lineTile(3)};
            case DOTTED:
                return new TextureRegion[]{lineTile(3)};
            case STRAIGHT:
            default:
                return new TextureRegion[]{lineTile(1), lineTile(2)};
        }
    }

    /** 取第 index 号（1 起）TriLines 贴图；首次读取时记录内容高度（贴图本身不裁剪）。 */
    private static TextureRegion lineTile(int index){
        int slot = index - 1;
        if(lineTiles[slot] == null){
            lineTiles[slot] = loadLineTile(index);
        }
        return lineTiles[slot];
    }

    /**
     * 读取 TriLines 贴图，并记录其不透明内容的像素高度。
     *
     * <p><b>贴图不裁剪</b>：素材是 32×32 方格，线体只占中间一条且四周留透明边，
     * 直接首尾相接时这些透明留白正好成为图案自带的接缝（1/2/4 号内容 28×11，3 号 16×7）。
     * 这里只做一次 alpha 包围盒统计，得到“内容高度”，用于把线宽参数换算成等比缩放比例。
     */
    private static TextureRegion loadLineTile(int index){
        String name = "TriLines/TriLine-" + index;
        Fi file = findUiFile(name);
        // 文件缺失时沿用 tex() 的 atlas / white 回退（此时不参与纹理释放，内容高度按整图算）
        if(file == null){
            TextureRegion fallback = tex(name);
            lineContentHeights[index - 1] = fallback.height;
            return fallback;
        }

        Pixmap pixmap = new Pixmap(file);
        try{
            int w = pixmap.getWidth(), h = pixmap.getHeight();
            // arc 的 Pixmap.get 直接读取本地字节序的 RGBA8888 数据，alpha 所在字节随平台字节序变化；
            // 这里统计 4 个通道各自的 0 值数量，取“0 最多”的通道作为 alpha：
            // - 透明区写成 (0,0,0,0) 时，四个通道结果一致，取哪个都对；
            // - 透明区写成 (255,255,255,0) 时，只有真正的 alpha 通道会有大量 0，据此可正确识别。
            int alphaShift = 0;
            int bestZeros = -1;
            for(int channel = 0; channel < 4; channel++){
                int shift = channel * 8;
                int zeros = 0;
                for(int y = 0; y < h; y++){
                    for(int x = 0; x < w; x++){
                        if(((pixmap.get(x, y) >> shift) & 0xff) == 0) zeros++;
                    }
                }
                if(zeros > bestZeros){
                    bestZeros = zeros;
                    alphaShift = shift;
                }
            }

            int minX = w, minY = h, maxX = -1, maxY = -1;
            for(int y = 0; y < h; y++){
                for(int x = 0; x < w; x++){
                    if(((pixmap.get(x, y) >> alphaShift) & 0xff) != 0){
                        if(x < minX) minX = x;
                        if(y < minY) minY = y;
                        if(x > maxX) maxX = x;
                        if(y > maxY) maxY = y;
                    }
                }
            }
            Texture texture = new Texture(pixmap); // 构造即完成上传，之后可以释放 pixmap
            TextureRegion region = new TextureRegion(texture); // 整张 32×32 原样保留
            // 记录内容高度（整图没有不透明像素时按整图高度处理，避免除零）
            lineContentHeights[index - 1] = (maxY >= minY) ? (maxY - minY + 1) : h;
            lineTextures[index - 1] = texture;
            return region;
        }finally{
            pixmap.dispose();
        }
    }

    /** 贴图不透明内容高度（像素）；用于把线宽换算成缩放，贴图本身仍按整图绘制。 */
    private static float lineContentHeight(int index){
        float recorded = lineContentHeights[index - 1];
        return recorded > 0f ? recorded : lineTile(index).height;
    }

    /** 4 号包边的水平镜像副本（末端包边用），只创建一次。 */
    private static TextureRegion capMirrored(){
        if(lineCapMirror == null){
            TextureRegion mirror = new TextureRegion(lineTile(4));
            mirror.flip(true, false); // 只翻转 u，纵向绘制时该翻转会随 90° 旋转变成沿线条方向翻转
            lineCapMirror = mirror;
        }
        return lineCapMirror;
    }

    /**
     * 按“固定节距”循环贴图绘制的一条线。
     *
     * <p>贴图<b>整张 32×32 直接首尾相接</b>（不裁剪），四周透明留白即图案自带的接缝。
     * 所有贴图统一按内容高度等比缩放（宽、高用同一比例，既不变形也不拉伸），
     * 因此 1/2 号长划与 3 号短点的粗细差异保持素材原样。
     * 线宽可由构造参数指定；未指定时取单元格在厚度方向的尺寸。
     * 只绘制完整图案周期，剩余长度在两端居中留白，避免右端出现半截贴图。
     *
     * <p>类公开是为了支持<b>生长动画</b>：{@link #progress(float)} 控制已显示的长度比例，
     * 由 {@code TriDraw.lineReveal(...)} 创建的实例专供动画使用（不走缓存，各自动画互不影响）。
     */
    public static class LineDrawable extends BaseDrawable{
        private final TextureRegion[] tiles;
        private final TextureRegion cap;        // 4 号包边（首端，原方向）
        private final TextureRegion capMirror;  // 4 号包边（末端，水平镜像）
        private final boolean vertical, capStart, capEnd;
        /** 显式线宽（UI 单位）；&lt;= 0 表示沿用单元格尺寸。 */
        private final float thickness;
        /** 图案的内容高度基准（像素），把“线宽”换算成等比缩放比例；由 patternUnit 预先算好。 */
        private final float unit;
        /** 显示进度（0~1）：1 = 完整，0.x = 只显示到该比例，用于“自左向右 / 自上而下延长”。 */
        private float progress = 1f;

        LineDrawable(TextureRegion[] tiles, boolean vertical, boolean capStart, boolean capEnd, float thickness, float unit){
            this.tiles = tiles;
            this.cap = lineTile(4);
            this.capMirror = capMirrored();
            this.vertical = vertical;
            this.capStart = capStart;
            this.capEnd = capEnd;
            this.thickness = thickness;
            // unit 由 patternUnit() 统一给出：取图案内（含包边）最大的不透明内容高度
            this.unit = Math.max(1f, unit);
        }

        /** 设置显示进度（0~1，自动夹取），返回自身便于链式调用。 */
        public LineDrawable progress(float value){
            this.progress = Mathf.clamp(value);
            return this;
        }

        @Override
        public void draw(float x, float y, float width, float height){
            if(width <= 0f || height <= 0f) return;
            float fraction = Mathf.clamp(progress);
            if(fraction <= 0f) return;                              // 进度为 0：完全不画
            // 线宽：显式参数优先；未指定时取单元格在厚度方向的尺寸（横向用高度、纵向用宽度）
            float cellThickness = vertical ? width : height;
            float lineWidth = thickness > 0f ? thickness : cellThickness;
            // 等比缩放：贴图的宽、高共用同一比例，只改变“显示大小”而不改变形状
            float scale = lineWidth / unit;
            if(vertical){
                drawVertical(x, y, width, height, scale, fraction);
            }else{
                drawHorizontal(x, y, width, height, scale, fraction);
            }
        }

        /** 横向绘制：贴图按原方向从左到右依次排布，只画到 fraction 对应的右边界。 */
        private void drawHorizontal(float x, float y, float width, float height, float scale, float fraction){
            float centerY = y + height / 2f;                       // 线体在单元格内竖直居中
            float capW = cap.width * scale;                        // 包边占用的长度
            float start = x + (capStart ? capW : 0f);
            float end = x + width - (capEnd ? capW : 0f);
            float avail = end - start;
            float limit = x + width * fraction;                    // 当前已“长出”的右边界

            if(avail > 0f){
                float cycle = 0f;                                  // 一个完整图案周期的长度
                for(TextureRegion t : tiles) cycle += t.width * scale;
                if(cycle > 0f){
                    int cycles = (int)(avail / cycle);             // 只排完整周期，避免半截贴图
                    float cursor = start + (avail - cycles * cycle) / 2f; // 余量居中，两端留白对称
                    outer:
                    for(int c = 0; c < cycles; c++){
                        for(TextureRegion t : tiles){
                            float tw = t.width * scale;
                            float th = t.height * scale;
                            if(cursor >= limit) break outer;       // 已长到边界
                            if(cursor + tw <= limit){
                                // 整格可见：按整图绘制（透明留白即图案自带接缝）
                                Draw.rect(t, cursor, centerY - th / 2f, tw, th);
                            }else{
                                // 生长中的最后一格：只裁出左侧可见部分
                                drawPartialHorizontal(t, cursor, centerY - th / 2f, tw, th, limit - cursor);
                            }
                            cursor += tw;
                        }
                    }
                }
            }

            // 首端包边：同样随进度裁切，保证动画起点干净
            if(capStart){
                float ch = cap.height * scale;
                if(limit >= x + capW){
                    Draw.rect(cap, x, centerY - ch / 2f, capW, ch);
                }else if(limit > x){
                    drawPartialHorizontal(cap, x, centerY - ch / 2f, capW, ch, limit - x);
                }
            }
            // 末端包边只在完全展开后出现，避免它跟着生长边缘一起移动
            if(capEnd && fraction >= 1f){
                float ch = cap.height * scale;
                Draw.rect(capMirror, x + width - capW, centerY - ch / 2f, capW, ch);
            }
        }

        /** 纵向绘制：贴图旋转 90° 后从上到下排布，只画到 fraction 对应的下边界。 */
        private void drawVertical(float x, float y, float width, float height, float scale, float fraction){
            float centerX = x + width / 2f;                        // 线体在单元格内水平居中
            float capLen = cap.width * scale;                      // 包边沿线条方向占用的长度
            float start = y + (capStart ? capLen : 0f);
            float end = y + height - (capEnd ? capLen : 0f);
            float avail = end - start;
            float limit = y + height * fraction;

            if(avail > 0f){
                float cycle = 0f;
                for(TextureRegion t : tiles) cycle += t.width * scale;
                if(cycle > 0f){
                    int cycles = (int)(avail / cycle);
                    float cursor = start + (avail - cycles * cycle) / 2f;
                    outer:
                    for(int c = 0; c < cycles; c++){
                        for(TextureRegion t : tiles){
                            float len = t.width * scale;   // 贴图长度轴（旋转后沿屏幕 y）
                            float th = t.height * scale;   // 线宽（旋转后沿屏幕 x）
                            if(cursor >= limit) break outer;
                            if(cursor + len <= limit){
                                // 7 参 rect：以格心为中心旋转 90°，让贴图的“长度轴”转到竖直方向
                                Draw.rect(t, centerX, cursor + len / 2f, len, th, len / 2f, th / 2f, 90f);
                            }else{
                                drawPartialVertical(t, centerX, cursor, len, th, limit - cursor);
                            }
                            cursor += len;
                        }
                    }
                }
            }

            if(capStart){
                float th = cap.height * scale;
                if(limit >= y + capLen){
                    Draw.rect(cap, centerX, y + capLen / 2f, capLen, th, capLen / 2f, th / 2f, 90f);
                }else if(limit > y){
                    drawPartialVertical(cap, centerX, y, capLen, th, limit - y);
                }
            }
            if(capEnd && fraction >= 1f){
                drawRotatedCap(capMirror, centerX, y + height - capLen / 2f, capLen, scale);
            }
        }

        /**
         * 只绘制贴图左侧 keep 宽度的部分：uv 与绘制宽度同步裁剪，图案不会被压缩。
         * 生长动画时每帧只有“边界那一格”走到这里，开销可忽略。
         */
        private void drawPartialHorizontal(TextureRegion region, float x, float y, float fullW, float fullH, float keep){
            float frac = Mathf.clamp(keep / fullW, 0f, 1f);
            if(frac <= 0f) return;
            TextureRegion part = new TextureRegion(region);        // 复制一份，避免改动缓存里的 region
            part.u2 = part.u + (region.u2 - region.u) * frac;      // 截取左侧 uv 段
            Draw.rect(part, x, y, fullW * frac, fullH);            // 宽度同比缩短，像素不被拉伸
        }

        /** 纵向版的局部绘制：沿旋转后的长度轴（屏幕 y）裁切。 */
        private void drawPartialVertical(TextureRegion region, float centerX, float y, float fullLen, float fullTh, float keep){
            float frac = Mathf.clamp(keep / fullLen, 0f, 1f);
            if(frac <= 0f) return;
            TextureRegion part = new TextureRegion(region);
            part.u2 = part.u + (region.u2 - region.u) * frac;
            float len = fullLen * frac;
            Draw.rect(part, centerX, y + len / 2f, len, fullTh, len / 2f, fullTh / 2f, 90f);
        }

        /** 纵向包边：与图案同样旋转 90° 绘制，保持与线体一致的朝向。 */
        private void drawRotatedCap(TextureRegion region, float centerX, float centerY, float len, float scale){
            float th = region.height * scale;
            Draw.rect(region, centerX, centerY, len, th, len / 2f, th / 2f, 90f);
        }
    }

    /** 打字机（简单版）：居中对齐，调用方自行加入布局。 */
    public static Label typewriter(Element host, String text, float speed){
        return typewriter(host, text, speed, Align.center, null, null);
    }

    /**
     * 预计算“已显示可见字符数 → 原始文本截取位置”的映射。
     *
     * <p>规则：颜色/样式标记（{@code [yellow]}、{@code [#FF0000]} 等）整体计入截取位置但不计入可见字符，
     * 因此任一时刻截取出来的前缀都不会以半个标记结尾；{@code [[} 视为转义的字面方括号，按一个可见字符计。
     *
     * @return 长度为“总可见字符数 + 1”的数组，{@code cuts[n]} 表示显示 n 个可见字符时应截取到的原始下标
     *         （末位等于 {@code text.length()}）
     */
    static int[] markupCuts(String text){
        int[] cuts = new int[text.length() + 1];
        int visible = 0;
        int i = 0;
        while(i < text.length()){
            char c = text.charAt(i);
            // 转义的 "[["：字面方括号，算一个可见字符
            if(c == '[' && i + 1 < text.length() && text.charAt(i + 1) == '['){
                i += 2;
                visible++;
                cuts[visible] = i;
                continue;
            }
            if(c == '['){
                int close = text.indexOf(']', i + 1);
                int newline = text.indexOf('\n', i + 1);
                // 长度合理、不跨行、且内部无空白才视为标记，否则按普通方括号字符处理
                if(close > i + 1 && close - i <= 24 && (newline < 0 || newline > close) && noWhitespace(text, i + 1, close)){
                    i = close + 1;
                    cuts[visible] = i; // 标记完整越过，截取位置随之前移（不占用可见字符数）
                    continue;
                }
            }
            i++;
            visible++;
            cuts[visible] = i;
        }
        int[] out = new int[visible + 1];
        System.arraycopy(cuts, 0, out, 0, visible + 1);
        return out;
    }

    /**
     * 标记内部（{@code [} 之后到 {@code ]} 之前）是否不含空白字符；
     * 含空白的方括号按普通文字处理，避免把 "…[备注 说明]…" 这类正文误当标记吞掉。
     */
    private static boolean noWhitespace(String text, int from, int to){
        for(int k = from; k < to; k++){
            if(Character.isWhitespace(text.charAt(k))) return false;
        }
        return true;
    }

    /** 打字机：通过宿主 Action 播放，按“可见字符/秒”刷新文本；不会覆盖宿主已有 update。
     *
     *  <p>标记感知：颜色/样式标记（如 {@code [yellow]}、{@code [#FF0000]}）会被整体吐出，
     *  既不占用打字进度，也不会出现被截断的半个标记（旧版 {@code [lightgray]} 之类标记
     *  在打到一半时既不显示颜色、又会把标记原文露出来的问题由此解决）。
     *  {@code [[} 作为转义的字面方括号，按一个可见字符计。这样文本可以继续沿用旧版内联标记排版，
     *  同时也兼容调用方用 Label 样式统一设定基色（未被标记覆盖的文字使用样式的字体颜色）。
     *
     *  @param host 场景中的宿主（需持续 act；Action 会随宿主移除而停止）
     *  @param text 完整文本
     *  @param speed 可见字符/秒
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

        // 预计算“已显示可见字符数 → 原始文本截取位置”映射（实现见 markupCuts）。
        int[] cuts = markupCuts(text);
        int total = cuts.length - 1;

        host.addAction(new Action(){
            float elapsed;
            int shown;
            boolean firstDone, finishDone;

            @Override
            public boolean act(float delta){
                elapsed += delta;
                int next = Math.min(total, (int)(elapsed * charsPerSecond));
                if(next != shown){
                    shown = next;
                    typed.setText(text.substring(0, cuts[shown]));
                    if(shown > 0 && !firstDone){
                        firstDone = true;
                        if(onFirstChar != null) onFirstChar.run();
                    }
                }
                if(shown >= total && !finishDone){
                    finishDone = true;
                    if(onFinish != null) onFinish.run();
                }
                return shown >= total;
            }
        });
        return typed;
    }
}
