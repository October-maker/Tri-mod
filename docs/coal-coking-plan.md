# Tri 模组 · 煤炭焦化增产方案（基于现有分馏工厂）

> 状态：设计方案，**尚未落地任何代码**。本文件是跨上下文的经验存档，便于后续接续实现。
> 建立时间：2026-11-16。相关源码：`src/triangle/content/TriOilFactory.java`、`TriLiquids.java`、`TriItems.java`、`TriFactory.java`（boiler）、`SerTriTechTree.java`、`src/triangle/black/MRFactory/{MultiRecipeFactory,Recipe}.java`。

## 0. 现有石化线的机制约束（方案必须遵守）

- 分馏类方块 = `MultiRecipeFactory`（MRF）。典型形态：`size 3~5`、`itemCapacity 40`、`liquidCapacity 120~320`、`outputsLiquid = true`、`rotate = true`、`liquidOutputDirections = {1,2,3}`（输出液体按数组顺序定泄流方向，超出数组长度则任意侧泄）。
- 每个配方成对存在：**常电配方**（`powerUse` 直给，单位「每 tick」，`×60` = 每秒）与**高热配方**（`powerUse = 2`、`heatEnabled = true`、`heatRequirement`、`recipeMaxEfficiency = 4`、`stopLowTemperature = true`、`stopUndervoltage = true`）。
- 比率风格：输入 1 → 输出合计 ≈ 0.9~1.0；`craftTime` 一律 30（60 = 1 秒）。
- 三处电力口径：`powerUse 12f` = 720/s；`powerUse 20f/60f` = 20/s。
- 液体注册：`TriLiquids.load()` 内 `new Liquid("name", Color.valueOf("RRGGBBAA"))`；参数由 `assets/content/liquids/**/*.json` 覆盖（`barColor/flammability/temperature/viscosity/explosiveness/heatCapacity/databaseTag`）。
- 科技树：Java 方块无 JSON `research`，一律在 `SerTriTechTree.load()` 里 `Events.on(ContentInitEvent, e -> addNode(parent, child))` 手动挂载，**父必须先于子**；多配方工厂的堆叠研究节点用 `addNodeLater` 或 `Recipe.requiresResearch`。
- 现有石化链死端：`oilArene`（芳烃）全库只有产出、没有消费方——焦化链可顺手接通。

## 1. 现实工业链路 → 工序对照

| 现实工序 | 关键条件 | 产出 | 本方案对应 |
|---|---|---|---|
| 备煤（破碎、配煤） | 粒度 <3mm | 配合煤 | **省略**，直接吃原版 `Items.coal` |
| 高温干馏（焦炉） | 1000~1100℃、隔绝空气、18~24h | 焦炭 70~75%、焦炉煤气 15~20%、煤焦油 3~4%、粗苯 0.8~1.2%、氨 0.2% | `cokeOvenBattery` |
| 煤气净化 | 脱 H₂S→硫、脱 NH₃→氨水、脱苯 | 净煤气、硫、氨 | `coalGasPurificationTower` |
| 煤气转化制氢 | 蒸汽转化 | H₂ | `cogReformingFurnace` |
| 焦油常压蒸馏 | 脱水后切 <170℃ / 170~230 / 230~300℃ | 轻油、酚油、萘油 | `coalTarAtmosphericStill` |
| 焦油减压蒸馏 | 300~360 / 360~400 / >400℃ | 洗油、蒽油、沥青 | `coalTarVacuumStill` |
| 焦油加氢精制 | 高压 H₂ | 汽柴油、石脑油 | **复用** `oilCokingHydrogenationTower` 加配方 |
| 粗苯加氢精制 | 加氢脱噻吩 | 苯/甲苯/二甲苯 | **复用** `oilAromaticHydrocarbonComplex` 加配方 |
| 沥青延迟焦化 | 400~500℃ | 针状焦、焦化气 | `pitchDelayedCoker` |

## 2. 新增内容

**物品（2）**：`coke`（焦炭，flammability 高，可烧）、`needleCoke`（针状焦，hardness 高、价值高）。
**液体（9）**：`coalTar`、`cog`（粗焦炉煤气）、`cogClean`、`crudeBenzene`、`coalTarLight`、`coalTarPhenol`、`coalTarNaphthalene`、`coalTarWash`、`coalTarAnthracene`、`pitch`。

| 液体 | 现实对应 | barColor | 黏度 / 可燃性 |
|---|---|---|---|
| `coalTar` | 煤焦油（脱水后） | `1A1410A0` | 0.85 / 1.5 |
| `cog` | 焦炉煤气（粗） | `4A4A4A90` | 0.40 / 3.0 |
| `cogClean` | 净化焦炉煤气 | `6A6A6A90` | 0.40 / 3.2 |
| `crudeBenzene` | 粗苯 | `C8A85AA0` | 0.55 / 4.0 |
| `coalTarLight` | 轻油 | `B0895AA0` | 0.50 / 3.5 |
| `coalTarPhenol` | 酚油 | `7A5A3AA0` | 0.70 / 2.0 |
| `coalTarNaphthalene` | 萘油 | `9A8A6AA0` | 0.75 / 1.8 |
| `coalTarWash` | 洗油 | `6A5540A0` | 0.80 / 1.5 |
| `coalTarAnthracene` | 蒽油 | `4A3A2AA0` | 0.90 / 1.2 |
| `pitch` | 煤沥青 | `0A0A0AA0` | 1.00 / 0.6 |

新 `databaseTag`：`Coal`（煤炭焦化）、`fromCoal`（焦化产物）。

## 3. 方块数值草案（6 新 + 3 扩展）

### ① `cokeOvenBattery` 焦炉（5×5，MRF，dirs {1,2,3}，h≈300）
```
常规：Items.coal 4 → coke 3 + coalTar 0.30 + cog 0.50 + crudeBenzene 0.10
     craftTime 60，powerUse 3（180/s）
高热：同产出，powerUse 2，heatRequirement 15，recipeMaxEfficiency 4（×4 → 液率 1.2/s 等）
需求：FeSteel 60、TiAlloy 30、Al 30、MonocrystallineSi 20
绘制：baseGlow("FFB06099") + DrawFlame
```
收率对齐现实：焦 75%、气 15~20%、焦油 3~4%、粗苯 ~1%。

### ② `coalGasPurificationTower` 煤气净化（3×3，dirs {1,3}）
```
cog 0.50 + water 0.40 → cogClean 0.45 + TriItems.S 1(物) + liquidAmmonia 0.08
craftTime 30，powerUse 6（360/s）；高热 powerUse 2 + heatReq 5 + maxEff 4
需求：FeSteel 35、Al 20、MonocrystallineSi 10、TiAlloy 15
```

### ③ `cogReformingFurnace` 煤气转化制氢（3×3）
```
cogClean 0.50 + steam 0.30 → hydrogen 0.45
craftTime 30，powerUse 8（480/s）；高热 heatReq 8
需求：TiAlloy 25、FeSteel 30、MonocrystallineSi 15、Pt 3
```

### ④ `coalTarAtmosphericStill` 焦油常压蒸馏（5×5，dirs {1,2,3}）
```
coalTar 0.30 → coalTarLight 0.06 + coalTarPhenol 0.07 + coalTarNaphthalene 0.09
craftTime 30，powerUse 12（720/s）；高热 heatReq 10
需求：FeSteel 50、TiAlloy 15、Al 35、MonocrystallineSi 35
```

### ⑤ `coalTarVacuumStill` 焦油减压蒸馏（5×5，dirs {1,2,3}）
```
coalTarNaphthalene 0.20 → coalTarWash 0.06 + coalTarAnthracene 0.07 + pitch 0.06
craftTime 30，powerUse 12；高热 heatReq 12
```

### ⑥ `pitchDelayedCoker` 沥青延迟焦化（3×3）
```
pitch 0.15 → needleCoke 2(物) + cog 0.15
craftTime 60，powerUse 6（360/s）；高热 heatReq 12
需求：FeSteel 40、TiAlloy 25、WSteel 5、MonocrystallineSi 10
```

### 扩展（不加方块）
| 方块 | 新配方 | 备注 |
|---|---|---|
| `oilCokingHydrogenationTower` | `coalTarWash .15 + coalTarAnthracene .10 + H₂ .12 → oilGasoline .07 + oilDiesel .06 + oilNaphtha .07`（30t，9.8，高热 heatReq 9） | 煤焦油加氢改质 |
| `oilAromaticHydrocarbonComplex` | `crudeBenzene .10 + H₂ .08 → oilArene .10`（30t，6，高热 heatReq 3） | 粗苯精制接芳烃 |
| `boiler` | `cogClean .60 + water 5.18 → steam 5.18`；`coke 2 + water 5.18 → steam 5.18`（60t，105/60） | 煤气与焦炭的燃料销路 |

上述三条扩展配方建议 `requiresResearch = true`（走 MRF 堆叠研究节点），不改旧配方的解锁面。

## 4. 焦炭 / 针状焦出口（链路闭合）

1. `boiler` 烧焦炭 → 蒸汽。
2. `WC` / `carbide` 现配方（`tungsten 6 + Items.coal 3`）各加一条「`coke 2` 顶替 `Items.coal 3`」的版本。
3. `needleCoke` → `Items.graphite`（人造石墨/电极）或作为后续电解线高端材料。
4. `oilArene` 死端由粗苯接通，并建议补一个消费方（苯酚/苯酐/炸药线）。

## 5. 科技树挂载（父先子后）

```java
addNode(TriOilFactory.oilVacuumDistillationColumn, cokeOvenBattery);   // 主锚点
addNode(cokeOvenBattery, coalGasPurificationTower);
addNode(coalGasPurificationTower, cogReformingFurnace);
addNode(cokeOvenBattery, coalTarAtmosphericStill);
addNode(coalTarAtmosphericStill, coalTarVacuumStill);
addNode(coalTarVacuumStill, pitchDelayedCoker);
```
备选锚点：`TriFactory.boiler`（把焦化做成独立「燃料工业」支线）。

## 6. 落地顺序（5 步）

1. `TriItems` 加 `coke`、`needleCoke` + `assets/content/items/*.json`（flammability/hardness/cost）。
2. `TriLiquids` 加 9 个字段与颜色 + `assets/content/liquids/Coal/*.json` 参数（`research` 留空交给科技树）。
3. 新建 `src/triangle/content/TriCoalFactory.java`（与 `TriOilFactory` 平行，`TriFactory.load()` 调用）：6 个 MRF 照抄现有写法（常电+高热双配方），末尾统一 `recipes.each(r -> r.stopUndervoltage = true)`、`databaseTag = "Coal"`。
4. 三处扩展配方 + `requiresResearch`。
5. `SerTriTechTree` 加 6 条 `addNode`；bundle 补 `block.triangle-*.name/description`、`liquid.triangle-*.name/description/details`、`item.triangle-*.name/description`。

验证：`compileJava --offline` → 游戏内核对科技树节点、每块多液体泄流方向、详情页配方/功耗。

## 7. 待拍板取舍

1. 备煤工序：建议省掉（直接 `Items.coal`），否则多一个方块 + 多一种物品只为止改名。
2. 焦炉煤气是否独立液体：建议独立（`cog`/`cogClean`），组成与石油气差异大。
3. 液体数量：9 种是上限；精简方案可把轻油/酚油/萘油三合一为「焦油轻馏分」。
4. 电耗：全链常电合计约 2820/s，全高热约 120/s×6 + 热源；偏贵可把焦炉与两塔 `powerUse` 各降 20~30%，比率不变。

## 8. 完整链路（文字版，供无需图时核对）

```
Items.coal ──(焦炉 ①)──┬─ coke ──┬─ boiler(复用) → steam → 供热/发电
                       │         └─ WC/carbide 配方（coke 2 顶煤 3）
                       ├─ coalTar ──(常压蒸馏 ④)──┬─ coalTarLight
                       │                          ├─ coalTarPhenol
                       │                          └─ coalTarNaphthalene ──(减压蒸馏 ⑤)──┬─ coalTarWash ──┐
                       │                                                                ├─ coalTarAnthracene ─┤
                       │                                                                └─ pitch ──(延迟焦化 ⑥)→ needleCoke / cog(回流)
                       │                                            ┌───────────────────────┘
                       │                                            └─(焦油加氢 复用扩展)→ gasoline / diesel / naphtha
                       ├─ cog ──(煤气净化 ②)→ cogClean + S + liquidAmmonia
                       │                    └─(转化制氢 ③)→ hydrogen → 焦油加氢 / 加氢裂化 / 粗苯精制
                       └─ crudeBenzene ──(粗苯精制 复用扩展)→ oilArene（当前死端，建议接消费方）
```

图：见对话中的 HTML 工业链图（动态/交互版可另做）。
