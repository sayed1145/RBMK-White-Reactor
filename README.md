RBMK 白色反应堆 v4.0 — Mindustry v8 Java 模组

协作：sayed1145（真人/算法）+ NLM-2b（人机/工程）

适配 Mindustry v8 build 160.5，安卓与桌面通用。

反应堆和四座工厂全部用真 3D 模型：同一份 Java 网格代码，离线用透视 z-buffer 烘焙，运行时把运动部件逐面投影绘制，并用前景遮挡层处理烘焙几何与运动部件之间的前后关系。

反应堆 芯块厂 包壳轧制厂 燃料组件厂 除盐水处理站
preview/rbmk-plant.gif preview/pellet-plant.gif preview/cladding-mill.gif preview/fuel-assembly-plant.gif preview/water-treatment.gif

GIF 由 tools/preview 直接驱动游戏里的 draw() 逐帧截取。

3D 管线

部分 作用
Cam 固定透视相机，烘焙和运行时共用
Mesh 四边形网格，提供 box / bevel / lathe / pipe / ring 等工具
Light 主光 + 环境光 + 高光
tools/bake/Baker 离线透视 z-buffer 渲染，SSAA、AO、程序化材质
Live 运行时逐面投影 + 背面剔除 + 光照，用 Fill.quad，不用自定义着色器
前景层 烘焙时单独输出比运动部件更靠近相机的静态像素，运行时最后绘制

绘制顺序：烘焙底图 → 运动部件 → 前景层 → 永远在最上方的部件。

反应堆

· 709 个通道按正方晶格排满半径内整圆，节距 1.445。
· 211 根 CPS 棒用最远点采样分配，从圆心到边缘密度均匀。
· 每根棒是独立 3D 方柱，高度取自 rodActual[i]，每帧插值；燃料通道高度跟随局部功率。
· 按屏幕像素密度做三档 LOD。

四座工厂

· 芯块厂：12 冲头转塔，冲头沿凸轮曲线下冲回弹，芯块滑入料盘，烧结炉窗口随预热发光。
· 包壳轧制厂：飞轮曲柄连杆驱动轧机机架往复，上下轧辊反向滚动，管坯南粗北细，刻痕随进给移动。
· 燃料组件厂：三轴龙门机械手，8 段轨迹完成取放插焊，18 个位按进度装满。
· 除盐水处理站：刮泥桥旋转，水面高光流动，两座离子交换柱液位错相起伏，气泡上升，给水泵风扇随预热变速。

动画由 warmup / progress / totalProgress 连续驱动。

性能

preview.Bench 实测（桌面 JVM，每次 draw，满细节）：

建筑 CPU / 次 四边形 / 次
RBMK 电站（8×8） 0.27 ms ≈3555
核燃料芯块厂 0.16 ms ≈854
包壳轧制厂 0.04 ms ≈349
燃料组件厂 0.04 ms ≈343
除盐水处理站 0.02 ms ≈189

静态部分预烘焙，每建筑固定 2 张贴图；运动部件只提交少量四边形；不用自定义着色器，每帧不分配内存。

物品图标

芯块、包壳管、燃料组件、除盐水的 32px 图标由同一管线渲染（tools/bake/ItemIcons）。

供应链

1. 核燃料芯块厂：钍 2 + 硅 1 + 4.5 电力 → 浓缩燃料芯块 2 / 2 秒
2. 包壳轧制厂：钛 3 + 钢化玻璃 1 + 3.8 电力 → 锆合金包壳 2 / 1.5 秒
3. 燃料组件厂：芯块 4 + 包壳 2 + 石墨 2 + 6.5 电力 → 铀燃料组件 1 / 3 秒
4. 除盐水处理站：原版水 0.36/tick + 5.2 电力 → 除盐轻水 0.32/tick
5. RBMK 白色电站 Mk.II：消耗燃料组件和除盐轻水发电

发电

· 引导模式额定：500 power/tick
· 专业模式最大：1000 power/tick
· 效率 = 堆芯功率 × 主汽阀开度，200% 电气负荷封顶

操作

引导模式：接入燃料组件与除盐轻水 → 控制台选引导模式 → 保持自动功率调节 → 目标负荷 100%。稳态约 101% 堆芯功率，输出约 500/tick。

专业模式：

· SAR 24 根短吸收棒
· ER 24 根紧急保护棒
· AC 24 根自动功率调节棒
· MR 139 根手动径向调节棒
· 合计 211 根，支持 #1–#211 逐根选择和抽出度微调
· 211 根映射到 709 方柱阵列中互不重复的 211 个位置
· 两条主冷却回路，每条 4 台主循环泵，8 台可独立调速
· 给水调节、汽包水位、主汽阀
· AZ-5 统一紧急停堆

实时显示：堆芯功率、净发电、温度、汽包压力与水位、左右回路流量、空泡份额、氙中毒、ORM、空间功率峰值。低/高鼓水位、单侧流量不足、低 ORM、超温超压进入跳闸。

已测试高输出配置：211 根棒约 82% 抽出，8 泵 100%，给水 100%，主汽阀 100%，输出约 1000/tick。

专业界面用于营造操作感，不是工程控制系统。

设计参考

· OECD NEA：RBMK-1000 为石墨慢化压力管式沸水堆，211 根控制棒，3200 MWt / 1000 MWe
· World Nuclear Association：两条冷却回路、每回路四台泵、汽水分离器、自动/手动/紧急控制棒、正空泡系数
· IAEA INSAG-7：人机界面、培训、RBMK 设计、控制棒与安全系统
· IAEA RBMK 技术综述：1661 个燃料通道、两套回路、每回路四台主泵、ORM、事故后改进

这些机构没有审查、认证或背书本模组。

安装

使用 dist/RBMK-White-Reactor-v4.0.1.jar。

1. 手机下载 jar
2. Mindustry → 模组 → 导入模组
3. 选择 jar 并重启
4. 要求 Mindustry build ≥160，测试版本 v8 build 160.5

jar 含桌面 .class 和安卓 classes.dex，Android min API 21。

构建与测试

```bash
bash tools/setup_env.sh v160.5
./build.sh
./test_server.sh
```

只改模型时：

```bash
./build_classes.sh
J=~/.cache/vendor/jdk-17*/bin/java; CP=build/tools:build/classes:$HOME/.cache/vendor/Mindustry.jar
$J -Djava.awt.headless=true -cp $CP bake.BakeAll assets/sprites preview pellet-plant
$J -Djava.awt.headless=true -cp $CP preview.Preview assets/sprites /tmp/fr pellet-plant 36 16
$J -Djava.awt.headless=true -cp $CP preview.Bench assets/sprites
```

无头测试覆盖：5 个建筑与 4 种资源加载、整条生产链产出、输入过滤、引导稳态 500/tick、专业 1000/tick、211 根独立棒、AZ-5、存档往返、熔毁。结果见 test-server-report.txt。

参考代码

· 用户(sayed1145)提供的 VoxelIndustry-src-1.1.zip：固定透视相机 + 网格投影 + 画家排序的思路参考；v4 管线为重新编写，未包含其代码。
· wzk112/reactor-audio-visualizer（MIT）：白色反应堆/控制棒阵列的美术语言与交互节奏参考。
· Mindustry v160.5 API：https://github.com/Anuken/Mindustry/tree/v160.5