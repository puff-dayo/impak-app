from __future__ import annotations

from PySide6.QtCore import (
    Qt,
    Signal,
)
from PySide6.QtGui import (
    QPixmap,
)
from PySide6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QVBoxLayout,
    QWidget,
)


class ThumbItemWidget(QWidget):
    clicked = Signal(int)

    def __init__(self, idx: int, pixmap: QPixmap, name: str, is_keyframe: bool, parent=None):
        super().__init__(parent)
        self.idx = idx
        self.setObjectName("ThumbItem")
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 6, 8, 6)
        layout.setSpacing(4)

        image_label = QLabel()
        image_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        image_label.setPixmap(pixmap)
        layout.addWidget(image_label)

        bottom = QHBoxLayout()
        bottom.setContentsMargins(0, 0, 0, 0)

        idx_label = QLabel(f"#{idx}")
        idx_label.setObjectName("ThumbMeta")

        type_label = QLabel("KEY" if is_keyframe else "Δ")
        type_label.setObjectName("ThumbTypeKey" if is_keyframe else "ThumbTypeDelta")

        bottom.addWidget(idx_label)
        bottom.addStretch(1)
        bottom.addWidget(type_label)

        layout.addLayout(bottom)

    def mousePressEvent(self, event) -> None:
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit(self.idx)
        super().mousePressEvent(event)
