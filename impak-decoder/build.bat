uv pip install pip build twine setuptools wheel
uv build
twine check dist/*
uv pip install dist/impakdecoder-0.1.3-py3-none-any.whl
