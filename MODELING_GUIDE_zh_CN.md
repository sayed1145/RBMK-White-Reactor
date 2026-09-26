# v4 3D 建模与动画指南

v4 的所有外观（建筑贴图、前景层、预览图、物品图标、运行时动画）都来自 `src/rbmk/gfx` 里的 Java 网格代码，**没有手绘贴图**。改外观就是改模型代码，然后重新烘焙。

## 1. 坐标系与相机

- 单位：1 格方块 = 8 单位。3×3 工厂的半宽 `half = 12`，8×8 电站 `half = 32`。
- x 向东、y 向北、z 向上；地台面 `deckZ`（工厂 1.4，电站 2）。
- `Cam`：透视相机位于 `(0, -4.9·half, 7·half)`，投影缩放 `s = D/(D − z)`。
- **北侧高度上限**：物体越靠北越不能太高，否则会被裁出建筑范围，上限约为 `z ≤ 1.186·(half − y)`。例如工厂 y = 3 处最高约 10.7。

## 2. 一个模型由四部分组成（`BlockModel`）

| 方法 | 用途 |
|---|---|
| `buildStatic(Mesh)` | 所有不动的几何，烘焙进 `<name>-hd.png` |
| `buildEnvelope(Mesh)` | `drawLive` 中运动部件的**保守扫掠体积**，用来计算前景层 |
| `buildRest(Mesh)` | 运动部件的静止姿态，只用于图标和预览 |
| `drawLive(Building)` | 运动部件，在前景层**之前**绘制，会被前面的静态几何遮挡 |
| `drawOver(Building)` | 永远在所有静态几何之上的部件（龙门吊、刮泥桥），在前景层**之后**绘制 |

包络要尽量贴合：包络里最高点以下的静态像素都不会进入前景层。如果某个高处的运动部件把包络撑得太高，就把它放到 `drawOver` 里，并且不要写进包络（组件厂的龙门吊、水处理站的刮泥桥都是这样做的）。

## 3. 建模工具（`Mesh`）

- `at(x,y,z)` 重置变换；`rot(axis,deg)` 叠加局部旋转；`axis(p0,p1)` 让局部 +z 指向 p1。
- `box / cbox / bevel / cbevel`：方块、居中方块、倒角方块。
- `lathe(sides, angleOffset, r0,z0, r1,z1, …)`：旋转体，轮廓按逆时针排列。`cyl / ring / tubeSide / pipe` 都是它的封装。
- `color(...)`、`style(flags, mat)`：flags 可取 `metal`、`emissive`、`glass`；mat 可取 `matPlate`、`matConcrete`、`matGrate`、`matHazard`、`matFins`、`matWater` 等程序化材质。
- `add(mesh, x,y,z, axis, deg)`：把运动部件的网格按姿态合并进来（用于 `buildRest`）。

## 4. 运行时绘制（`Live`）

```java
Live.pose(x, y, z, axis, deg);  Live.draw(mesh, cam, b.x, b.y);   // 刚体姿态
Live.link(x0,y0,z0, x1,y1,z1, w, h); Live.draw(unitMesh, cam, …);  // 连杆 / 液位这类可伸缩件
Live.glowR/G/B、Live.alpha、Live.resetTint()                         // 自发光颜色与透明度
```

同一建筑内的多个运动部件需要自己按从远到近（北 → 南、下 → 上）的顺序绘制。芯块厂的冲头每帧按 y 做插入排序；组件厂的元件按 y 预先排好序，被夹持的元件插在合适的位置。

## 5. 动画驱动

`FactoryModel` 提供 `warmup(b)`、`progress(b)`、`total(b)` 和 `smooth(a,b,t)`：

- 转速类（转塔、飞轮、风扇、刮泥桥）用 `totalProgress`：它按 warmup 积分，开机和停机时自然缓入缓出。
- 工序类（组件厂 8 段轨迹、元件逐个装入）用 `progress`，每一段都用 `smooth` 衔接。
- 发光和闪烁乘以 `warmup`，避免突然亮起。

## 6. 反应堆控制棒（`RodField`）

- 709 个通道排成正方晶格，填满整个圆。
- 211 根 CPS 棒用最远点采样选取，按 SAR/ER/AC/MR 顺序分配，因此整圆密度均匀。
- 棒高 `0.5 + 2.95·抽出度`，燃料通道高 `0.26 + 1.2·min(局部功率, 1.5)`。
- LOD 分三档：≥2.6 像素/单位时画全部细节，≥1.5 时不画顶盖，更低时只画顶面和正面。

## 7. 工作流

```bash
./build_classes.sh
J=~/.cache/vendor/jdk-17*/bin/java; CP=build/tools:build/classes:$HOME/.cache/vendor/Mindustry.jar
$J -Djava.awt.headless=true -cp $CP bake.BakeAll assets/sprites preview water-treatment   # 烘焙
$J -Djava.awt.headless=true -cp $CP preview.Preview assets/sprites /tmp/fr water-treatment 36 16   # 截帧
$J -Djava.awt.headless=true -cp $CP preview.Bench assets/sprites                            # 性能
```

`preview.Preview` 用捕获式 Batch 运行真实的 `draw()` 代码。如果用到了着色器，或者出现非有限值顶点，它会直接报错。
