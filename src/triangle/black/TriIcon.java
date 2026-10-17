package triangle.black;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;
import triangle.triangle;

/**
 * 模组 UI 图标纹理加载。
 *
 * Mindustry 打包模组 sprites 时，atlas 键 = "&lt;modName&gt;-&lt;文件名无扩展名&gt;"
 * （如 mod.hjson name 为 "triangle" 时，sprites/ui/Tmail.png 的键为 "triangle-Tmail"，
 * 参考 NewHorizon 模组：new-horizon-raid 等）。直接用 Core.atlas.find("Tmail")
 * 会找不到并回退 ohno/白块。
 *
 * 本类依次尝试：直接文件名键 → ui/ 路径键 → 模组名前缀键（引擎标准）→
 * 从模组文件直接读取（root/file 多候选路径），成功后以引擎标准键注册进 atlas。
 */
public class TriIcon{

    /** 文件兜底加载的缓存（atlas 收录的不在此缓存，由 atlas 统一管理）。 */
    private static final ObjectMap<String, TextureRegion> fileCache = new ObjectMap<>();
    /** 模组名前缀（含末尾 "-"），惰性取模组 meta.name。 */
    private static String modPrefix;

    private TriIcon(){}

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
        return null;
    }
}
