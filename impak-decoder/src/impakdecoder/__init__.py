from .decoder import ImpakReader
from .differ import reconstruct


def open(path, **kwargs) -> ImpakReader:
    return ImpakReader(path, **kwargs)


__all__ = [
    "open",
    "ImpakReader",
    "reconstruct",
]

__version__ = "0.1.1"
