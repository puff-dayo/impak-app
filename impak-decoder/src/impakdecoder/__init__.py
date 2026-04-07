from .decoder import ImpakReader
from .differ import reconstruct


def open(path, low_ram_mode=False, cache_size=None, **kwargs) -> ImpakReader:
    return ImpakReader(
        path,
        low_ram_mode=low_ram_mode,
        cache_size=cache_size,
        **kwargs,
    )


__all__ = [
    "open",
    "ImpakReader",
    "reconstruct",
]

__version__ = "0.1.3"
