from __future__ import annotations

from pathlib import Path
from typing import Optional

from PIL import Image
from PySide6.QtCore import (
    QThread,
    Signal,
)
from PySide6.QtGui import (
    QImage,
)
from PySide6.QtWidgets import (
    QWidget,
)
from impakdecoder import ImpakReader
from impakdecoder.formats import FRAME_KEYFRAME


def make_thumbnail_qimage(img: Image.Image, width: int, height: int) -> QImage:
    thumb = img.convert("RGB")
    thumb.thumbnail((width, height), Image.Resampling.LANCZOS)
    return pil_to_qimage(thumb)


def pil_to_qimage(img: Image.Image) -> QImage:
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    data = img.tobytes("raw", "RGBA")
    qimg = QImage(data, img.width, img.height, QImage.Format.Format_RGBA8888)
    return qimg.copy()


class ThumbnailLoader(QThread):
    thumbnail_ready = Signal(int, QImage, str, bool)
    status_text = Signal(str)
    failed = Signal(str)

    def __init__(self, reader: ImpakReader, filepath: Path, parent: Optional[QWidget] = None):
        super().__init__(parent)
        self._reader = reader
        self._filepath = filepath

    def run(self) -> None:
        try:
            n = len(self._reader)
            for i in range(n):
                try:
                    img = self._reader[i]
                    qimg = make_thumbnail_qimage(img, 140, 88)

                    abs_id = self._reader._content_to_abs(i)
                    entry = self._reader._index[abs_id]

                    meta = self._reader.get_metadata(i)
                    name = meta.get("name", f"#{i}")
                    is_key = entry["frame_type"] == FRAME_KEYFRAME
                    self.thumbnail_ready.emit(i, qimg, name, is_key)
                except Exception:
                    continue
            self.status_text.emit(f"{self._filepath.name}  ·  {n} frames")
        except Exception as exc:
            self.failed.emit(str(exc))
