from __future__ import annotations

import io
import zlib
from typing import List, Tuple

from PIL import Image

Patch = Tuple[int, int, int, int, bytes]

def _compress(raw_bytes: bytes) -> bytes:
    return zlib.compress(raw_bytes, level=6)


def _decompress(data: bytes) -> bytes:
    return zlib.decompress(data)


def _encode_crop(
    img: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    codec: str = "png",
    quality: int = 100,
) -> bytes:
    crop = img.crop((x, y, x + w, y + h))
    buf = io.BytesIO()
    if codec == "webp":
        lossless = quality == 100
        if lossless:
            crop.save(buf, format="WEBP", lossless=True)
        else:
            crop.save(buf, format="WEBP", lossless=False, quality=quality, method=4)
        return buf.getvalue()          # WebP is self-compressed; no extra zlib
    else:
        crop.save(buf, format="PNG", compress_level=1)
        return _compress(buf.getvalue())


def _decode_patch(data: bytes, codec: str = "png") -> Image.Image:
    if codec == "webp":
        return Image.open(io.BytesIO(data))
    else:
        raw = _decompress(data)
        return Image.open(io.BytesIO(raw))



def _tiles_to_rects(tiles, tile_size, img_w, img_h):
    rects = []
    for tr, tc in tiles:
        x = tc * tile_size
        y = tr * tile_size
        w = min(tile_size, img_w - x)
        h = min(tile_size, img_h - y)
        rects.append((x, y, w, h))
    return rects


def _merge_rects(rects, merge_gap, tile_size, img_w, img_h):
    if not rects:
        return []

    by_row: dict[int, list] = {}
    for (x, y, w, h) in rects:
        by_row.setdefault(y, []).append((x, y, w, h))

    merged_rows = []
    for y_key in sorted(by_row):
        row = sorted(by_row[y_key], key=lambda r: r[0])
        current = list(row[0])
        for rect in row[1:]:
            rx, ry, rw, rh = rect
            gap = rx - (current[0] + current[2])
            if gap <= merge_gap:
                new_right = max(current[0] + current[2], rx + rw)
                current[2] = new_right - current[0]
                current[3] = max(current[3], rh)
            else:
                merged_rows.append(tuple(current))
                current = [rx, ry, rw, rh]
        merged_rows.append(tuple(current))

    merged_rows.sort(key=lambda r: (r[0], r[1]))
    final = []
    used = [False] * len(merged_rows)
    for i, ra in enumerate(merged_rows):
        if used[i]:
            continue
        ax, ay, aw, ah = ra
        for j, rb in enumerate(merged_rows[i + 1:], i + 1):
            if used[j]:
                continue
            bx, by, bw, bh = rb
            if bx == ax and bw == aw:
                gap = by - (ay + ah)
                if gap <= merge_gap:
                    ah = by + bh - ay
                    used[j] = True
        final.append((ax, ay, aw, ah))
        used[i] = True

    clamped = []
    for (x, y, w, h) in final:
        x = max(0, x)
        y = max(0, y)
        w = min(w, img_w - x)
        h = min(h, img_h - y)
        if w > 0 and h > 0:
            clamped.append((x, y, w, h))
    return clamped


def reconstruct(base_img: Image.Image, patches: List[Patch], codec: str = "png") -> Image.Image:
    result = base_img.copy().convert("RGBA")
    for (x, y, w, h, compressed) in patches:
        patch_img = _decode_patch(compressed, codec=codec).convert("RGBA")
        result.paste(patch_img, (x, y))
    return result
