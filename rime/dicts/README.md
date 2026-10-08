# 译词词典目录

本目录存放**生成产物**，不入库大文件（`.dict.yaml` / `.db` / `.lua` 已由 `.gitignore` 排除）。

## 一、数据流向

```
CC-CEDICT（汉英）        Rime 拼音词典（luna_pinyin）
      │                          │
      └──────────┬───────────────┘
                 ▼
        tools/cedict_to_rime.py
                 │
   ┌─────────────┼──────────────┬────────────────┐
   ▼             ▼              ▼                ▼
pickword_en    pickword_en.db  pickword_gloss.lua  pickword_en.tsv
.dict.yaml   （路线 B：SQLite）（路线 A2：Lua 表）（通用映射表）
（路线 A：词典第三列即候选注释）
```

## 二、准备输入

1. **CC-CEDICT**（汉英词典，UTF-8，约 12 万条）
   - 官网下载页：<https://www.mdbg.net/chineseindictionary?page=cc-cedict>
   - 解压得到 `cedict_ts.u8`，放到任意位置（不要入库，文件约 20 MB）。
2. **Rime 拼音词典**（提供「词 → 拼音编码」）
   - Trime 子模块：`app/data/rime/luna-pinyin/luna_pinyin.dict.yaml`
   - 或直接取：<https://github.com/rime/rime-luna-pinyin>（需 `git submodule update --init`）。

## 三、生成命令

```bash
# 路线 A：带英文注释的 Rime 词典（最常用）
python tools/cedict_to_rime.py \
  --cedict ~/Downloads/cedict_ts.u8 \
  --rime-dict ../app/data/rime/luna-pinyin/luna_pinyin.dict.yaml \
  --out rime/dicts/pickword_en.dict.yaml

# 同时产出 SQLite（路线 B 用）与 Lua 表（路线 A2 用）
python tools/cedict_to_rime.py ... --sqlite rime/dicts/pickword_en.db
python tools/cedict_to_rime.py ... --format lua --out rime/lua/pickword_gloss.lua
```

常用参数：

| 参数 | 作用 | 建议 |
|---|---|---|
| `--topn 20000` | 只保留前 N 条（按拼音词典内的出现顺序，近似常用度） | 先跑 2 万条试包体与内存 |
| `--max-defs 2` | 每条保留几条英文释义 | 2 条足够，多则候选行被撑爆 |
| `--max-gloss 48` | 注释最大字符数 | 48；`comment_position: right` 时还要考虑屏宽 |
| `--allow-unmatched` | 拼音词典没收录的词，用 CEDICT 去调拼音兜底 | 覆盖率更高，但拼写规则可能与 luna 不一致，需实测 |

## 四、部署到手机（Trime）

Trime 的用户数据目录通常是：
- Android 11+：`/storage/emulated/0/Android/data/com.osfans.trime/files/rime`（或 App 内「用户数据目录」）
- 更简单：App 内选择**方案/词典导入**或用「复制到用户目录」功能。

需要放进去的文件：

```
rime/pickword.schema.yaml        → 用户数据目录
rime/dicts/pickword_en.dict.yaml → 用户数据目录
rime/lua/pickword_comment.lua     → 用户数据目录/lua/
rime/lua/pickword_gloss.lua       → 用户数据目录/lua/
```

然后在 Trime 里「部署 / 重新部署」，首次会编译 prism（几秒到几十秒），之后输入拼音即可看到候选旁出现英文。

## 五、验证清单

- [ ] 输入 `diannao` → 候选「电脑」右侧/下方出现 `computer`
- [ ] 关闭 `show_gloss` 开关 → 注释消失
- [ ] 断网（飞行模式）→ 注释照常出现（证明纯离线）
- [ ] 候选条不被注释挤爆（必要时调小 `--max-gloss` 或 `comment_text_size`）
