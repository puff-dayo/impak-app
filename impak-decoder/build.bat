uv pip install pip build twine setuptools wheel
uv run --active python -m build
twine check dist/*
uv pip install dist/impakdecoder-0.1.1-py3-none-any.whl
