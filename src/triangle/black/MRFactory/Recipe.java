package triangle.black.MRFactory;

import arc.func.Boolp;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.ctype.UnlockableContent;
import mindustry.type.*;
import triangle.black.TMail.TriInbox;

/**
 * 多配方工厂的一条生产配方。
 *
 * <p>分别声明物品、液体、载荷的输入/输出，以及生产时间、电力与热量参数。
 * 既可直接给字段赋值，也可用 {@code new Recipe(资源, 数量, "键", 值, ...)} 的成对参数写法，
 * 支持的字符串键见 {@link #Recipe(Object...)}。
 */
public class Recipe {
    /** 每周期消耗的物品。 */
    public Seq<ItemStack> inputItem = new Seq<>();
    /** 每周期消耗的液体。 */
    public Seq<LiquidStack> inputLiquid = new Seq<>();
    /** 每周期消耗的载荷。 */
    public Seq<PayloadStack> inputPayload = new Seq<>();

    /** 每周期产出的物品。 */
    public Seq<ItemStack> outputItem = new Seq<>();
    /** 每周期产出的液体。 */
    public Seq<LiquidStack> outputLiquid = new Seq<>();
    /** 每周期产出的载荷。 */
    public Seq<PayloadStack> outputPayload = new Seq<>();

    /** 生产一个周期所需时间（tick，60 = 1 秒）。 */
    public float craftTime = 60f;
    /** 电力消耗，单位：每 tick（1/60 秒）。每秒消耗 = powerUse × 60；如 20f/60f = 20/秒。 */
    public float powerUse = 0.0f;
    /** 是否需要研究解锁生产（默认 false = 无需研究）。 */
    public boolean requiresResearch = false;
    /** 研究条目：requiresResearch = true 时在工厂 init 中创建并挂载。 */
    public UnlockableContent research;
    /** 可选：显式研究消耗，覆盖默认的 inputItem。 */
    public ItemStack[] researchCost;
    /**
     * 额外解锁条件；null 表示不附加条件。
     * 仅在 {@link #requiresResearch} 为 true 时生效；研究节点和配方都必须满足该条件。
     */
    public Boolp unlockCondition = null;
    /** 科技树“研究条件”中显示的文字；null 时使用通用文本。 */
    public String unlockConditionText = null;

    /** 电力不足时是否立刻停止生产。 */
    public boolean stopUndervoltage = false;
    /** 热量不足时是否立刻停止生产。 */
    public boolean stopLowTemperature = false;

    // ---- 热量相关 ----
    /** 是否启用热量功能（参与供热/耗热）。 */
    public boolean heatEnabled = false;
    /** 热量需求（每 tick 消耗）。 */
    public float heatRequirement = 0f;
    /** 热量产出。 */
    public float heatOutput = 0f;
    /** 热量充足时的最大效率倍率（超过需求的热量可继续提升效率）。 */
    public float recipeMaxEfficiency = 4f;

    /**
     * 以成对参数构造配方。
     *
     * <p>每两个参数为一组：
     * <ul>
     *   <li>{@code Item + Integer} → 物品输入；</li>
     *   <li>{@code Liquid + Float} → 液体输入；</li>
     *   <li>{@code UnlockableContent + Integer} → 载荷输入；</li>
     *   <li>{@code String + 数值/布尔} → 设置字段，支持 {@code "power"}、{@code "time"}、
     *       {@code "heatReq"}、{@code "heatOut"}、{@code "heatEnabled"}、{@code "stopLowTemperature"}、
     *       {@code "stopUndervoltage"}、{@code "requiresResearch"}、{@code "recipeMaxEfficiency"}；
     *       {@code "unlockCondition"} 接受 {@link Boolp}；{@code "unlockConditionText"} 接受字符串。</li>
     * </ul>
     * 参数个数为奇数说明配置笔误，最后一个参数会被忽略，同时输出警告。
     */
    public Recipe(Object... objects) {
        if(objects.length % 2 != 0){
            Log.warn("[triangle] Recipe 构造器收到奇数个参数（@ 个），最后一个参数 '@' 被忽略；配方参数应为成对的 键:值 或 资源:数量。",
                    objects.length, String.valueOf(objects[objects.length - 1]));
        }
        for (int i = 0; i < objects.length / 2; i++) {
            if (objects[i * 2] instanceof Item item && objects[i * 2 + 1] instanceof Integer count) {
                inputItem.add(new ItemStack(item, count));
            } else if (objects[i * 2] instanceof Liquid liquid && objects[i * 2 + 1] instanceof Float count) {
                inputLiquid.add(new LiquidStack(liquid, count));
            } else if (objects[i * 2] instanceof UnlockableContent payload && objects[i * 2 + 1] instanceof Integer count) {
                inputPayload.add(new PayloadStack(payload, count));
            } else if (objects[i * 2] instanceof String key) {
                // 字符串键：设置电力、时间与热量等标量参数
                Object val = objects[i * 2 + 1];
                float value = (val instanceof Number) ? ((Number) val).floatValue() : 0f;
                if (key.equals("power")) {
                    powerUse = value;
                } else if (key.equals("time")) {
                    craftTime = value;
                } else if (key.equals("heatReq")) {
                    heatRequirement = value;
                    heatEnabled = true;
                } else if (key.equals("heatOut")) {
                    heatOutput = value;
                    heatEnabled = true;
                } else if (key.equals("heatEnabled")) {
                    heatEnabled = (val instanceof Boolean b) ? b : (value > 0);
                } else if (key.equals("stopLowTemperature")) {
                    stopLowTemperature = (val instanceof Boolean b) ? b : (value > 0);
                } else if (key.equals("stopUndervoltage")) {
                    stopUndervoltage = (val instanceof Boolean b) ? b : (value > 0);
                } else if (key.equals("requiresResearch")) {
                    requiresResearch = (val instanceof Boolean b) ? b : (value > 0);
                } else if (key.equals("unlockCondition") && val instanceof Boolp condition) {
                    unlockCondition = condition;
                } else if (key.equals("unlockConditionText") && val instanceof String text) {
                    unlockConditionText = text;
                } else if (key.equals("recipeMaxEfficiency")) {
                    recipeMaxEfficiency = value;
                }
            }
        }
    }

    /** 科技树节点已解锁。 */
    public static Boolp tech(UnlockableContent content){
        return () -> content != null && content.unlocked();
    }

    /** Tmail 指定 key 的信件已读。
     *  动态资讯使用稳定 id，例如 {@code "modinfo-about"}；
     *  内置信件使用不带 modinfo- 前缀的 key，例如 {@code "info_about"}。
     */
    public static Boolp mailRead(String mailKey){
        return () -> TriInbox.isRead(mailKey);
    }

    /** Tmail 指定 key 的信件附件已收取。 */
    public static Boolp mailAttachmentCollected(String mailKey){
        return () -> TriInbox.attachmentCollected(mailKey);
    }

    /** 全部条件同时满足。 */
    public static Boolp all(Boolp... conditions){
        return () -> {
            if(conditions == null) return true;
            for(Boolp condition : conditions){
                if(condition != null && !condition.get()) return false;
            }
            return true;
        };
    }
}
