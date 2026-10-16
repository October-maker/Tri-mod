package triangle.black.MRFactory;

import arc.struct.Seq;
import arc.util.Log;
import mindustry.ctype.UnlockableContent;
import mindustry.type.*;

public class Recipe {
    public Seq<ItemStack> inputItem = new Seq<>();
    public Seq<LiquidStack> inputLiquid = new Seq<>();
    public Seq<PayloadStack> inputPayload = new Seq<>();

    public Seq<ItemStack> outputItem = new Seq<>();
    public Seq<LiquidStack> outputLiquid = new Seq<>();
    public Seq<PayloadStack> outputPayload = new Seq<>();

    public float craftTime = 60f;
    /** 单位：每 tick（1/60 秒）。每秒消耗 = powerUse × 60；如 20f/60f = 20/秒。 */
    public float powerUse = 0.0f;
    public boolean requiresResearch = false; // 是否需要研究解锁生产（原 enabled 重命名；默认 false=无需研究）
    public UnlockableContent research; // 研究条目（requiresResearch=true 时在工厂 init 中创建并挂载）
    public ItemStack[] researchCost; // 可选：显式研究消耗，覆盖默认的 inputItem

    //欠压或低热时是否工作
    public boolean stopUndervoltage = false;//电力不足时立刻停止生产
    public boolean stopLowTemperature = false;//热量不足时立刻停止生产
    
    // 热量相关
    public boolean heatEnabled = false; // 是否启用热量功能
    public float heatRequirement = 0f; // 热量需求（消耗）
    public float heatOutput = 0f; // 热量产出
    public float recipeMaxEfficiency = 4f;

    public Recipe(Object... objects) {
        // 参数必须成对（键:值 或 资源:数量）；奇数长度说明配置笔误，最后一个参数会被静默丢弃，
        // 提前告警避免配方配置错误静默失效
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
                // 支持通过字符串键设置 powerUse 和 craftTime
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
                } else if (key.equals("recipeMaxEfficiency")) {
                    recipeMaxEfficiency = value;
                }
            }
        }
    }
}
