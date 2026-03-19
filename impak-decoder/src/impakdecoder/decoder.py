from __future__ import annotations

import json
from pathlib import Path
from typing import Iterator, List, Union

from PIL import Image

from .differ import reconstruct
from .formats import (
    FILE_HEADER_SIZE,
    FRAME_INDEX_ENTRY_SIZE,
    FRAME_KEYFRAME,
    FRAME_DELTA,
    MODE_NAMES,
    CODEC_NAMES,
    unpack_file_header,
    unpack_index_entry,
    unpack_patch_header,
    PATCH_HEADER_SIZE,
    MAGIC,
)


class ImpakReader:
    def __init__(self, path: Union[str, Path]):
        self.path = Path(path)
        self._fh = open(self.path, "rb")
        self._header: dict = {}
        self._index: list[dict] = []
        self._name_map: dict[str, int] = {}   # name → content_id
        self._meta_cache: dict[int, dict] = {}
        self._decode_cache: dict[int, Image.Image] = {}

        self._read_header()
        self._read_index()

        self._baseline_count: int = self._detect_baseline_count()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def close(self):
        if self._fh and not self._fh.closed:
            self._fh.close()

    def __len__(self) -> int:
        """Number of content frames (baseline frames are not counted)."""
        return self._header["frame_count"] - self._baseline_count

    def __getitem__(self, key: Union[int, str]) -> Image.Image:
        if isinstance(key, str):
            self._build_name_map()
            if key not in self._name_map:
                raise KeyError(f"Frame name '{key}' not found in collection")
            abs_id = self._name_map[key]
            return self._decode_frame(abs_id)

        if key < 0:
            key = len(self) + key
        if not (0 <= key < len(self)):
            raise IndexError(f"Frame index {key} out of range (0..{len(self)-1})")
        return self._decode_frame(self._content_to_abs(key))

    def __iter__(self) -> Iterator[Image.Image]:
        for i in range(len(self)):
            yield self._decode_frame(self._content_to_abs(i))

    def get_metadata(self, frame_id: int) -> dict:
        return self._read_metadata(self._content_to_abs(frame_id))

    def info(self) -> str:
        h = self._header
        mode_name = MODE_NAMES.get(h["diff_mode"], "unknown")
        codec_name = CODEC_NAMES.get(h.get("codec", 0), "png")
        quality = h.get("quality", 100)
        quality_str = "lossless" if quality == 100 else f"quality={quality}"
        total_bytes = self.path.stat().st_size
        n_content = len(self)
        lines = [
            f"File    : {self.path}",
            f"Format  : impak v{h['version']}",
            f"Diff    : {mode_name}",
            f"Codec   : {codec_name} ({quality_str})",
            f"Canvas  : {h['width']} × {h['height']} px",
            f"Frames  : {n_content}"
            + (f"  ({self._baseline_count} hidden baseline(s))" if self._baseline_count else ""),
            f"Size    : {total_bytes:,} bytes ({total_bytes/1024:.1f} KB)",
            "",
            f"{'ID':>4}  {'Type':>9}  {'Ref':>4}  {'Patches':>7}  {'Bytes':>9}  Name",
            "─" * 64,
        ]
        for content_id in range(n_content):
            abs_id = self._content_to_abs(content_id)
            entry = self._index[abs_id]
            ftype = "keyframe" if entry["frame_type"] == FRAME_KEYFRAME else "delta"
            ref_abs = entry["ref_frame_id"]
            if entry["frame_type"] == FRAME_DELTA:
                if ref_abs < self._baseline_count:
                    ref = f"B{ref_abs}"
                else:
                    ref = str(ref_abs - self._baseline_count)
            else:
                ref = "self"
            data_bytes = self._frame_data_size(abs_id)
            meta = self._read_metadata(abs_id)
            name = meta.get("name", "")
            lines.append(
                f"{content_id:>4}  {ftype:>9}  {ref:>4}  "
                f"{entry['patch_count']:>7}  {data_bytes:>9,}  {name}"
            )
        return "\n".join(lines)

    def diff_map(self, frame_id: int) -> list[tuple[int, int, int, int]]:
        abs_id = self._content_to_abs(frame_id)
        entry = self._index[abs_id]
        if entry["frame_type"] == FRAME_KEYFRAME:
            return [(0, 0, self._header["width"], self._header["height"])]
        patches = self._read_patches(abs_id)
        return [(x, y, w, h) for (x, y, w, h, _) in patches]

    @property
    def canvas_size(self) -> tuple[int, int]:
        return (self._header["width"], self._header["height"])

    @property
    def mode(self) -> str:
        return MODE_NAMES.get(self._header["diff_mode"], "unknown")

    @property
    def baseline_count(self) -> int:
        return self._baseline_count

    def _content_to_abs(self, content_id: int) -> int:
        return content_id + self._baseline_count

    def _decode_frame(self, abs_id: int) -> Image.Image:
        if abs_id in self._decode_cache:
            return self._decode_cache[abs_id].copy()

        entry = self._index[abs_id]
        codec = self.codec

        if entry["frame_type"] == FRAME_KEYFRAME:
            patches = self._read_patches(abs_id)
            assert len(patches) == 1
            img = self._decompress_patch_to_image(patches[0][4], codec)
        else:
            ref_id = entry["ref_frame_id"]
            ref_img = self._decode_frame(ref_id)
            patches = self._read_patches(abs_id)
            img = reconstruct(ref_img, patches, codec=codec)

        img = img.convert("RGBA")
        self._decode_cache[abs_id] = img
        return img.copy()

    def _read_header(self):
        self._fh.seek(0)
        raw = self._fh.read(FILE_HEADER_SIZE)
        if len(raw) < FILE_HEADER_SIZE:
            raise ValueError("File too small to be a valid .impak archive")
        h = unpack_file_header(raw)
        if h["magic"] != MAGIC:
            raise ValueError("Not a valid .impak file (bad magic bytes)")
        self._header = h

    def _read_index(self):
        self._fh.seek(self._header["index_offset"])
        count = self._header["frame_count"]
        self._index = []
        for _ in range(count):
            raw = self._fh.read(FRAME_INDEX_ENTRY_SIZE)
            self._index.append(unpack_index_entry(raw))

    def _read_patches(self, abs_id: int) -> list:
        entry = self._index[abs_id]
        self._fh.seek(entry["data_offset"])
        patches = []
        for _ in range(entry["patch_count"]):
            hdr = self._fh.read(PATCH_HEADER_SIZE)
            x, y, w, h, data_len = unpack_patch_header(hdr)
            data = self._fh.read(data_len)
            patches.append((x, y, w, h, data))
        return patches

    def _read_metadata(self, abs_id: int) -> dict:
        if abs_id in self._meta_cache:
            return self._meta_cache[abs_id]
        entry = self._index[abs_id]
        meta_len = entry["metadata_len"]
        if meta_len == 0:
            self._meta_cache[abs_id] = {}
            return {}
        self._fh.seek(entry["data_offset"])
        for _ in range(entry["patch_count"]):
            hdr = self._fh.read(PATCH_HEADER_SIZE)
            _, _, _, _, data_len = unpack_patch_header(hdr)
            self._fh.seek(data_len, 1)
        raw = self._fh.read(meta_len)
        try:
            meta = json.loads(raw.decode())
        except Exception:
            meta = {}
        self._meta_cache[abs_id] = meta
        return meta

    def _frame_data_size(self, abs_id: int) -> int:
        entry = self._index[abs_id]
        total = self._header["frame_count"]
        if abs_id + 1 < total:
            next_off = self._index[abs_id + 1]["data_offset"]
        else:
            next_off = self.path.stat().st_size
        return next_off - entry["data_offset"]

    def _decompress_patch_to_image(self, compressed: bytes, codec: str = "png") -> Image.Image:
        from .differ import _decode_patch
        return _decode_patch(compressed, codec=codec)

    def _detect_baseline_count(self) -> int:
        count = 0
        total = self._header["frame_count"]
        for abs_id in range(total):
            meta = self._read_metadata(abs_id)
            if meta.get("_baseline"):
                count += 1
            else:
                break
        return count

    @property
    def codec(self) -> str:
        return CODEC_NAMES.get(self._header.get("codec", 0), "png")

    @property
    def quality(self) -> int:
        return self._header.get("quality", 100)

    def _build_name_map(self):
        if self._name_map:
            return
        for content_id in range(len(self)):
            abs_id = self._content_to_abs(content_id)
            meta = self._read_metadata(abs_id)
            if "name" in meta:
                self._name_map[meta["name"]] = abs_id

    @classmethod
    def load_all(cls, path: Union[str, Path]) -> List[Image.Image]:
        with cls(path) as r:
            return list(r)

    @classmethod
    def load_frame(cls, path: Union[str, Path], frame_id: int) -> Image.Image:
        with cls(path) as r:
            return r[frame_id]
