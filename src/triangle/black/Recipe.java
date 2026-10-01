package triangle.black;

import arc.struct.Seq;
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
    public int priority = 0;
    public float powerUse = 1.0f;

    //欠压或低热时是否工作
    public boolean stopUndervoltage = false;//电力不足时立刻停止生产
    public boolean stopLowTemperature = false;//热量不足时立刻停止生产
    
    // 热量相关
    public boolean heatEnabled = false; // 是否启用热量功能
    public float heatRequirement = 0f; // 热量需求（消耗）
    public float heatOutput = 0f; // 热量产出
    public float recipeMaxEfficiency = 4f;

    public Recipe(Object... objects) {
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
                } else if (key.equals("recipeMaxEfficiency")) {
                    recipeMaxEfficiency = value;
                }
            }
        }
    }
}
