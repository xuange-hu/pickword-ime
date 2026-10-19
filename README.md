# 拾词输入法 · PickWord（项目代号，提交软著前可改名）

> 打字顺便学外语的开源 Android 输入法。候选词旁显示所学语言的译词与词性，
> 并内置"生词本 + 间隔复习"的主动学习闭环。离线优先、零数据上传、GPL-3.0 开源。

> **临时名称说明**：当前名「拾词 / PickWord」「com.pickword.ime」均为占位。
> 在向中国版权保护中心提交软著**之前**可自由改名；提交后名称须与软著名、桌面显示名**一字不差**，不可再改。

> **作者 / GitHub**：[@xuange-hu](https://github.com/xuange-hu)（显示名 Nexus）
> **仓库地址**：https://github.com/xuange-hu/pickword-ime（在 GitHub 创建空仓库后推送）

---

## 一句话定位

在用户每天本就要做的"打字"动作里，顺手完成外语词汇积累——不打断输入，不强制背词，
用"主动收藏 + 间隔复习"把被动浏览变成可复习的记忆。

## 为什么做（赛道与差异化）

- 赛道极早期（2026-09 后才集中爆发），真实玩家不到 10 个，非赢家通吃市场。
- 现有产品多在"被动译词"层；**主动复习闭环 + 离线开源 + 备考词表**仍是空白楔子。
- 大厂（搜狗/讯飞）暂无此功能，且云端上报的弱点是本项目的离线隐私后盾。

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 输入法内核（v0.1） | **自研 Java 内核** | 纯 Android SDK 实现，无 NDK、无 native 依赖，秒装秒用；`minSdk 21 → targetSdk 36` |
| 输入法内核（可选路线） | 同文 Trime（`osfans/trime`，GPL-3.0） | 需要 Rime 生态（多方案/多语种）时可切到 Trime，详见 [`docs/改造地图.md`](docs/改造地图.md) |
| 译词逻辑 | Rime 候选 `comment` 列 + `librime-lua` 过滤器 | 中文候选生成后查本地词典，把译词作为 comment 附到候选；Trime 已编译 Lua 插件（`RIME_PLUGINS` 含 `librime-lua`），显示层无需改动 |
| 本地词典 | CC-CEDICT（约 11 万条，~10 MB，纯离线） | 候选旁英/日/西译词与词性来源 |
| 生词本 / 复习 | Android Room（SQLite） + SM-2 间隔重复 | 候选"收藏"入口、复习页、学习趋势 |
| 词表导入 | CSV（四六级 / 雅思等） | 差异化：备考人群定制词表 |
| 许可证 | GPL-3.0 | 继承自 Trime；修改后必须开源，源码放 GitHub 满足合规 |

## 路线图（双轨并行）

开发主线：`P0 立项/编译` → `P1 MVP 译词` → `P2 学习闭环` → `P4 上架` → `P5 运营迭代`
资质线（与主线并行）：`软件著作权申请（个人·免费·1–2月，现在启动）` → `隐私政策页 + 商店账号` → 上架收口

- **P0（第 1–2 周）**：建仓库、fork Trime 跑通编译、注册酷安开发者、开软著账号备材料。
- **P1（第 3–6 周）**：CC-CEDICT 转词典 + 译词注入（路线 A / A2）+ 语言开关；酷安内测。
- **P2（第 7–11 周）**：生词本 + SM-2 复习 + 词表导入。
- **P4（第 12–14 周）**：酷安正式 + 提交 OPPO/vivo（个人账号）；备选 F-Droid。
- **P5（持续）**：运营迭代到稳定版；后置 iOS（需 Mac，fork 仓 Hamster）。

## 构建 APK（3 条命令）

前置：JDK 17、Android SDK（Platform 36 + Build Tools 36.0.0）。

```bash
# 1) 生成词库（约 15 MB，仓库已内置一份，改了词源才需要重跑）
python tools/build_ime_db.py \
  --luna luna_pinyin.dict.yaml \
  --cedict cccedict.txt \
  --freq-dict jieba_dict.txt \
  --out app/src/main/assets/pickword.db

# 2) 构建
./gradlew :app:assembleDebug

# 3) 产物
# app/build/outputs/apk/debug/app-debug.apk
```

安装后在系统「设置 → 语言与输入法」启用「拾词输入法」，再切换为当前输入法即可。
更细的环境配置与常见坑见 [`docs/编译与调试.md`](docs/编译与调试.md)；
架构、算法与兼容性设计见 [`docs/产品架构.md`](docs/产品架构.md)。

## 目录约定

```
pickword-ime/
├── README.md                 # 本文件
├── settings.gradle / build.gradle / gradle.properties
├── app/                      # Android 工程（v0.1 自研内核，可直接构建）
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/pickword.db        # 内置词库（生成物）
│       ├── java/com/pickword/ime/    # ime / engine / data / vocab / ui
│       └── res/                      # 布局、颜色、深色模式、图标
├── rime/                     # Rime 方案与译词资产
│   ├── pickword.schema.yaml  # 方案模板（继承 luna_pinyin 拼写规则）
│   ├── lua/
│   │   └── pickword_comment.lua  # 译词注入 Lua filter（路线 A2）
│   └── dicts/                # 生成产物存放处（README 里有生成与部署说明）
├── tools/
│   ├── build_ime_db.py       # 构建内置词库 pickword.db（CEDICT + Rime + jieba 词频）
│   ├── cedict_to_rime.py     # CC-CEDICT + 拼音词典 → 带英文注释的 Rime 词典 / SQLite / Lua 表
│   └── make_icons.py         # 生成应用图标（纯标准库绘制 PNG）
├── docs/
│   ├── P0启动清单.md
│   ├── 产品架构.md            # 构思、分层架构、引擎算法、兼容性与便利性
│   ├── 改造地图.md            # Trime 代码定位与三条译词实现路线
│   ├── 编译与调试.md          # 环境、子模块、构建、常见坑
│   ├── 软著材料清单与模板.md
│   ├── 隐私政策模板.md
│   └── 商店上架指南.md        # 酷安 / OPPO / vivo 个人开发者路径与材料清单
└── privacy-policy/
    └── index.html            # 隐私政策网页（GitHub Pages 托管，上架用链接）
```

## 立即行动（P0）

见 [`docs/P0启动清单.md`](docs/P0启动清单.md)。本周四件事：开软著账号备材料、建仓库、fork Trime 跑通编译、注册酷安开发者。

开工前建议按序读这两份：

1. [`docs/编译与调试.md`](docs/编译与调试.md) —— 环境、子模块、构建命令与常见坑。
2. [`docs/改造地图.md`](docs/改造地图.md) —— 译词该注入到 Trime 的哪一层，三条路线怎么选。

## 仓库与 Git 初始化

本地已 `git init` 并提交初始脚手架，远程指向你的 GitHub 仓库 `https://github.com/xuange-hu/pickword-ime.git`。
在 GitHub 创建**空仓库**（不要勾选 README / .gitignore）后，执行：

```bash
git push -u origin main   # 首次需 gh auth login 或配置 SSH / Personal Access Token
```

本地提交身份已设为 `xuange-hu`。

## 约束与红线

- 软著名称 = App 名 = 桌面名，提交前定稿，提交后不可改。
- 离线优先：默认不申请 `INTERNET` 权限，隐私政策可写"不收集任何输入内容"。
- GPL-3.0：衍生版必须开源，禁止使用"青简/水杉"等他人品牌。
