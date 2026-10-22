#!/usr/bin/env python3
# PC-side replication of pickword-ime v0.4.0 engine (Fuzzy + context boost)
# Mirrors Java PinyinEngine / Fuzzy / SyllableSplitter to verify before building.
import sqlite3, copy

DB = "app/src/main/assets/pickword.db"

# ---- SyllableSplitter (isSyllable) ----
SYL = set()
def _load_syllables():
    import re
    # pull the SYLLABLES array from the Java source for exact parity
    src = open("app/src/main/java/com/pickword/ime/engine/SyllableSplitter.java", encoding="utf-8").read()
    block = src.split("String[] SYLLABLES = {",1)[1].split("};",1)[0]
    for m in re.findall(r'"([a-z]+)"', block):
        SYL.add(m)
_load_syllables()
def is_syllable(s): return s in SYL

# ---- Fuzzy ----
INITIAL_CLASSES = [{"z","zh"},{"c","ch"},{"s","sh"},{"n","l","r"},{"f","h"}]
FINAL_CLASSES = [{"an","ang"},{"en","eng"},{"in","ing"}]

def split_initial(syl):
    if syl.startswith("zh") or syl.startswith("ch") or syl.startswith("sh"):
        return syl[:2]
    c = syl[0]
    if "bpmfdtngkhjqxrlzcs".find(c) >= 0:
        return c
    return ""

def expand_in(part, classes):
    for cls in classes:
        if part in cls:
            return set(cls)
    return {part}

def expand_final(fin):
    for cls in FINAL_CLASSES:
        if fin in cls:
            return set(cls)
    if len(fin) > 1:
        if fin.startswith("y") or fin.startswith("w"):
            glide = fin[0]
            core = fin[1:]
            for cls in FINAL_CLASSES:
                if core in cls:
                    return {glide+v for v in cls}
    return {fin}

def fuzzy_expand(syllable):
    if not syllable: return set()
    ini = split_initial(syllable)
    fin = syllable[len(ini):]
    initials = expand_in(ini, INITIAL_CLASSES)
    finals = expand_final(fin)
    res = set()
    for a in initials:
        for b in finals:
            res.add(a+b)
    res.add(syllable)
    return res

def fuzzy_raw_variants(raw):
    # single-syllable split assumption for test (use split() equivalent)
    # emulate split(): take minimal syllable count split. We'll just test known splits.
    return None

# minimal syllable splitter (parity with Java for our test inputs)
import functools
@functools.lru_cache(maxsize=None)
def split_min(raw):
    n=len(raw); best=[None]*(n+1); best[n]=[[]]
    for i in range(n-1,-1,-1):
        best[i]=[]
        for j in range(min(n,i+6),i,-1):
            seg=raw[i:j]
            if seg in SYL and best[j] is not None:
                for tail in best[j]:
                    best[i].append([seg]+tail)
    allp=best[0]
    if not allp: return []
    mn=min(len(p) for p in allp)
    return [p for p in allp if len(p)==mn][:8]

def fuzzy_raw_variants(raw):
    splits=split_min(raw)
    if not splits: return set()
    syllables=splits[0]
    options=[]
    for syl in syllables:
        expanded=fuzzy_expand(syl)
        valid={v for v in expanded if is_syllable(v)}
        options.append(valid)
    res=set()
    def combine(idx,cur):
        if len(res)>=16: return
        if idx==len(options):
            res.add(cur); return
        for s in options[idx]:
            combine(idx+1,cur+s)
            if len(res)>=16: return
    combine(0,"")
    res.discard(raw)
    return res

# ---- Engine scoring mirror ----
SCORE_EXACT=1200; SCORE_PHRASE=1000; SCORE_SINGLE=950; SCORE_COMPOSED=1080
SCORE_PREFIX=700; SCORE_INITIALS=500; SCORE_FUZZY=760; RARE_PENALTY=600
CONTEXT_BOOST_PER=40; CONTEXT_BOOST_CAP=260

def boost(f):
    v=abs(f)
    if v>1000: v=1000
    return v//10

def contains_rare(s):
    for ch in s:
        o=ord(ch)
        if 0x4E00<=o<=0x9FFF:
            try:
                if len(ch.encode("gb2312"))!=2: return True
            except Exception:
                return True
    return False

con=sqlite3.connect(DB)
cur=con.cursor()

def query(raw, limit=10, context=None, fuzzy=True, cooc=None):
    r=raw.lower()
    merged={}
    def collect(sql,args,base,rawlen=False,length_penalty=False):
        cur.execute(sql,args)
        for word,gloss,freq,rawcode in cur.fetchall():
            plen=len(rawcode) if rawcode else len(r)
            score=base+boost(freq)
            if length_penalty:
                score-=(plen-len(r))*12
            if contains_rare(word): score-=RARE_PENALTY
            if word not in merged or score>merged[word][1]:
                merged[word]=(gloss,score)
    # 1 exact
    collect("SELECT word,gloss,freq,raw FROM entries WHERE raw = ?",[r],SCORE_EXACT)
    # 2 phrase + single
    for syl in split_min(r):
        pinyin=" ".join(syl)
        collect("SELECT word,gloss,freq,raw FROM entries WHERE pinyin = ?",[pinyin],SCORE_PHRASE)
        if syl:
            collect("SELECT word,gloss,freq,raw FROM entries WHERE pinyin = ?",[syl[0]],SCORE_SINGLE)
    # 3 prefix
    if r:
        upper=r[:-1]+chr(ord(r[-1])+1)
        collect("SELECT word,gloss,freq,raw FROM entries WHERE raw >= ? AND raw < ?",[r,upper],SCORE_PREFIX,length_penalty=True)
    # 4 initials
    if len(r)>1:
        collect("SELECT word,gloss,freq,raw FROM entries WHERE initials = ?",[r],SCORE_INITIALS)
    # 6 fuzzy
    if fuzzy:
        variants=fuzzy_raw_variants(r)
        if variants:
            ph=",".join("?"*len(variants))
            args=list(variants)
            cur.execute(f"SELECT word,gloss,freq,raw FROM entries WHERE raw IN ({ph})",args)
            for word,gloss,freq,rawcode in cur.fetchall():
                score=SCORE_FUZZY+boost(freq)
                if contains_rare(word): score-=RARE_PENALTY
                if word not in merged or score>merged[word][1]:
                    merged[word]=(gloss,score)
    # context boost
    if context and cooc:
        out=[]
        for word,(gloss,score) in merged.items():
            add=min(cooc.get(word,0)*CONTEXT_BOOST_PER, CONTEXT_BOOST_CAP)
            out.append((word,gloss,score+add))
    else:
        out=[(w,g,s) for w,(g,s) in merged.items()]
    out.sort(key=lambda x:-x[2])
    return out[:limit]

def top_words(res):
    return [(w,s) for w,g,s in res]

print("="*60)
print("FUZZY EXPAND sanity:")
for t in ["yin","ying","wang","wan","hao","zong","feng","si","shi","women","hao"]:
    print(f"  {t:8s} -> {sorted(fuzzy_raw_variants(t)) if t in ('women','hao','woshi') else sorted(fuzzy_expand(t))}")

print("="*60)
print("fuzzy_raw_variants (multi-syllable now works?):")
for t in ["woshi","womenhao","zongguo"]:
    print(f"  {t:10s} -> {sorted(fuzzy_raw_variants(t))}")

print("="*60)
print("QUERY tests (fuzzy ON):")
for q in ["zong","hao","si","women","woshi"]:
    res=query(q,12)
    print(f"\n  query '{q}' top12:")
    for w,s in top_words(res):
        print(f"     {w:6s} {s}")

print("="*60)
print("CONTEXT BOOST test: query 'hao' with prev='women' (cooc women->hao, women->hao?):")
# simulate cooc: after typing 我们, hao should boost
cooc={"好":3,"号":5,"耗":1}
res=query("hao",12,context="我们",cooc=cooc)
print("  top12 with context(我们):")
for w,s in top_words(res):
    print(f"     {w:6s} {s}")
print("  (without context for comparison):")
res2=query("hao",12)
for w,s in top_words(res2):
    print(f"     {w:6s} {s}")
con.close()
print("\nDONE")
