from __future__ import annotations

from typing import Optional

from PySide6.QtCore import (
    Qt,
    QSize,
    Signal,
    QPoint,
)
from PySide6.QtGui import (
    QImage,
    QPixmap,
    QWheelEvent,
)
from PySide6.QtWidgets import (
    QFrame,
    QLabel,
    QScrollArea,
    QWidget,
    QSizePolicy,
)


class ImageView(QScrollArea):
    mouse_pos_changed = Signal(str)

    def __init__(self, parent: Optional[QWidget] = None):
        super().__init__(parent)
        self.setWidgetResizable(False)
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setBackgroundRole(self.palette().ColorRole.Dark)

        self._label = QLabel()
        self._label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._label.setMouseTracking(True)
        self._label.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        self.setWidget(self._label)

        self._base_pixmap: Optional[QPixmap] = None
        self._base_size: Optional[QSize] = None
        self._scale: float = 1.0
        self._fit_mode: bool = False

        self._dragging = False
        self._drag_start = QPoint()
        self._h_start = 0
        self._v_start = 0

        self.viewport().setMouseTracking(True)
        self._label.installEventFilter(self)
        self.viewport().installEventFilter(self)

    def clear(self) -> None:
        self._base_pixmap = None
        self._base_size = None
        self._label.clear()
        self._label.resize(1, 1)
        self.mouse_pos_changed.emit("")

    def set_qimage(self, qimg: QImage) -> None:
        pixmap = QPixmap.fromImage(qimg)
        self._base_pixmap = pixmap
        self._base_size = pixmap.size()
        self._render()

    def set_scale(self, scale: float, fit_mode: bool = False) -> None:
        self._scale = max(0.05, min(8.0, scale))
        self._fit_mode = fit_mode
        self._render()

    def zoom_in(self, step: float = 0.25) -> float:
        if self._fit_mode:
            self._scale = 1.0
            self._fit_mode = False
        self._scale = min(8.0, self._scale + step)
        self._render()
        return self._scale

    def zoom_out(self, step: float = 0.25) -> float:
        if self._fit_mode:
            self._scale = 1.0
            self._fit_mode = False
        self._scale = max(0.05, self._scale - step)
        self._render()
        return self._scale

    def zoom_100(self) -> None:
        self._fit_mode = False
        self._scale = 1.0
        self._render()

    def zoom_fit(self) -> None:
        self._fit_mode = True
        self._render()

    def current_scale(self) -> float:
        if not self._base_pixmap:
            return 1.0
        if self._fit_mode:
            return self._fit_scale()
        return self._scale

    def wheelEvent(self, event: QWheelEvent) -> None:
        if event.modifiers() & Qt.KeyboardModifier.ControlModifier:
            if event.angleDelta().y() > 0:
                self.zoom_in(0.10)
            else:
                self.zoom_out(0.10)
            event.accept()
            return
        super().wheelEvent(event)

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        if self._fit_mode and self._base_pixmap:
            self._render()

    def eventFilter(self, obj, event):
        et = event.type()

        if et == event.Type.MouseMove:
            self._handle_mouse_move(event)
            if self._dragging:
                delta = event.globalPosition().toPoint() - self._drag_start
                self.horizontalScrollBar().setValue(self._h_start - delta.x())
                self.verticalScrollBar().setValue(self._v_start - delta.y())
            return False

        if et == event.Type.MouseButtonPress and event.button() == Qt.MouseButton.LeftButton:
            self._dragging = True
            self._drag_start = event.globalPosition().toPoint()
            self._h_start = self.horizontalScrollBar().value()
            self._v_start = self.verticalScrollBar().value()
            self.viewport().setCursor(Qt.CursorShape.ClosedHandCursor)
            return False

        if et == event.Type.MouseButtonRelease and event.button() == Qt.MouseButton.LeftButton:
            self._dragging = False
            self.viewport().setCursor(Qt.CursorShape.ArrowCursor)
            return False

        return super().eventFilter(obj, event)

    def _fit_scale(self) -> float:
        if not self._base_size:
            return 1.0
        vw = max(1, self.viewport().width() - 8)
        vh = max(1, self.viewport().height() - 8)
        return min(vw / self._base_size.width(), vh / self._base_size.height(), 1.0)

    def _render(self) -> None:
        if not self._base_pixmap or not self._base_size:
            return

        scale = self._fit_scale() if self._fit_mode else self._scale
        new_w = max(1, int(self._base_size.width() * scale))
        new_h = max(1, int(self._base_size.height() * scale))

        scaled = self._base_pixmap.scaled(
            new_w,
            new_h,
            Qt.AspectRatioMode.IgnoreAspectRatio,
            Qt.TransformationMode.FastTransformation if scale > 2.0
            else Qt.TransformationMode.SmoothTransformation,
        )
        self._label.setPixmap(scaled)
        self._label.resize(scaled.size())

    def _handle_mouse_move(self, event) -> None:
        if not self._base_size or not self._label.pixmap():
            self.mouse_pos_changed.emit("")
            return

        source = event.pos()
        x = int(source.x() / self.current_scale())
        y = int(source.y() / self.current_scale())

        if 0 <= x < self._base_size.width() and 0 <= y < self._base_size.height():
            self.mouse_pos_changed.emit(f"{x}, {y}")
        else:
            self.mouse_pos_changed.emit("")
