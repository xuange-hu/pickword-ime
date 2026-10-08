#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
cedict_to_rime.py —— 把 CC-CEDICT 汉英词典 + Rime 拼音词典，合成「带英文注释的 Rime 词典」

用途
----
拾词输入法路线 A：让 Rime 在生成中文候选时，把英文释义写进候选的 comment 列，
Trime 的候选渲染层（CandidateItemUi / LabeledCandidateItemUi）会自动把 comment 显示在候选旁。

产出格式（Rime 词典第三列非纯数字时即作为候选注释）：
    汉字<TAB>拼音音节(空格分隔)<TAB>English gloss

输入
----
1) CC-CEDICT 词典（cedict_ts.u8，UTF-8）
   下载：https://www.mdbg.net/chineseindictionary?page=cc-cedict
   直接链接形如：https://www.mdbg.net/chineseindictionary/export/cedict/cedict_1_0_ts_utf-8_mdbg.zip
   行格式：傳統 传统 [chuan2 tong3] /traditional (simplified)/.../

2) Rime 拼音词典（luna_pinyin.dict.yaml）
   来源：Trime 仓库子模块 app/data/rime/luna-pinyin/luna_pinyin.dict.yaml
   （或 https://github.com/rime/rime-luna-pinyin）
   行格式：汉字<TAB>拼音音节(空格分隔)[<TAB>权重]

依赖：仅标准库。用法见 --help / 下方 USAGE。
"""
from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
from typing import Dict, Iterable, List, Tuple

# ----------------------------------------------------------------------
# 常量
# ----------------------------------------------------------------------

CEDICT_LINE = re.compile(
    r"^(?P<trad>\S+)\s+(?P<simp>\S+)\s+\[(?P<pinyin>[^\]]+)\]\s*(?P<gloss>/.*/)?\s*$"
)
# luna_pinyin 词典正文行：词<TAB>编码[<TAB>权重]
DICT_SPLIT = re.compile(r"\t+")
# 注释里不允许出现制表符/换行；斜杠是 CEDICT 的释义分隔符，需替换
FORBIDDEN_IN_COMMENT = re.compile(r"[\t\r\n]+")
# 用于判断是否「可保留」的词条：中文 + 可选少量 ASCII（如 "QQ"、"B超"）
KEEP_WORD = re.compile(r"^[一-鿿0-9A-Za-z·・\-]{1,12}$")

USAGE = """
示例
----
# 1) 生成 Rime 词典（默认参数即可）
python tools/cedict_to_rime.py \\
    --cedict cedict_ts.u8 \\
    --rime-dict ../app/data/rime/luna-pinyin/luna_pinyin.dict.yaml \\
    --out rime/dicts/pickword_en.dict.yaml

# 2) 同时产出 SQLite，供路线 B（Android 层后处理）使用
python tools/cedict_to_rime.py ... --sqlite rime/dicts/pickword_en.db

# 3) 只要常用 2 万条、释义截断到 40 字符（控制词典体积 / 内存）
python tools/cedict_to_rime.py ... --topn 20000 --max-gloss 40 --max-defs 2

# 4) 不依赖拼音编码，直接输出「中文<TAB>英文」映射（供 Lua 或 App 层查表）
python tools/cedict_to_rime.py ... --format map --out rime/dicts/pickword_en.tsv
"""

# ----------------------------------------------------------------------
# 解析
# ----------------------------------------------------------------------


def iter_cedict(path: str) -> Iterable[Tuple[str, str, List[str]]]:
    """逐行解析 CC-CEDICT，产出 (简体词, 拼音, [释义...])。

    忽略注释行（#! 开头的元信息行）与解析失败的行。
    """
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m = CEDICT_LINE.match(line)
            if not m:
                continue
            simp = m.group("simp")
            gloss_raw = m.group("gloss") or ""
            defs = [d.strip() for d in gloss_raw.split("/") if d.strip()]
            yield simp, m.group("pinyin"), defs


def load_rime_codes(path: str) -> Dict[str, List[str]]:
    """解析 Rime 词典，返回 {词: [拼音编码, ...]}（多音字会有多个编码）。"""
    codes: Dict[str, List[str]] = {}
    # Rime 词典结构：--- / YAML 头 / --- / 正文。第二个 --- 之后才是词条。
    sep_count = 0
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if line.strip() in ("---", "..."):
                sep_count += 1
                continue
            if sep_count < 2:
                continue
            if not line or line.lstrip().startswith("#"):
                continue
            parts = DICT_SPLIT.split(line)
            if len(parts) < 2:
                continue
            word, code = parts[0].strip(), parts[1].strip()
            if not word or not code:
                continue
            bucket = codes.setdefault(word, [])
            if code not in bucket:
                bucket.append(code)
    return codes


# ----------------------------------------------------------------------
# 清洗
# ----------------------------------------------------------------------


def clean_gloss(defs: List[str], max_defs: int, max_len: int) -> str:
    """把 CEDICT 的多条释义压成一行英文注释。"""
    picked: List[str] = []
    for d in defs:
        # 量词标注（"CL:個|个,..."）对学习无用，默认丢弃
        if d.startswith("CL:"):
            continue
        # CEDICT 释义里常见 "see  syllables"、"CL:個|个,..." 等噪声，保留但压缩
        d = d.replace("|", "")
        d = re.sub(r"\s+", " ", d).strip()
        if not d:
            continue
        picked.append(d)
        if len(picked) >= max_defs:
            break
    gloss = "; ".join(picked)
    gloss = FORBIDDEN_IN_COMMENT.sub(" ", gloss)
    if len(gloss) > max_len:
        gloss = gloss[: max_len - 1].rstrip() + "…"
    return gloss.strip()


def build_entries(
    cedict_path: str,
    rime_codes: Dict[str, List[str]],
    max_defs: int,
    max_len: int,
    allow_unmatched: bool,
) -> List[Tuple[str, str, str]]:
    """合成 (词, 拼音编码, 英文注释) 列表。

    allow_unmatched=True 时，CEDICT 中有释义、但拼音词典里没有编码的词也会被保留，
    编码列用 CEDICT 自带的带调拼音（去声调）兜底——这能让覆盖率显著提升，
    但拼写规则与 luna_pinyin 可能不一致，需实测后决定是否启用。
    """
    entries: List[Tuple[str, str, str]] = []
    seen = set()
    for simp, pinyin, defs in iter_cedict(cedict_path):
        if not KEEP_WORD.match(simp):
            continue
        gloss = clean_gloss(defs, max_defs, max_len)
        if not gloss:
            continue
        codes = rime_codes.get(simp)
        if codes:
            # 多音字：为每个编码各出一条，Rime 会按权重排序、注释一致
            for code in codes:
                key = (simp, code)
                if key in seen:
                    continue
                seen.add(key)
                entries.append((simp, code, gloss))
        elif allow_unmatched or not rime_codes:
            # 无拼音词典（如 --format map）时，编码列仅作占位，不影响映射输出
            code = re.sub(r"[0-9]", "", pinyin).strip().lower()
            if not code:
                continue
            key = (simp, code)
            if key in seen:
                continue
            seen.add(key)
            entries.append((simp, code, gloss))
    return entries


def sort_entries(
    entries: List[Tuple[str, str, str]], word_order: Dict[str, int] | None, topn: int
) -> List[Tuple[str, str, str]]:
    """排序并截断。

    有拼音词典时按其在原词典中的出现顺序（近似词频）排序，保证 topn 留下的是常用词。
    """
    if word_order:
        entries.sort(key=lambda e: (word_order.get(e[0], len(word_order)), len(e[0]), e[0]))
    else:
        entries.sort(key=lambda e: (len(e[0]), e[0]))
    if topn > 0:
        entries = entries[:topn]
    return entries


# ----------------------------------------------------------------------
# 输出
# ----------------------------------------------------------------------


def write_rime_dict(path: str, name: str, version: str, entries: List[Tuple[str, str, str]]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# 拾词输入法 · 带英文注释的拼音词典\n")
        fh.write("# 由 tools/cedict_to_rime.py 自动生成，请勿手工编辑\n")
        fh.write("# 第三列（非数字）即候选 comment，由 Trime 显示在候选旁\n")
        fh.write("---\n")
        fh.write(f"name: {name}\n")
        fh.write(f'version: "{version}"\n')
        fh.write("sort: by_weight\n")
        fh.write("---\n")
        for word, code, gloss in entries:
            fh.write(f"{word}\t{code}\t{gloss}\n")


def write_map(path: str, entries: List[Tuple[str, str, str]]) -> None:
    """输出「中文<TAB>英文」映射，供 Lua / Android 层查表使用。"""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        for word, _code, gloss in entries:
            fh.write(f"{word}\t{gloss}\n")


def write_lua(path: str, entries: List[Tuple[str, str, str]]) -> None:
    """输出 Lua table，供 rime/lua/pickword_comment.lua 直接 require。

    形如：
        return {
          ["电脑"] = "computer",
          ...
        }
    """
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    dedup: Dict[str, str] = {}
    for word, _code, gloss in entries:
        dedup.setdefault(word, gloss)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("-- 自动生成，请勿手工编辑（tools/cedict_to_rime.py --format lua）\n")
        fh.write("return {\n")
        for word, gloss in dedup.items():
            # 转义 Lua 长字符串中的引号与反斜杠
            w = word.replace("\\", "\\\\").replace('"', '\\"')
            g = gloss.replace("\\", "\\\\").replace('"', '\\"')
            fh.write(f'  ["{w}"] = "{g}",\n')
        fh.write("}\n")


def write_sqlite(path: str, entries: List[Tuple[str, str, str]]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    if os.path.exists(path):
        os.remove(path)
    conn = sqlite3.connect(path)
    try:
        conn.execute("CREATE TABLE words (word TEXT PRIMARY KEY, gloss TEXT NOT NULL)")
        # 同一词多行时保留第一条
        dedup: Dict[str, str] = {}
        for word, _code, gloss in entries:
            dedup.setdefault(word, gloss)
        conn.executemany(
            "INSERT OR IGNORE INTO words (word, gloss) VALUES (?, ?)", list(dedup.items())
        )
        conn.execute("CREATE INDEX idx_words_word ON words(word)")
        conn.commit()
    finally:
        conn.close()


# ----------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="CC-CEDICT + Rime 拼音词典 → 带英文注释的 Rime 词典 / SQLite / 映射表",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=USAGE,
    )
    ap.add_argument("--cedict", required=True, help="CC-CEDICT 文件（cedict_ts.u8）路径")
    ap.add_argument(
        "--rime-dict",
        help="Rime 拼音词典（luna_pinyin.dict.yaml）路径；省略时必须用 --format map",
    )
    ap.add_argument("--out", required=True, help="输出文件路径")
    ap.add_argument(
        "--format",
        choices=("rime", "map", "lua"),
        default="rime",
        help=(
            "rime=带注释的 .dict.yaml；map=中文<TAB>英文 映射表；"
            "lua=Lua table（供 rime/lua/pickword_comment.lua 使用）（默认 rime）"
        ),
    )
    ap.add_argument("--sqlite", help="同时输出 SQLite（路线 B 用），指定 .db 路径")
    ap.add_argument("--name", default="pickword_en", help="词典 name（默认 pickword_en）")
    ap.add_argument("--version", default="0.1", help="词典 version（默认 0.1）")
    ap.add_argument("--max-defs", type=int, default=2, help="每条最多保留几条释义（默认 2）")
    ap.add_argument("--max-gloss", type=int, default=48, help="注释最大字符数（默认 48）")
    ap.add_argument("--topn", type=int, default=0, help="只保留前 N 条（0=不限制）")
    ap.add_argument(
        "--allow-unmatched",
        action="store_true",
        help="保留拼音词典中缺失编码的词，用 CEDICT 去调拼音兜底（覆盖率更高，需实测）",
    )
    args = ap.parse_args(argv)

    if not os.path.isfile(args.cedict):
        print(f"[错误] 找不到 CEDICT 文件：{args.cedict}", file=sys.stderr)
        return 2

    rime_codes: Dict[str, List[str]] = {}
    word_order: Dict[str, int] | None = None
    if args.rime_dict:
        if not os.path.isfile(args.rime_dict):
            print(f"[错误] 找不到 Rime 词典：{args.rime_dict}", file=sys.stderr)
            return 2
        rime_codes = load_rime_codes(args.rime_dict)
        word_order = {w: i for i, w in enumerate(rime_codes.keys())}
        print(f"[信息] 拼音词条：{len(rime_codes):,}")
    elif args.format == "rime":
        print("[错误] --format rime 需要 --rime-dict（词典必须带拼音编码）", file=sys.stderr)
        return 2
    elif args.allow_unmatched:
        print("[警告] 未提供 --rime-dict，将全部使用 CEDICT 自带拼音兜底", file=sys.stderr)

    entries = build_entries(
        args.cedict, rime_codes, args.max_defs, args.max_gloss, args.allow_unmatched
    )
    entries = sort_entries(entries, word_order, args.topn)
    if not entries:
        print("[错误] 没有产出任何词条，请检查输入文件路径与格式", file=sys.stderr)
        return 1

    if args.format == "rime":
        write_rime_dict(args.out, args.name, args.version, entries)
    elif args.format == "lua":
        write_lua(args.out, entries)
    else:
        write_map(args.out, entries)
    if args.sqlite:
        write_sqlite(args.sqlite, entries)

    print(f"[完成] 词条 {len(entries):,} 条 → {args.out}")
    if args.sqlite:
        print(f"[完成] SQLite → {args.sqlite}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
