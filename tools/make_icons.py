#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_icons.py —— 生成拾词输入法的应用图标（纯标准库，手工绘制 PNG）

图标含义：品牌绿底 + 白色「候选条」+ 下方「键盘条」，一眼能认出是输入法。
输出：mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 下的 ic_launcher.png

用法：
    python tools/make_icons.py                 # 输出到 app/src/main/res
    python tools/make_icons.py --out /tmp/icon # 单独输出一个 192px 预览
"""
from __future__ import annotations

import argparse
import os
import struct
import zlib

BRAND = (14, 124, 102)      # #0E7C66
WHITE = (255, 255, 255)
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

SUB = 3  # 每像素 3x3 超采样，用于抗锯齿


def rounded_rect_hit(x: float, y: float, x0: float, y0: float, x1: float, y1: float,
                     r: float) -> bool:
    """点 (x, y) 是否落在圆角矩形内。"""
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    dx = min(x - x0, x1 - x)
    dy = min(y - y0, y1 - y)
    if dx < r and dy < r:
        return (r - dx) ** 2 + (r - dy) ** 2 <= r * r
    return True


def render(size: int) -> bytes:
    """渲染一个 size×size 的 RGBA 图像，返回原始像素（每行前置过滤字节 0）。"""
    rows = []
    bg_radius = size * 0.22
    # 候选条：宽 62%，高 16%，位于上方 36%
    bar = (size * 0.19, size * 0.30, size * 0.81, size * 0.46, size * 0.08)
    # 键盘条：宽 46%，高 11%，位于下方 62%
    kb = (size * 0.27, size * 0.56, size * 0.73, size * 0.67, size * 0.05)

    for py in range(size):
        row = bytearray()
        for px in range(size):
            # 3x3 超采样取平均颜色
            acc = [0, 0, 0, 0]
            for sy in range(SUB):
                for sx in range(SUB):
                    x = px + (sx + 0.5) / SUB
                    y = py + (sy + 0.5) / SUB
                    inside_bg = rounded_rect_hit(
                        x, y, 0, 0, size, size, bg_radius)
                    if not inside_bg:
                        continue
                    on_bar = rounded_rect_hit(x, y, *bar)
                    on_kb = rounded_rect_hit(x, y, *kb)
                    if on_bar:
                        acc[0] += WHITE[0]
                        acc[1] += WHITE[1]
                        acc[2] += WHITE[2]
                        acc[3] += 255
                    elif on_kb:
                        # 键盘条用 70% 不透明的白，形成层次
                        acc[0] += int(255 * 0.72)
                        acc[1] += int(255 * 0.72)
                        acc[2] += int(255 * 0.72)
                        acc[3] += int(255 * 0.72)
                    else:
                        acc[0] += BRAND[0]
                        acc[1] += BRAND[1]
                        acc[2] += BRAND[2]
                        acc[3] += 255
            total = SUB * SUB
            row += bytes((
                acc[0] // total if acc[3] else 0,
                acc[1] // total if acc[3] else 0,
                acc[2] // total if acc[3] else 0,
                acc[3] // total,
            ))
        rows.append(bytes(row))
    return b"".join(b"\x00" + r for r in rows)


def write_png(path: str, size: int, raw: bytes) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


def main() -> int:
    ap = argparse.ArgumentParser(description="生成拾词输入法图标")
    ap.add_argument("--res", default="app/src/main/res", help="res 目录路径")
    ap.add_argument("--preview", help="额外输出一个 192px 预览 PNG 的路径")
    args = ap.parse_args()

    for folder, size in SIZES.items():
        target = os.path.join(args.res, folder, "ic_launcher.png")
        write_png(target, size, render(size))
        print(f"[生成] {target} · {size}px")

    if args.preview:
        write_png(args.preview, 192, render(192))
        print(f"[生成] 预览 {args.preview}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
