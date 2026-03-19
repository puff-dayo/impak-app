class Palette:
    _themes: dict = {
        "dark": {
            "DARK_BG": "#22222a",
            "PANEL_BG": "#22222a",
            "SIDEBAR_BG": "#1e1e26",
            "ACCENT": "#df6f8e",
            "ACCENT_HOVER": "#ff8fae",
            "ACCENT_MUTED": "#6a4158",
            "TEXT_PRIMARY": "#e8e6f0",
            "TEXT_MUTED": "#8a889a",
            "TEXT_DIM": "#6a6878",
            "BORDER": "#2e2e3a",
            "SUCCESS": "#4caf88",
            "WARNING": "#e8a44a",
            "DANGER": "#e85555",
            "THUMB_HOVER": "#2e2e3a",
            "CANVAS_BG": "#0f0f13",
            "PATCH_BG": "#1a1a22",
        },
        "light": {
            "DARK_BG": "#e4e3ec",
            "PANEL_BG": "#e4e3ec",
            "SIDEBAR_BG": "#ebebf3",
            "ACCENT": "#c0365a",
            "ACCENT_HOVER": "#e0507a",
            "ACCENT_MUTED": "#e8b4c0",
            "TEXT_PRIMARY": "#1a1820",
            "TEXT_MUTED": "#5a5870",
            "TEXT_DIM": "#8a8898",
            "BORDER": "#c8c7d4",
            "SUCCESS": "#1e8a5a",
            "WARNING": "#b06820",
            "DANGER": "#b02020",
            "THUMB_HOVER": "#d4d3e0",
            "CANVAS_BG": "#d8d7e0",
            "PATCH_BG": "#dddce8",
        },
    }

    _active: str = "dark"

    @classmethod
    def set_theme(cls, name: str) -> None:
        if name not in cls._themes:
            raise ValueError(f"Unknown theme {name!r}")
        cls._active = name

    @classmethod
    def is_dark(cls) -> bool:
        return cls._active == "dark"

    @classmethod
    def _c(cls, key: str) -> str:
        return cls._themes[cls._active][key]

    @classmethod
    def DARK_BG(cls) -> str: return cls._c("DARK_BG")

    @classmethod
    def PANEL_BG(cls) -> str: return cls._c("PANEL_BG")

    @classmethod
    def SIDEBAR_BG(cls) -> str: return cls._c("SIDEBAR_BG")

    @classmethod
    def ACCENT(cls) -> str: return cls._c("ACCENT")

    @classmethod
    def ACCENT_HOVER(cls) -> str: return cls._c("ACCENT_HOVER")

    @classmethod
    def ACCENT_MUTED(cls) -> str: return cls._c("ACCENT_MUTED")

    @classmethod
    def TEXT_PRIMARY(cls) -> str: return cls._c("TEXT_PRIMARY")

    @classmethod
    def TEXT_MUTED(cls) -> str: return cls._c("TEXT_MUTED")

    @classmethod
    def TEXT_DIM(cls) -> str: return cls._c("TEXT_DIM")

    @classmethod
    def BORDER(cls) -> str: return cls._c("BORDER")

    @classmethod
    def SUCCESS(cls) -> str: return cls._c("SUCCESS")

    @classmethod
    def WARNING(cls) -> str: return cls._c("WARNING")

    @classmethod
    def DANGER(cls) -> str: return cls._c("DANGER")

    @classmethod
    def THUMB_HOVER(cls) -> str: return cls._c("THUMB_HOVER")

    @classmethod
    def CANVAS_BG(cls) -> str: return cls._c("CANVAS_BG")

    @classmethod
    def PATCH_BG(cls) -> str: return cls._c("PATCH_BG")

    @classmethod
    def BASELINE_COLOR(cls) -> str: return cls._c("WARNING")
