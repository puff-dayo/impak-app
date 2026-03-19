from __future__ import annotations

import sys
from pathlib import Path
from typing import Optional

from PIL import Image
from PySide6.QtCore import (
    Qt,
    QSize,
)
from PySide6.QtGui import (
    QAction,
    QDragEnterEvent,
    QDropEvent,
    QImage,
    QKeySequence,
    QPixmap,
    QShortcut, QIcon,
)
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QFormLayout,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSplitter,
    QStatusBar,
    QToolBar,
    QVBoxLayout,
    QWidget,
    QSizePolicy,
    QAbstractItemView,
)
from impakdecoder import ImpakReader
from impakdecoder.formats import FRAME_KEYFRAME, MODE_NAMES

from model.thumbnail import ThumbnailLoader
from util.colors import Palette
from util.image import pil_to_qimage
from view.image_view import ImageView
from view.thumb_item import ThumbItemWidget


def resource_path(name: str) -> str:
    if getattr(sys, "frozen", False):
        base = Path(sys.executable).resolve().parent
    else:
        base = Path(__file__).resolve().parent
    return str(base / name)


class ImpakViewer(QMainWindow):
    def __init__(self, filepath: Optional[str] = None):
        super().__init__()

        self.setWindowTitle("impak Viewer")
        self.setWindowIcon(QIcon(resource_path("icon.png")))
        self.resize(1280, 800)
        self.setMinimumSize(800, 560)

        self._reader: Optional[ImpakReader] = None
        self._filepath: Optional[Path] = None
        self._current_idx: int = 0
        self._thumb_loader: Optional[ThumbnailLoader] = None

        self._current_pil_image: Optional[Image.Image] = None
        self._zoom_value: float = 1.0
        self._fit_mode: bool = True
        self._dark_mode: bool = True

        self.setAcceptDrops(True)

        self._setup_style()
        self._build_ui()
        self._build_actions()
        self._install_shortcuts()

        if filepath:
            self._open_file(filepath)
        else:
            self._show_welcome()

    def _setup_style(self) -> None:
        db = Palette.DARK_BG()
        pb = Palette.PANEL_BG()
        sb = Palette.SIDEBAR_BG()
        ac = Palette.ACCENT()
        am = Palette.ACCENT_MUTED()
        tp = Palette.TEXT_PRIMARY()
        tm = Palette.TEXT_MUTED()
        td = Palette.TEXT_DIM()
        br = Palette.BORDER()
        th = Palette.THUMB_HOVER()
        ptbg = Palette.PATCH_BG()

        self.setStyleSheet(f"""
            QMainWindow, QWidget {{
                background: {db};
                color: {tp};
                font-size: 11pt;
            }}
            QMenuBar {{
                background: {pb};
                color: {tp};
            }}
            QMenuBar::item:selected {{
                background: {am};
            }}
            QMenu {{
                background: {pb};
                color: {tp};
                border: 1px solid {br};
            }}
            QMenu::item:selected {{
                background: {am};
            }}
            QToolBar {{
                background: {pb};
                border: none;
                spacing: 4px;
                padding: 3px;
            }}
            QPushButton {{
                background: {pb};
                color: {tp};
                border: 1px solid {br};
                padding: 4px 8px;
                border-radius: 5px;
            }}
            QPushButton:hover {{
                background: {th};
            }}
            QPushButton#accentButton {{
                background: {am};
                border: 1px solid {am};
                font-weight: 600;
            }}
            QPushButton#accentButton:hover {{
                background: {ac};
            }}
            QLabel#sectionHeader {{
                color: {td};
                font-size: 9pt;
                font-weight: 700;
                letter-spacing: 1px;
            }}
            QLabel#muted {{
                color: {tm};
            }}
            QLabel#dim {{
                color: {td};
            }}
            QLabel#mono {{
                font-family: Consolas, "Courier New", monospace;
            }}
            QListWidget {{
                background: {sb};
                border: none;
                outline: none;
            }}
            QListWidget::item {{
                background: transparent;
                border: none;
                padding: 0px;
                margin: 1px;
            }}
            QListWidget::item:selected {{
                background: {db};
                border-left: 3px solid {ac};
            }}
            QWidget#ThumbItem {{
                background: transparent;
                border-radius: 6px;
            }}
            QWidget#ThumbItem:hover {{
                background: {th};
            }}
            QLabel#ThumbMeta {{
                color: {td};
                font-size: 8pt;
            }}
            QLabel#ThumbTypeKey {{
                color: {ac};
                font-size: 8pt;
                font-weight: 700;
            }}
            QLabel#ThumbTypeDelta {{
                color: {td};
                font-size: 8pt;
                font-weight: 700;
            }}
            QScrollArea {{
                border: none;
                background: {db};
            }}
            QListWidget#patchList {{
                background: {ptbg};
                color: {tm};
                border: 1px solid {br};
                font-family: Consolas, "Courier New", monospace;
                font-size: 10pt;
            }}
            QSplitter::handle {{
                background: {br};
            }}
            QStatusBar {{
                background: {pb};
                color: {td};
                border-top: 1px solid {br};
            }}
            QCheckBox {{
                color: {tm};
            }}
            QCheckBox::indicator:checked {{
                background: {am};
                border: 1px solid {ac};
            }}
            QScrollBar:vertical {{
                background: {pb};
                width: 12px;
                margin: 0;
                border: none;
            }}
            QScrollBar::handle:vertical {{
                background: {br};
                min-height: 24px;
                border-radius: 6px;
            }}
            QScrollBar::handle:vertical:hover {{
                background: {am};
            }}
            QScrollBar::add-line:vertical,
            QScrollBar::sub-line:vertical {{
                height: 0px;
                border: none;
                background: none;
            }}
            QScrollBar::add-page:vertical,
            QScrollBar::sub-page:vertical {{
                background: transparent;
            }}
            QScrollBar:horizontal {{
                background: {pb};
                height: 12px;
                margin: 0;
                border: none;
            }}
            QScrollBar::handle:horizontal {{
                background: {br};
                min-width: 24px;
                border-radius: 6px;
            }}
            QScrollBar::handle:horizontal:hover {{
                background: {am};
            }}
            QScrollBar::add-line:horizontal,
            QScrollBar::sub-line:horizontal {{
                width: 0px;
                border: none;
                background: none;
            }}
            QScrollBar::add-page:horizontal,
            QScrollBar::sub-page:horizontal {{
                background: transparent;
            }}
        """)

    def _build_ui(self) -> None:
        self._build_toolbar()

        splitter = QSplitter(Qt.Orientation.Horizontal)

        self._sidebar = self._build_sidebar()
        self._center = self._build_center()
        self._info_panel = self._build_info_panel()

        splitter.addWidget(self._sidebar)
        splitter.addWidget(self._center)
        splitter.addWidget(self._info_panel)
        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setStretchFactor(2, 0)
        splitter.setSizes([180, 900, 200])

        self.setCentralWidget(splitter)
        self._build_status_bar()

    def _build_toolbar(self) -> None:
        tb = QToolBar("Click to hide this bar")
        tb.setMovable(False)
        tb.setIconSize(QSize(16, 16))
        self.addToolBar(Qt.ToolBarArea.TopToolBarArea, tb)

        open_btn = QPushButton("Open")
        open_btn.setObjectName("accentButton")
        open_btn.clicked.connect(self._prompt_open)
        tb.addWidget(open_btn)

        tb.addSeparator()

        btn_first = QPushButton("<<")
        btn_prev = QPushButton("<")
        btn_next = QPushButton(">")
        btn_last = QPushButton(">>")
        btn_first.setToolTip("First")
        btn_prev.setToolTip("Previous")
        btn_next.setToolTip("Next")
        btn_last.setToolTip("Last")

        btn_first.clicked.connect(lambda: self._go_to(0))
        btn_prev.clicked.connect(self._prev_frame)
        btn_next.clicked.connect(self._next_frame)
        btn_last.clicked.connect(lambda: self._go_to(-1))

        tb.addWidget(btn_first)
        tb.addWidget(btn_prev)
        tb.addWidget(btn_next)
        tb.addWidget(btn_last)

        tb.addSeparator()

        self._frame_counter = QLabel("—  /  —")
        self._frame_counter.setObjectName("muted")
        tb.addWidget(self._frame_counter)

        spacer = QWidget()
        spacer.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        tb.addWidget(spacer)

        self._zoom_out_btn = QPushButton("−")
        self._zoom_in_btn = QPushButton("+")
        self._zoom_actual_btn = QPushButton("100%")
        self._zoom_fit_btn = QPushButton("Fit")
        self._zoom_label = QLabel("100%")
        self._zoom_label.setObjectName("muted")
        self._zoom_label.setFixedWidth(48)

        self._zoom_out_btn.clicked.connect(lambda: self._zoom_step(-0.25))
        self._zoom_in_btn.clicked.connect(lambda: self._zoom_step(+0.25))
        self._zoom_actual_btn.clicked.connect(self._zoom_100)
        self._zoom_fit_btn.clicked.connect(self._zoom_fit)

        tb.addSeparator()
        tb.addWidget(self._zoom_out_btn)
        tb.addWidget(self._zoom_label)
        tb.addWidget(self._zoom_in_btn)
        tb.addWidget(self._zoom_actual_btn)
        tb.addWidget(self._zoom_fit_btn)

        tb.addSeparator()
        self._theme_btn = QPushButton("Light")
        self._theme_btn.setToolTip("Switch to light theme")
        self._theme_btn.clicked.connect(self._toggle_theme)
        tb.addWidget(self._theme_btn)

    def _build_sidebar(self) -> QWidget:
        panel = QWidget()
        self._sidebar_panel = panel
        panel.setStyleSheet(f"background: {Palette.SIDEBAR_BG()};")

        layout = QVBoxLayout(panel)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        header = QWidget()
        header_layout = QHBoxLayout(header)
        header_layout.setContentsMargins(12, 8, 12, 8)
        header_layout.setSpacing(6)

        left = QLabel("FRAMES")
        left.setObjectName("sectionHeader")
        right = QLabel("")
        right.setObjectName("dim")
        self._frame_count_label = right

        header_layout.addWidget(left)
        header_layout.addStretch(1)
        header_layout.addWidget(right)

        divider = QFrame()
        self._sidebar_divider = divider
        divider.setFrameShape(QFrame.Shape.HLine)
        divider.setStyleSheet(f"background: {Palette.BORDER()}; max-height: 1px; border: none;")

        self._thumb_list = QListWidget()
        self._thumb_list.setObjectName("thumbList")
        self._thumb_list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self._thumb_list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self._thumb_list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self._thumb_list.currentRowChanged.connect(self._go_to)

        layout.addWidget(header)
        layout.addWidget(divider)
        layout.addWidget(self._thumb_list, 1)

        return panel

    def _build_center(self) -> QWidget:
        wrapper = QWidget()
        layout = QVBoxLayout(wrapper)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        self._image_view = ImageView()
        self._image_view.setStyleSheet(f"background: {Palette.CANVAS_BG()};")
        self._image_view.mouse_pos_changed.connect(self._set_coords)

        self._welcome_label = QLabel("Open an .impak file from File → Open\nor drag and drop a file here")
        self._welcome_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._welcome_label.setObjectName("dim")
        self._welcome_label.setStyleSheet(f"font-size: 16pt; color: {Palette.TEXT_DIM()};")
        self._welcome_label.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)

        container = QWidget()
        stack_layout = QVBoxLayout(container)
        stack_layout.setContentsMargins(0, 0, 0, 0)
        stack_layout.setSpacing(0)
        stack_layout.addWidget(self._image_view)

        overlay_layout = QVBoxLayout()
        overlay_layout.setContentsMargins(0, 0, 0, 0)
        overlay_layout.addWidget(self._welcome_label, alignment=Qt.AlignmentFlag.AlignCenter)
        stack_layout.addLayout(overlay_layout)

        layout.addWidget(container, 1)
        return wrapper

    def _build_info_panel(self) -> QWidget:
        panel = QWidget()
        self._info_panel_widget = panel
        panel.setStyleSheet(f"background: {Palette.PANEL_BG()};")
        panel.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Expanding)

        outer = QVBoxLayout(panel)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)

        header = QWidget()
        header_layout = QHBoxLayout(header)
        header_layout.setContentsMargins(12, 8, 12, 8)

        title = QLabel("INFO")
        title.setObjectName("sectionHeader")
        header_layout.addWidget(title)
        header_layout.addStretch(1)

        divider = QFrame()
        self._info_header_divider = divider
        divider.setFrameShape(QFrame.Shape.HLine)
        divider.setStyleSheet(f"background: {Palette.BORDER()}; max-height: 1px; border: none;")

        body_scroll = QScrollArea()
        self._info_body_scroll = body_scroll
        body_scroll.setWidgetResizable(True)
        body_scroll.setFrameShape(QFrame.Shape.NoFrame)
        body_scroll.setStyleSheet(f"background: {Palette.PANEL_BG()};")

        body = QWidget()
        body_scroll.setWidget(body)

        body_layout = QVBoxLayout(body)
        body_layout.setContentsMargins(14, 10, 14, 10)
        body_layout.setSpacing(8)

        form1 = QFormLayout()
        form1.setLabelAlignment(Qt.AlignmentFlag.AlignLeft)
        form1.setFormAlignment(Qt.AlignmentFlag.AlignTop)
        form1.setHorizontalSpacing(10)
        form1.setVerticalSpacing(6)

        self._lbl_file = QLabel("—")
        self._lbl_mode = QLabel("—")
        self._lbl_canvas = QLabel("—")
        self._lbl_frames = QLabel("—")
        self._lbl_baselines = QLabel("—")
        self._lbl_filesize = QLabel("—")

        for lbl in (
                self._lbl_file,
                self._lbl_mode,
                self._lbl_canvas,
                self._lbl_frames,
                self._lbl_baselines,
                self._lbl_filesize,
        ):
            lbl.setWordWrap(True)
            lbl.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)

        self._baseline_row_label = self._dim_label("Baselines")
        form1.addRow(self._dim_label("File"), self._lbl_file)
        form1.addRow(self._dim_label("Mode"), self._lbl_mode)
        form1.addRow(self._dim_label("Canvas"), self._lbl_canvas)
        form1.addRow(self._dim_label("Frames"), self._lbl_frames)
        form1.addRow(self._baseline_row_label, self._lbl_baselines)
        form1.addRow(self._dim_label("Size"), self._lbl_filesize)
        body_layout.addLayout(form1)
        body_layout.addWidget(self._divider_widget())

        form2 = QFormLayout()
        form2.setHorizontalSpacing(10)
        form2.setVerticalSpacing(6)

        self._lbl_fname = QLabel("—")
        self._lbl_ftype = QLabel("—")
        self._lbl_fref = QLabel("—")
        self._lbl_fpatches = QLabel("—")

        for lbl in (
                self._lbl_fname,
                self._lbl_ftype,
                self._lbl_fref,
                self._lbl_fpatches,
        ):
            lbl.setWordWrap(True)
            lbl.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)

        form2.addRow(self._dim_label("Name"), self._lbl_fname)
        form2.addRow(self._dim_label("Type"), self._lbl_ftype)
        form2.addRow(self._dim_label("Ref"), self._lbl_fref)
        form2.addRow(self._dim_label("Patches"), self._lbl_fpatches)
        body_layout.addLayout(form2)
        body_layout.addWidget(self._divider_widget())

        patch_hdr = QLabel("CHANGED REGIONS")
        patch_hdr.setObjectName("sectionHeader")
        body_layout.addWidget(patch_hdr)

        self._patch_list = QListWidget()
        self._patch_list.setObjectName("patchList")
        self._patch_list.setFixedHeight(160)
        body_layout.addWidget(self._patch_list)

        body_layout.addStretch(1)

        export_frame_btn = QPushButton("Export this frame")
        export_frame_btn.setObjectName("accentButton")
        export_frame_btn.clicked.connect(self._export_frame)

        export_all_btn = QPushButton("Export all frames")
        export_all_btn.clicked.connect(self._export_all)

        body_layout.addWidget(self._divider_widget())
        body_layout.addWidget(export_frame_btn)
        body_layout.addWidget(export_all_btn)

        outer.addWidget(header)
        outer.addWidget(divider)
        outer.addWidget(body_scroll, 1)
        return panel

    def _build_status_bar(self) -> None:
        sb = QStatusBar()
        self.setStatusBar(sb)

        self._status_label = QLabel("Ready")
        self._status_label.setObjectName("dim")

        self._coords_label = QLabel("")
        self._coords_label.setObjectName("dim")

        sb.addWidget(self._status_label, 1)
        sb.addPermanentWidget(self._coords_label)

    def _build_actions(self) -> None:
        self._act_open = QAction("Open…", self)
        self._act_export_frame = QAction("Export current frame…", self)
        self._act_export_all = QAction("Export all frames…", self)
        self._act_quit = QAction("Quit", self)

        self._act_fit = QAction("Fit to window", self)
        self._act_actual = QAction("Actual size", self)
        self._act_zoom_in = QAction("Zoom in", self)
        self._act_zoom_out = QAction("Zoom out", self)

        self._act_first = QAction("First frame", self)
        self._act_prev = QAction("Previous", self)
        self._act_next = QAction("Next", self)
        self._act_last = QAction("Last frame", self)

        self._act_open.triggered.connect(self._prompt_open)
        self._act_export_frame.triggered.connect(self._export_frame)
        self._act_export_all.triggered.connect(self._export_all)
        self._act_quit.triggered.connect(self.close)

        self._act_fit.triggered.connect(self._zoom_fit)
        self._act_actual.triggered.connect(self._zoom_100)
        self._act_zoom_in.triggered.connect(lambda: self._zoom_step(+0.2))
        self._act_zoom_out.triggered.connect(lambda: self._zoom_step(-0.2))

        self._act_first.triggered.connect(lambda: self._go_to(0))
        self._act_prev.triggered.connect(self._prev_frame)
        self._act_next.triggered.connect(self._next_frame)
        self._act_last.triggered.connect(lambda: self._go_to(-1))

    def _install_shortcuts(self) -> None:
        self._act_open.setShortcut(QKeySequence.StandardKey.Open)
        self._act_quit.setShortcut(QKeySequence.StandardKey.Quit)

        QShortcut(QKeySequence(Qt.Key.Key_Left), self, activated=self._prev_frame)
        QShortcut(QKeySequence(Qt.Key.Key_Right), self, activated=self._next_frame)
        QShortcut(QKeySequence(Qt.Key.Key_Home), self, activated=lambda: self._go_to(0))
        QShortcut(QKeySequence(Qt.Key.Key_End), self, activated=lambda: self._go_to(-1))
        QShortcut(QKeySequence("f"), self, activated=self._zoom_fit)
        QShortcut(QKeySequence("1"), self, activated=self._zoom_100)
        QShortcut(QKeySequence("+"), self, activated=lambda: self._zoom_step(+0.2))
        QShortcut(QKeySequence("-"), self, activated=lambda: self._zoom_step(-0.2))

    def _dim_label(self, text: str) -> QLabel:
        lbl = QLabel(text, wordWrap=True)
        lbl.setObjectName("dim")
        return lbl

    def _divider_widget(self) -> QWidget:
        line = QFrame()
        line.setFrameShape(QFrame.Shape.HLine)
        line.setStyleSheet(f"background: {Palette.BORDER()}; max-height: 1px; border: none;")
        return line

    def _prompt_open(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self,
            "Open .impak collection",
            "",
            "impak collections (*.impak);;All files (*.*)",
        )
        if path:
            self._open_file(path)

    def _open_file(self, path: str) -> None:
        try:
            if self._thumb_loader and self._thumb_loader.isRunning():
                self._thumb_loader.quit()
                self._thumb_loader.wait(1000)

            if self._reader:
                self._reader.close()

            self._reader = ImpakReader(path)
            self._filepath = Path(path)
            self._current_idx = 0
            self._thumb_list.clear()

            self.setWindowTitle(f"impak Viewer  —  {self._filepath.name}")

            self._update_file_info()
            self._build_thumbnails()
            self._go_to(0)
            n = len(self._reader)
            self._set_status(f"Opened  {self._filepath.name}  ({n} frames)")
        except Exception as exc:
            QMessageBox.critical(self, "Open failed", str(exc))

    def _show_welcome(self) -> None:
        self._image_view.clear()
        self._welcome_label.show()

    def _build_thumbnails(self) -> None:
        self._thumb_list.clear()

        if not self._reader or not self._filepath:
            return

        n = len(self._reader)
        self._frame_count_label.setText(str(n))
        self._set_status("Loading thumbnails…")

        self._thumb_loader = ThumbnailLoader(self._reader, self._filepath, self)
        self._thumb_loader.thumbnail_ready.connect(self._add_thumbnail_item)
        self._thumb_loader.status_text.connect(self._set_status)
        self._thumb_loader.failed.connect(lambda msg: self._set_status(f"Thumbnail load error: {msg}", error=True))
        self._thumb_loader.start()

    def _add_thumbnail_item(self, idx: int, qimg: QImage, name: str, is_keyframe: bool) -> None:
        pixmap = QPixmap.fromImage(qimg)

        item = QListWidgetItem()
        item.setSizeHint(QSize(160, 120))
        self._thumb_list.addItem(item)

        widget = ThumbItemWidget(idx, pixmap, name, is_keyframe)
        widget.clicked.connect(self._go_to)
        self._thumb_list.setItemWidget(item, widget)

    def _select_thumb(self, idx: int) -> None:
        if 0 <= idx < self._thumb_list.count():
            self._thumb_list.blockSignals(True)
            self._thumb_list.setCurrentRow(idx)
            self._thumb_list.blockSignals(False)
            self._thumb_list.scrollToItem(
                self._thumb_list.item(idx),
                QAbstractItemView.ScrollHint.PositionAtCenter,
            )

    def _go_to(self, idx: int) -> None:
        if not self._reader:
            return

        n = len(self._reader)
        if idx < 0:
            idx = n + idx
        idx = max(0, min(idx, n - 1))

        self._current_idx = idx
        self._frame_counter.setText(f"{idx + 1}  /  {n}")
        self._select_thumb(idx)
        self._load_and_show_frame(idx)
        self._update_frame_info(idx)

    def _prev_frame(self) -> None:
        self._go_to(self._current_idx - 1)

    def _next_frame(self) -> None:
        self._go_to(self._current_idx + 1)

    def _load_and_show_frame(self, idx: int) -> None:
        if not self._reader:
            return

        try:
            img = self._reader[idx]

            self._current_pil_image = img
            self._display_image(img)
        except Exception as exc:
            self._set_status(f"Error loading frame {idx}: {exc}", error=True)

    def _display_image(self, img: Image.Image) -> None:
        qimg = pil_to_qimage(img)
        self._image_view.set_qimage(qimg)
        self._welcome_label.hide()

        if self._fit_mode:
            self._image_view.zoom_fit()
            scale = self._image_view.current_scale()
        else:
            self._image_view.set_scale(self._zoom_value, fit_mode=False)
            scale = self._zoom_value

        self._zoom_label.setText(f"{int(scale * 100)}%")

    def _refresh_canvas(self) -> None:
        if self._reader:
            self._load_and_show_frame(self._current_idx)

    def _zoom_fit(self) -> None:
        self._fit_mode = True
        self._image_view.zoom_fit()
        self._zoom_label.setText(f"{int(self._image_view.current_scale() * 100)}%")
        self._zoom_value = self._image_view.current_scale()

    def _zoom_100(self) -> None:
        self._fit_mode = False
        self._zoom_value = 1.0
        self._image_view.zoom_100()
        self._zoom_label.setText("100%")

    def _zoom_step(self, delta: float) -> None:
        if self._fit_mode:
            self._fit_mode = False

        self._zoom_value = max(0.05, min(8.0, self._zoom_value + delta))
        self._image_view.set_scale(self._zoom_value, fit_mode=False)
        self._zoom_label.setText(f"{int(self._zoom_value * 100)}%")

    def _update_file_info(self) -> None:
        if not self._reader or not self._filepath:
            return

        h = self._reader._header
        size_bytes = self._filepath.stat().st_size
        size_str = (
            f"{size_bytes / 1024 / 1024:.1f} MB"
            if size_bytes > 1_000_000
            else f"{size_bytes / 1024:.1f} KB"
        )

        self._lbl_file.setText(self._filepath.name)
        self._lbl_mode.setText(MODE_NAMES.get(h["diff_mode"], "?"))
        self._lbl_canvas.setText(f"{h['width']} × {h['height']}")

        self._lbl_frames.setText(str(len(self._reader)))

        bc = self._reader.baseline_count
        has_baselines = bc > 0
        self._lbl_baselines.setText(str(bc) if has_baselines else "—")
        self._lbl_baselines.setStyleSheet(
            f"color: {Palette.WARNING()};" if has_baselines else ""
        )
        self._baseline_row_label.setVisible(has_baselines)
        self._lbl_baselines.setVisible(has_baselines)

        self._lbl_filesize.setText(size_str)

    def _update_frame_info(self, idx: int) -> None:
        if not self._reader:
            return

        abs_id = self._reader._content_to_abs(idx)
        entry = self._reader._index[abs_id]
        meta = self._reader.get_metadata(idx)

        is_keyframe = entry["frame_type"] == FRAME_KEYFRAME
        ftype = "keyframe" if is_keyframe else "delta"
        fcolor = Palette.ACCENT() if is_keyframe else Palette.SUCCESS()

        self._lbl_fname.setText(meta.get("name", f"frame_{idx:04d}"))
        self._lbl_ftype.setText(ftype)
        self._lbl_ftype.setStyleSheet(f"color: {fcolor};")

        if is_keyframe:
            ref_text = "self"
            ref_color = Palette.TEXT_DIM()
        else:
            ref_abs = entry["ref_frame_id"]
            bc = self._reader.baseline_count
            if ref_abs < bc:
                ref_text = f"B{ref_abs}"
                ref_color = Palette.WARNING()
            else:
                ref_text = str(ref_abs - bc)
                ref_color = Palette.TEXT_PRIMARY()

        self._lbl_fref.setText(ref_text)
        self._lbl_fref.setStyleSheet(f"color: {ref_color};")
        self._lbl_fpatches.setText(str(entry["patch_count"]))

        self._patch_list.clear()
        try:
            regions = self._reader.diff_map(idx)
            for x, y, w, h in regions:
                self._patch_list.addItem(f" {x:>4},{y:>4}  {w:>4}×{h:<4}")
        except Exception:
            pass

    def _export_frame(self) -> None:
        if not self._reader:
            return

        meta = self._reader.get_metadata(self._current_idx)
        default = meta.get("name", f"frame_{self._current_idx:04d}") + ".png"

        path, _ = QFileDialog.getSaveFileName(
            self,
            "Export frame",
            default,
            "PNG (*.png);;JPEG (*.jpg);;WebP (*.webp)",
        )
        if not path:
            return

        try:
            img = self._reader[self._current_idx]
            img.convert("RGB").save(path)
            self._set_status(f"Exported  {Path(path).name}")
        except Exception as exc:
            QMessageBox.critical(self, "Export failed", str(exc))

    def _export_all(self) -> None:
        if not self._reader:
            return

        folder = QFileDialog.getExistingDirectory(self, "Export all frames to folder")
        if not folder:
            return

        out = Path(folder)
        try:
            n = len(self._reader)
            self._set_status("Exporting…")
            QApplication.processEvents()

            for i in range(n):
                meta = self._reader.get_metadata(i)
                name = meta.get("name", f"frame_{i:04d}")
                img = self._reader[i]
                img.convert("RGB").save(out / f"{name}.png")

            self._set_status(f"Exported {n} frames to  {folder}")
        except Exception as exc:
            QMessageBox.critical(self, "Export failed", str(exc))

    # ── Drag and drop ──

    def dragEnterEvent(self, event: QDragEnterEvent) -> None:
        mime = event.mimeData()
        if mime.hasUrls():
            urls = mime.urls()
            if any(u.toLocalFile().lower().endswith(".impak") for u in urls):
                event.acceptProposedAction()
                return
        event.ignore()

    def dropEvent(self, event: QDropEvent) -> None:
        urls = event.mimeData().urls()
        for url in urls:
            path = url.toLocalFile()
            if path.lower().endswith(".impak"):
                self._open_file(path)
                event.acceptProposedAction()
                return
        event.ignore()

    # ── Theme toggle ──

    def _toggle_theme(self) -> None:
        Palette.set_theme("light" if Palette.is_dark() else "dark")
        self._dark_mode = Palette.is_dark()
        self._apply_theme()

    def _apply_theme(self) -> None:
        if self._dark_mode:
            self._theme_btn.setText("Light")
            self._theme_btn.setToolTip("Switch to light theme")
        else:
            self._theme_btn.setText("Dark")
            self._theme_btn.setToolTip("Switch to dark theme")

        self._setup_style()

        self._sidebar_panel.setStyleSheet(f"background: {Palette.SIDEBAR_BG()};")
        self._sidebar_divider.setStyleSheet(
            f"background: {Palette.BORDER()}; max-height: 1px; border: none;"
        )
        self._info_panel_widget.setStyleSheet(f"background: {Palette.PANEL_BG()};")
        self._info_header_divider.setStyleSheet(
            f"background: {Palette.BORDER()}; max-height: 1px; border: none;"
        )
        self._info_body_scroll.setStyleSheet(f"background: {Palette.PANEL_BG()};")
        self._image_view.setStyleSheet(f"background: {Palette.CANVAS_BG()};")
        self._welcome_label.setStyleSheet(f"font-size: 16pt; color: {Palette.TEXT_DIM()};")

        if self._reader:
            self._update_frame_info(self._current_idx)
            self._update_file_info()

        self._set_status(self._status_label.text())

    # ── Status ──

    def _set_status(self, msg: str, error: bool = False) -> None:
        color = Palette.DANGER() if error else Palette.TEXT_DIM()
        self._status_label.setText(msg)
        self._status_label.setStyleSheet(f"color: {color};")

    def _set_coords(self, text: str) -> None:
        self._coords_label.setText(text)


def main() -> int:
    app = QApplication(sys.argv)
    icon = QIcon(resource_path("icon.png"))
    app.setWindowIcon(icon)
    filepath = sys.argv[1] if len(sys.argv) > 1 else None
    window = ImpakViewer(filepath)
    window.show()

    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
