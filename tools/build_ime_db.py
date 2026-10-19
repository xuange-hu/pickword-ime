#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_ime_db.py —— 构建拾词输入法的内置词库 pickword.db

数据来源
--------
1) CC-CEDICT（词汇主体）：提供「词 + 拼音 + 英文释义」，是候选与译词的主来源。
   格式：傳統 传统 [chuan2 tong3] /tradition/traditional/.../
2) Rime 明月拼音词典（单字补充）：CEDICT 未收录的字，用它补齐拼音，
   并作为词频的兜底依据。
   格式：字<TAB>拼音
3) 中文语料（可选，强烈推荐）：用于统计真实词频，让「我 / 我们 / 因为」按常用度排序。
   rime-essay 的 essay.txt 即可。没有语料时用词长与字典序兜底，排序会明显变差。

产出表（供 PinyinEngine 查询）
-----------------------------
    entries(word, pinyin, raw, initials, freq, gloss)
    · pinyin   : 空格分隔音节，如 "ni hao"（切分后的词组精确匹配）
    · raw      : 去空格全拼，如 "nihao"（整词精确 + 前缀范围匹配）
    · initials : 简拼，如 "nh"
    · freq     : 0–1000 的近似词频，越大越常用
    · gloss    : 英文译词，无则为空串

依赖：仅标准库。
"""
from __future__ import annotations

import argparse
import math
import os
import re
import sqlite3
import sys
import zipfile

CEDICT_LINE = re.compile(
    r"^(?P<trad>\S+)\s+(?P<simp>\S+)\s+\[(?P<pinyin>[^\]]+)\]\s*(?P<gloss>/.*/)?\s*$"
)
FORBIDDEN = re.compile(r"[\t\r\n]+")
# 只保留含汉字、无空格、长度 1–8 的词，过滤掉 "3C"、"XX" 之类噪声
WORD_KEEP = re.compile(r"^[一-鿿]{1,8}$")
NON_CHINESE = re.compile(r"[^一-鿿]")
TONE = re.compile(r"[0-9]+")


# ----------------------------------------------------------------------
# 解析
# ----------------------------------------------------------------------


def load_luna(path: str) -> list[tuple[str, str]]:
    """解析 Rime 词典，返回 [(词, 拼音)]，保留原顺序（近似字频序）。"""
    out: list[tuple[str, str]] = []
    sep_count = 0
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            stripped = line.strip()
            if stripped in ("---", "..."):
                sep_count += 1
                continue
            if sep_count < 2:
                continue
            if not stripped or stripped.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            word, code = parts[0].strip(), parts[1].strip()
            if not word or not code or not WORD_KEEP.match(word):
                continue
            out.append((word, code.lower()))
    return out


def load_cedict(path: str, max_defs: int, max_len: int) -> dict[str, tuple[str, str]]:
    """解析 CC-CEDICT，返回 {简体词: (拼音, 英文释义)}。"""
    result: dict[str, tuple[str, str]] = {}
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m = CEDICT_LINE.match(line)
            if not m:
                continue
            simp = m.group("simp")
            if not WORD_KEEP.match(simp) or simp in result:
                continue
            pinyin = " ".join(
                TONE.sub("", s) for s in m.group("pinyin").lower().split() if s
            )
            if not pinyin:
                continue
            defs: list[str] = []
            for d in (m.group("gloss") or "").split("/"):
                d = d.replace("|", "").strip()
                if not d or d.startswith("CL:"):
                    continue
                defs.append(re.sub(r"\s+", " ", d))
                if len(defs) >= max_defs:
                    break
            gloss = FORBIDDEN.sub(" ", "; ".join(defs)).strip()
            if len(gloss) > max_len:
                gloss = gloss[: max_len - 1].rstrip() + "…"
            result[simp] = (pinyin, gloss)
    return result


def load_corpus(path: str) -> str:
    """加载语料：纯文本或 zip（取其中最大的 .txt）。"""
    if path.lower().endswith(".zip"):
        with zipfile.ZipFile(path) as zf:
            best = None
            for info in zf.infolist():
                if info.is_dir() or info.filename.startswith("__MACOSX"):
                    continue
                if not info.filename.lower().endswith(".txt"):
                    continue
                if best is None or info.file_size > best.file_size:
                    best = info
            if best is None:
                return ""
            with zf.open(best) as fh:
                text = fh.read().decode("utf-8", errors="ignore")
    else:
        with open(path, "r", encoding="utf-8", errors="ignore") as fh:
            text = fh.read()
    # 去掉标点/空白/非汉字，避免跨标点匹配
    return NON_CHINESE.sub("", text)


def load_freq_dict(path: str) -> dict[str, int]:
    """解析 jieba 词典（word freq pos），返回 {词: 频次}。现代汉语词频，用于候选排序。"""
    table: dict[str, int] = {}
    with open(path, "r", encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) < 2:
                continue
            word = parts[0]
            try:
                value = int(float(parts[1]))
            except ValueError:
                continue
            if value > 0 and WORD_KEEP.match(word):
                table[word] = value
    return table


def count_words(corpus: str, words: set[str], max_word_len: int = 6) -> dict[str, int]:
    """用词表中所有词在语料里做滑窗匹配，得到近似词频。"""
    by_len: dict[int, set[str]] = {}
    for w in words:
        by_len.setdefault(len(w), set()).add(w)
    counts: dict[str, int] = {}
    n = len(corpus)
    limit = min(max_word_len, max(by_len.keys()) if by_len else 1)
    for i in range(n):
        for length in range(1, limit + 1):
            end = i + length
            if end > n:
                break
            bucket = by_len.get(length)
            if not bucket:
                continue
            sub = corpus[i:end]
            if sub in bucket:
                counts[sub] = counts.get(sub, 0) + 1
    return counts


def initials_of(pinyin: str) -> str:
    return "".join(s[0] for s in pinyin.split() if s)


# ----------------------------------------------------------------------
# 构建
# ----------------------------------------------------------------------


def build(args) -> int:
    for path in (args.cedict, args.luna):
        if not os.path.isfile(path):
            print(f"[错误] 找不到文件：{path}", file=sys.stderr)
            return 2

    luna = load_luna(args.luna)
    luna_map = dict(luna)
    print(f"[信息] 拼音单字：{len(luna):,}")

    cedict = load_cedict(args.cedict, args.max_defs, args.max_gloss)
    print(f"[信息] CEDICT 词条：{len(cedict):,}")

    # 词汇 = CEDICT（含拼音与英文） + CEDICT 未收录的字（来自拼音词典）
    vocab: dict[str, tuple[str, str]] = dict(cedict)
    gloss_count = sum(1 for v in vocab.values() if v[1])
    for word, pinyin in luna:
        if word not in vocab:
            vocab[word] = (pinyin, "")
    print(f"[信息] 合并词条：{len(vocab):,}（带英文 {gloss_count:,}）")

    # 词频：现代词频表（jieba）> 语料统计 > 字频顺序兜底
    freq: dict[str, int] = {}
    if args.freq_dict and os.path.isfile(args.freq_dict):
        table = load_freq_dict(args.freq_dict)
        common = {w: f for w, f in table.items() if w in vocab}
        print(f"[信息] 现代词频命中：{len(common):,} / 词典 {len(table):,}")
        if common:
            top = max(common.values())
            log_top = math.log(1 + top)
            for w, f in common.items():
                freq[w] = int(1000 * math.log(1 + f) / log_top)

    if args.corpus and os.path.exists(args.corpus):
        corpus = load_corpus(args.corpus)
        print(f"[信息] 语料字数：{len(corpus):,}")
        if corpus:
            counts = count_words(corpus, set(vocab.keys()))
            if counts:
                top = max(counts.values())
                log_top = math.log(1 + top)
                for w, c in counts.items():
                    # 只补现代词频表里没有的词，并压低上限，避免古语词盖过常用词
                    if w not in freq:
                        freq[w] = min(450, int(450 * math.log(1 + c) / log_top))

    # 仍未命中的词：单字用拼音词典顺序，其余按词长给一个低位兜底
    luna_index = {}
    for i, (w, _p) in enumerate(luna):
        luna_index.setdefault(w, i)
    for w in vocab:
        if w in freq:
            continue
        if w in luna_index:
            freq[w] = int(1000 * (len(luna) - luna_index[w]) / max(1, len(luna)))
        else:
            freq[w] = max(1, 300 - (len(w) - 1) * 60)

    rows = []
    for word in sorted(vocab.keys()):
        pinyin, gloss = vocab[word]
        value = max(0, min(1000, freq.get(word, 1)))
        rows.append((word, pinyin, pinyin.replace(" ", ""), initials_of(pinyin),
                     value, gloss))

    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)
    if os.path.exists(args.out):
        os.remove(args.out)

    conn = sqlite3.connect(args.out)
    try:
        conn.execute("PRAGMA journal_mode=OFF")
        conn.execute("PRAGMA synchronous=OFF")
        conn.execute("PRAGMA page_size=4096")
        conn.execute(
            "CREATE TABLE entries ("
            "word TEXT, pinyin TEXT, raw TEXT, initials TEXT, freq INTEGER, gloss TEXT)"
        )
        conn.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
        conn.executemany(
            "INSERT INTO entries (word, pinyin, raw, initials, freq, gloss) "
            "VALUES (?,?,?,?,?,?)",
            rows,
        )
        covered = sum(1 for r in rows if r[5])
        for k, v in (
            ("source_luna", os.path.basename(args.luna)),
            ("source_cedict", os.path.basename(args.cedict)),
            ("source_corpus", os.path.basename(args.corpus) if args.corpus else ""),
            ("entries", str(len(rows))),
            ("gloss_entries", str(covered)),
        ):
            conn.execute("INSERT INTO meta (key, value) VALUES (?,?)", (k, v))
        conn.commit()
        conn.execute("CREATE INDEX idx_entries_raw ON entries(raw)")
        conn.execute("CREATE INDEX idx_entries_pinyin ON entries(pinyin)")
        conn.execute("CREATE INDEX idx_entries_initials ON entries(initials)")
        conn.commit()
        conn.execute("VACUUM")
    finally:
        conn.close()

    size = os.path.getsize(args.out)
    print(f"[完成] {args.out} · 词条 {len(rows):,} · 带英文 {covered:,} · "
          f"{size / 1024 / 1024:.1f} MB")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="生成拾词输入法的内置词库 pickword.db",
    )
    ap.add_argument("--luna", required=True, help="luna_pinyin.dict.yaml 路径")
    ap.add_argument("--cedict", required=True, help="CC-CEDICT 文本路径")
    ap.add_argument("--freq-dict", help="现代词频表（jieba dict.txt：词 频次 词性）")
    ap.add_argument("--corpus", help="用于词频统计的语料（.txt 或 zip，可选）")
    ap.add_argument(
        "--out",
        default="app/src/main/assets/pickword.db",
        help="输出 db 路径（默认 app/src/main/assets/pickword.db）",
    )
    ap.add_argument("--max-defs", type=int, default=2, help="每条保留几条英文释义（默认 2）")
    ap.add_argument("--max-gloss", type=int, default=40, help="释义最大字符数（默认 40）")
    return build(ap.parse_args(argv))


if __name__ == "__main__":
    raise SystemExit(main())
