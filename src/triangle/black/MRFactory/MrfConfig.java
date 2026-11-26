package triangle.black.MRFactory;

/**
 * MRF 的配置载荷：一个定长头的 {@code int[]}。
 *
 * <p>布局（下标 = 元素序号）：
 * <pre>
 * 0            version
 * 1            flags
 * 2            recipe（基座 / 第一层的选择索引，自动为 -1）
 * 3            liquidMirror（0..3）
 * 4            layerCount（仅 flags 带 flagLayers 时有效）
 * 5 + 2i       第 i 层 flags（低两位）
 * 6 + 2i       第 i 层选择索引（自动为 -1）
 * </pre>
 *
 * <p>使用 int[] 的原因：它是 {@code TypeIO.writeObject/readObject} 的原生类型
 * （长度按 short 写 + 每个元素 4 字节），蓝图 {@code Schematics} 与存档 {@code SaveVersion}
 * 都走这条序列化路径，存档和多人发包不会因为“未知类型”抛异常掉线。
 * 自定义对象、{@code Seq}（会被写成 null）都不能用。
 */
public final class MrfConfig{
    /** 载荷版本；新增字段时 +1，并在尾部追加槽位。 */
    public static final int version = 1;
    /** 固定头长度：version / flags / recipe / mirror / layerCount。 */
    public static final int baseLength = 5;
    /** 层表上限（防御损坏存档导致的超大分配）。 */
    public static final int maxLayers = 64;
    public static final int maxLength = baseLength + maxLayers * 2;

    public static final int flagAuto = 1;
    public static final int flagOverride = 1 << 1;
    public static final int flagMirror = 1 << 2;
    public static final int flagLayers = 1 << 3;

    /** 层标志复用低两位。 */
    public static final int layerAuto = 1;
    public static final int layerOverride = 1 << 1;

    private MrfConfig(){
    }

    /**
     * 组装载荷。{@code layerModes}/{@code layerRecipes} 为 null 或空时是基座格式（5 个 int）。
     *
     * @param recipe       基座（或第一层）的选择索引，自动为 -1
     * @param auto         基座（或第一层）是否自动匹配
     * @param override     整机超控位
     * @param mirror       镜像位（只取低两位）
     * @param layerModes   每层标志（{@link #layerAuto} / {@link #layerOverride}）
     * @param layerRecipes 每层选择索引（自动为 -1）
     */
    public static int[] encode(int recipe, boolean auto, boolean override, int mirror,
                               int[] layerModes, int[] layerRecipes){
        int count = (layerModes == null || layerRecipes == null)
            ? 0
            : Math.min(layerModes.length, layerRecipes.length);
        count = Math.min(count, maxLayers);
        int[] out = new int[baseLength + count * 2];
        int flags = 0;
        if(auto) flags |= flagAuto;
        if(override) flags |= flagOverride;
        if((mirror & 3) != 0) flags |= flagMirror;
        if(count > 0) flags |= flagLayers;

        out[0] = version;
        out[1] = flags;
        out[2] = recipe;
        out[3] = mirror & 3;
        out[4] = count;
        for(int i = 0; i < count; i++){
            out[5 + i * 2] = layerModes[i] & (layerAuto | layerOverride);
            out[6 + i * 2] = layerRecipes[i];
        }
        return out;
    }

    /** 头完整且版本匹配；未来版本的载荷会被当成“不认识”而忽略，不会误读。 */
    public static boolean valid(int[] payload){
        return payload != null && payload.length >= baseLength && payload[0] == version;
    }

    public static boolean stacked(int[] payload){
        return (payload[1] & flagLayers) != 0;
    }

    public static boolean auto(int[] payload){
        return (payload[1] & flagAuto) != 0;
    }

    public static boolean override(int[] payload){
        return (payload[1] & flagOverride) != 0;
    }

    public static int mirror(int[] payload){
        return payload[3] & 3;
    }

    public static int recipe(int[] payload){
        return payload[2];
    }

    /** 实际可读的层数（同时受载荷长度约束，损坏数据不会越界）。 */
    public static int layerCount(int[] payload){
        int declared = payload[4];
        int available = (payload.length - baseLength) / 2;
        return Math.max(0, Math.min(declared, available));
    }

    public static int layerMode(int[] payload, int index){
        if(index < 0 || index >= layerCount(payload)) return 0;
        return payload[5 + index * 2] & (layerAuto | layerOverride);
    }

    public static boolean layerAuto(int[] payload, int index){
        return (layerMode(payload, index) & layerAuto) != 0;
    }

    public static boolean layerOverride(int[] payload, int index){
        return (layerMode(payload, index) & layerOverride) != 0;
    }

    public static int layerRecipe(int[] payload, int index){
        if(index < 0 || index >= layerCount(payload)) return -1;
        return payload[6 + index * 2];
    }

    /** 就地改镜像位；只给蓝图翻转用，调用方负责先 clone。 */
    public static void setMirror(int[] payload, int mask){
        if(!valid(payload)) return;
        payload[3] = mask & 3;
        if((mask & 3) != 0){
            payload[1] |= flagMirror;
        }else{
            payload[1] &= ~flagMirror;
        }
    }
}
