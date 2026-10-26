package triangle.black.MRFactory;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;

/**
 * 方案 A：在单个 Building 内保存多个独立生产层。
 *
 * <p>层与层共享物品/液体库存、电力和热量；每层拥有自己的配方选择、
 * 自动模式和 craft 进度。堆叠上限、科技增长线与跨工厂兼容列表分别配置。
 *
 * <p>本类不影响原有 {@link MultiRecipeFactory}；通过把需要支持堆叠的工厂实例
 * 改为本类来启用。
 */
public class StackedMultiRecipeFactory extends MultiRecipeFactory{

    /** 该实例的基础最大层数；<=0 时回退到科技增长线或 3。 */
    public int maxLayers = 3;
    /** 按科技解锁的最大层数增长线；每项的 tech 为空表示无条件。 */
    public Seq<StackUpgrade> stackUpgrades = new Seq<>();

    /** 旧建筑到新建筑的迁移缓存。 */
    private static final IntMap<StackedBuild> pendingMigrations = new IntMap<>();

    static{
        // placeBegan 在部分放置路径中拿不到旧建筑；事件在替换前提供 tile.build。
        Events.on(BlockBuildBeginEvent.class, event -> {
            if(event.breaking || event.tile == null) return;
            if(event.tile.build instanceof StackedBuild existing){
                pendingMigrations.put(event.tile.pos(), existing);
            }
        });
    }

    public StackedMultiRecipeFactory(String name){
        super(name);
    }

    /** 添加一条战役科技层数增长线。 */
    public StackedMultiRecipeFactory stackUpgrade(UnlockableContent tech, int layers){
        stackUpgrades.add(new StackUpgrade(tech, layers));
        return this;
    }

    /**
     * 为工厂创建一个独立的堆叠层数研究节点。
     * 节点挂在当前工厂的科技树节点下；研究完成后 maxLayersNow() 才会取该层数。
     */
    public StackedMultiRecipeFactory stackUpgrade(int layers, ItemStack[] cost){
        String contentName = name + "-stack-" + layers;
        RecipeResearch tech = new RecipeResearch(contentName);
        tech.cost = cost == null ? ItemStack.empty : cost;
        tech.localizedName = (localizedName == null || localizedName.isEmpty() ? name : localizedName) + " Stack " + layers;
        tech.uiIcon = uiIcon;
        tech.hideDatabase = true;
        tech.databaseTabs.clear();
        stackUpgrades.add(new StackUpgrade(tech, layers));
        Events.on(mindustry.game.EventType.ContentInitEvent.class, event -> addResearchNode(this, tech));
        return this;
    }

    /** 当前允许的最大层数；沙盒取全部增长线中的最高值。 */
    public int maxLayersNow(){
        int max = maxLayers > 0 ? maxLayers : 3;
        if(Vars.state != null && Vars.state.rules.infiniteResources){
            for(StackUpgrade upgrade : stackUpgrades){
                max = Math.max(max, upgrade.layers);
            }
            return max;
        }
        for(StackUpgrade upgrade : stackUpgrades){
            if(upgrade.tech == null || upgrade.tech.unlocked()){
                max = Math.max(max, upgrade.layers);
            }
        }
        return max;
    }

    public static class StackUpgrade{
        public final UnlockableContent tech;
        public final int layers;

        public StackUpgrade(UnlockableContent tech, int layers){
            this.tech = tech;
            this.layers = layers;
        }
    }

    /** 是否允许从已有建筑升级到当前方块。 */
    @Override
    public boolean canPlaceOn(Tile tile, mindustry.game.Team team, int rotation){
        if(tile != null && tile.build instanceof StackedBuild existing
            && StackCompatibility.canStack(this, existing.block)
            && existing.layers.size < maxLayersNow()){
            return true;
        }
        return super.canPlaceOn(tile, team, rotation);
    }

    /** 原版替换判定：允许堆叠工厂覆盖同格旧工厂。 */
    @Override
    public boolean canReplace(Block other){
        if(other instanceof StackedMultiRecipeFactory && StackCompatibility.canStack(this, other)){
            return true;
        }
        return super.canReplace(other);
    }

    @Override
    public void placeBegan(Tile tile, Block previous){
        if(tile != null && tile.build instanceof StackedBuild existing
            && StackCompatibility.canStack(this, existing.block)){
            pendingMigrations.put(tile.pos(), existing);
        }
        super.placeBegan(tile, previous);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Tile tile = Vars.world == null ? null : Vars.world.tile(x, y);
        if(tile != null && tile.build instanceof StackedBuild existing
            && StackCompatibility.canStack(this, existing.block)
            && existing.layers.size >= maxLayersNow()){
            drawPlaceText(Core.bundle.get("tri.stack.limit"), x, y, false);
        }
    }

    /** 一层独立生产状态。 */
    public static class LayerState{
        public int recipeIndex = -1;
        public int selectedIndex = -1;
        public boolean auto = true;
        public float progress;
    }

    public class StackedBuild extends MultiRecipeFactoryBuild{
        public Seq<LayerState> layers = new Seq<>();

        public StackedBuild(){
            super();
            layers.add(new LayerState());
        }

        @Override
        public void created(){
            super.created();
            if(layers.isEmpty()) layers.add(new LayerState());

            StackedBuild old = pendingMigrations.remove(tile.pos());
            if(old != null && old != this){
                layers.clear();
                for(LayerState layer : old.layers){
                    layers.add(copyLayer(layer));
                }
                if(items != null && old.items != null) items.set(old.items);
                copyLiquids(old);
                if(layers.size < block().maxLayersNow()){
                    layers.add(new LayerState());
                }
            }
            while(layers.size > Math.max(1, block().maxLayersNow())){
                layers.remove(layers.size - 1);
            }
            normalizeLayers();
            clampStorage();
        }

        private StackedMultiRecipeFactory block(){
            return StackedMultiRecipeFactory.this;
        }

        private LayerState copyLayer(LayerState src){
            LayerState out = new LayerState();
            out.recipeIndex = src.recipeIndex;
            out.selectedIndex = src.selectedIndex;
            out.auto = src.auto;
            out.progress = src.progress;
            return out;
        }

        private void copyLiquids(StackedBuild old){
            if(liquids == null || old.liquids == null) return;
            old.liquids.each((liquid, amount) -> liquids.add(liquid, amount));
        }

        @Override
        public void updateTile(){
            if(layers.isEmpty()) layers.add(new LayerState());

            for(LayerState layer : layers){
                recipeIndex = layer.recipeIndex;
                selectedIndex = layer.selectedIndex;
                auto = layer.auto;
                progress = layer.progress;

                super.updateTile();

                layer.recipeIndex = recipeIndex;
                layer.selectedIndex = selectedIndex;
                layer.auto = auto;
                layer.progress = progress;
            }
            clampStorage();
        }

        private void normalizeLayers(){
            for(LayerState layer : layers){
                if(layer.selectedIndex >= recipes.size) layer.selectedIndex = -1;
                if(layer.recipeIndex >= recipes.size) layer.recipeIndex = -1;
            }
        }

        @Override
        public int getMaximumAccepted(Item item){
            // 原版 itemCapacity 是每种物品的容量，不是全物品总量。
            return Math.max(0, itemCapacityPerType() - (items == null ? 0 : items.get(item)));
        }

        @Override
        public boolean acceptItem(mindustry.gen.Building source, Item item){
            return items != null && items.get(item) < itemCapacityPerType() && super.acceptItem(source, item);
        }

        @Override
        public boolean acceptLiquid(mindustry.gen.Building source, Liquid liquid){
            return liquids != null && liquidTotal() < capacityLiquids() && super.acceptLiquid(source, liquid);
        }

        private int itemCapacityPerType(){
            return Math.max(0, itemCapacity) * Math.max(1, layers.size);
        }

        private float capacityLiquids(){
            return Math.max(0f, liquidCapacity) * Math.max(1, layers.size);
        }

        private float liquidTotal(){
            return liquids == null ? 0f : liquids.sum((liquid, amount) -> amount);
        }

        @Override
        public boolean canOutputForRecipe(Recipe recipe){
            if(checkItemOutputFullness){
                for(ItemStack output : recipe.outputItem){
                    if(items.get(output.item) + output.amount > itemCapacityPerType()) return false;
                }
            }
            if(checkLiquidOutputFullness){
                for(LiquidStack output : recipe.outputLiquid){
                    if(liquids.get(output.liquid) + output.amount > capacityLiquids()) return false;
                }
            }
            return true;
        }

        @Override
        public boolean shouldConsume(){
            if(getRecipe() == null) return false;
            Recipe currentRecipe = getRecipe();
            if(power.status <= 0 || powerConsumer.isUndervoltage(this)) return false;
            if(currentRecipe.heatEnabled && currentRecipe.heatRequirement > 0
                && heat < currentRecipe.heatRequirement) return false;

            if(!ignoreLiquidFullness){
                if(currentRecipe.outputLiquid.isEmpty()) return true;
                boolean allFull = true;
                for(LiquidStack output : currentRecipe.outputLiquid){
                    if(liquids.get(output.liquid) >= capacityLiquids() - 0.001f){
                        if(!dumpExtraLiquid) return false;
                    }else{
                        allFull = false;
                    }
                }
                if(allFull) return false;
            }
            return enabled;
        }

        /** 缩容时销毁每种物品/液体超出当前容量的部分。 */
        public void clampStorage(){
            // 物品容量是每种物品独立计算的，不能按 items.total() 截断。
            int itemCap = itemCapacityPerType();
            if(items != null && itemCap >= 0){
                Seq<ItemStack> stacks = new Seq<>();
                items.each((item, amount) -> stacks.add(new ItemStack(item, amount)));
                for(ItemStack stack : stacks){
                    if(stack.amount > itemCap){
                        items.remove(stack.item, stack.amount - itemCap);
                    }
                }
            }

            float liquidCap = capacityLiquids();
            if(liquids == null) return;
            while(liquidTotal() > liquidCap + 0.0001f){
                Seq<LiquidStack> stacks = new Seq<>();
                liquids.each((liquid, amount) -> stacks.add(new LiquidStack(liquid, amount)));
                if(stacks.isEmpty()) break;
                LiquidStack first = stacks.first();
                liquids.remove(first.liquid, Math.min(first.amount, liquidTotal() - liquidCap));
            }
        }

        /** 逐层拆除；返回该层建造材料，超出容量部分随后销毁。 */
        public boolean removeLayer(int index){
            if(layers.size <= 1 || index < 0 || index >= layers.size) return false;
            layers.remove(index);

            ItemStack[] req = requirements;
            if(req != null && items != null){
                for(ItemStack stack : req){
                    items.add(stack.item, stack.amount);
                }
            }
            clampStorage();
            return true;
        }

        @Override
        public Object config(){
            StringBuilder out = new StringBuilder();
            for(int i = 0; i < layers.size; i++){
                if(i > 0) out.append(',');
                out.append(layers.get(i).auto ? -1 : layers.get(i).selectedIndex);
            }
            return out.toString();
        }

        @Override
        public void configured(Unit unit, Object value){
            if(value instanceof int[] arr){
                ensureLayerCount(arr.length);
                for(int i = 0; i < arr.length && i < layers.size; i++){
                    setSelection(layers.get(i), arr[i]);
                }
            }else if(value instanceof String text){
                String[] parts = text.isEmpty() ? new String[0] : text.split(",");
                ensureLayerCount(parts.length);
                for(int i = 0; i < parts.length && i < layers.size; i++){
                    try{
                        setSelection(layers.get(i), Integer.parseInt(parts[i].trim()));
                    }catch(NumberFormatException ignored){
                        setSelection(layers.get(i), -1);
                    }
                }
            }else if(value instanceof Integer integer){
                ensureLayerCount(1);
                setSelection(layers.first(), integer);
            }else{
                super.configured(unit, value);
            }
        }

        private void ensureLayerCount(int count){
            count = Math.max(1, Math.min(Math.max(1, block().maxLayersNow()), count));
            while(layers.size < count) layers.add(new LayerState());
            while(layers.size > count && layers.size > 1) layers.remove(layers.size - 1);
        }

        private void setSelection(LayerState layer, int value){
            if(value < 0 || value >= recipes.size){
                layer.auto = true;
                layer.selectedIndex = -1;
            }else{
                layer.auto = false;
                layer.selectedIndex = value;
            }
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(layers.size);
            for(LayerState layer : layers){
                write.i(layer.auto ? -1 : layer.selectedIndex);
                write.f(layer.progress);
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                int count = Math.max(1, read.i());
                count = Math.min(count, Math.max(1, block().maxLayersNow()));
                layers.clear();
                for(int i = 0; i < count; i++){
                    LayerState layer = new LayerState();
                    int value = read.i();
                    layer.progress = read.f();
                    setSelection(layer, value);
                    layers.add(layer);
                }
            }else{
                layers.clear();
                LayerState layer = new LayerState();
                layer.auto = auto;
                layer.selectedIndex = selectedIndex;
                layers.add(layer);
            }
            normalizeLayers();
            clampStorage();
        }

        @Override
        public void buildConfiguration(Table table){
            table.clearChildren();
            Table row = new Table();
            row.top().left();
            for(int i = 0; i < layers.size; i++){
                final int index = i;
                LayerState layer = layers.get(i);

                Table panel = new Table(Tex.button);
                panel.top().left().margin(4f);
                panel.add(Core.bundle.format("tri.stack.layer", i + 1)).left().pad(4).row();

                TextButton.TextButtonStyle autoActive = new TextButton.TextButtonStyle(Styles.defaultt);
                autoActive.up = Tex.buttonOver;
                autoActive.over = Tex.buttonOver;
                autoActive.down = Tex.buttonOver;
                autoActive.fontColor = Color.white;

                TextButton.TextButtonStyle autoInactive = new TextButton.TextButtonStyle(Styles.defaultt);
                autoInactive.up = Tex.buttonTrans;
                autoInactive.over = Tex.buttonTrans;
                autoInactive.down = Tex.buttonTrans;
                autoInactive.fontColor = Color.white;

                Seq<Button> recipeRows = new Seq<>();
                Seq<Image> checks = new Seq<>();
                TextButton autoBtn = new TextButton("", autoActive);

                Runnable refresh = () -> {
                    if(layer.auto){
                        autoBtn.setStyle(autoActive);
                        autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("onRe"));
                    }else{
                        autoBtn.setStyle(autoInactive);
                        autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("offRe"));
                    }
                    for(int r = 0; r < recipeRows.size; r++){
                        recipeRows.get(r).setDisabled(isLocked(recipes.get(r)));
                        boolean selected = !layer.auto && layer.selectedIndex == r;
                        checks.get(r).setDrawable(selected ? Tex.checkOnOver : Tex.checkOff);
                    }
                };

                autoBtn.clicked(() -> {
                    layer.auto = true;
                    layer.selectedIndex = -1;
                    configure(config());
                    refresh.run();
                });
                panel.add(autoBtn).size(240f, 46f).padBottom(6f).row();

                TextButton remove = new TextButton(Core.bundle.get("tri.stack.remove"));
                remove.setDisabled(layers.size <= 1);
                remove.clicked(() -> {
                    if(removeLayer(index)){
                        configure(config());
                        buildConfiguration(table);
                    }
                });

                panel.table(cont -> {
                    for(int r = 0; r < recipes.size; r++){
                        Recipe recipe = recipes.get(r);
                        int finalR = r;
                        Button recipeRow = new Button(Styles.defaultb);
                        recipeRow.left();
                        recipeRow.add("[accent][" + (finalR + 1) + "]:[]").width(48f);
                        recipeRow.table(inner -> {
                            inner.table(left -> {
                                left.left();
                                recipe.inputItem.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.item)));
                                recipe.inputLiquid.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                                recipe.inputPayload.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.item)));
                                if(recipe.powerUse > 0){
                                    left.table(pow -> {
                                        pow.image(Icon.power);
                                        pow.add("[stat]" + Strings.autoFixed(recipe.powerUse * 60f, 2));
                                    }).row();
                                }
                            }).growX();
                            inner.table(right -> {
                                right.left();
                                right.image(Icon.right).size(32f).padLeft(8f).padRight(12f);
                                recipe.outputItem.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.item)));
                                recipe.outputLiquid.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                                recipe.outputPayload.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.item)));
                            }).growX();
                        }).growX();

                        recipeRow.clicked(() -> {
                            if(isLocked(recipe)) return;
                            if(!layer.auto && layer.selectedIndex == finalR){
                                layer.auto = true;
                                layer.selectedIndex = -1;
                            }else{
                                layer.auto = false;
                                layer.selectedIndex = finalR;
                            }
                            configure(config());
                            refresh.run();
                        });

                        recipeRows.add(recipeRow);
                        Image check = new Image(Tex.checkOff);
                        checks.add(check);
                        cont.table(c -> {
                            c.add(recipeRow).growX();
                            c.add(check).size(32f).padLeft(8f);
                        }).fillX().row();
                    }
                    cont.add(remove).size(180f, 36f).padTop(6f).row();
                }).growX();

                refresh.run();
                boolean[] lastAuto = {layer.auto};
                int[] lastSelected = {layer.selectedIndex};
                panel.update(() -> {
                    if(layer.auto != lastAuto[0] || layer.selectedIndex != lastSelected[0]){
                        lastAuto[0] = layer.auto;
                        lastSelected[0] = layer.selectedIndex;
                        refresh.run();
                    }
                });

                row.add(panel).top().left().pad(4).width(560f);
            }

            ScrollPane pane = new ScrollPane(row);
            pane.setScrollingDisabled(false, true);
            pane.setFadeScrollBars(false);
            table.add(pane).grow();
        }

        private String recipeName(Recipe recipe, int index){
            if(!recipe.outputItem.isEmpty()) return recipe.outputItem.first().item.localizedName;
            if(!recipe.outputLiquid.isEmpty()) return recipe.outputLiquid.first().liquid.localizedName;
            if(!recipe.outputPayload.isEmpty()) return recipe.outputPayload.first().item.localizedName;
            return "Recipe " + (index + 1);
        }

        @Override
        public void draw(){
            super.draw();
            if(layers.size <= 1 || Fonts.outline == null) return;
            float cx = x + size * 4f;
            float cy = y + size * 8f - 8f;
            Draw.color(0f, 0f, 0f, 0.65f);
            Fill.rect(cx - 12f, cy - 10f, 24f, 15f);
            Draw.color(Pal.accent);
            Fonts.outline.draw(String.valueOf(layers.size), cx, cy, Color.white, 0.45f, false, Align.center);
            Draw.reset();
        }
    }
}
