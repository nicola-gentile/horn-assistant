# horn-assistant

Horn Assistant is a CHC solver agnostic preprocessor that implements several transformations of
Constrained Horn Clause systems that may help their resolution.

## Installation

### As a Python library

Requires Python >= 3.12 (CPython or PyPy).

```sh
# with uv
uv add git+https://github.com/nicola-gentile/horn-assistant
# with pip
pip install git+https://github.com/nicola-gentile/horn-assistant
```

Pin a release with `@vX.Y.Z` at the end of the URL.

### As a standalone tool

Every [GitHub release](https://github.com/nicola-gentile/horn-assistant/releases) ships self-contained
archives that embed a [PyPy](https://pypy.org) interpreter, so no Python installation is needed:

| Platform        | Archive                                      |
|-----------------|----------------------------------------------|
| Linux x86_64    | `horn-assistant-<version>-linux-x64.tar.gz`     |
| Linux aarch64   | `horn-assistant-<version>-linux-aarch64.tar.gz` |
| macOS Intel     | `horn-assistant-<version>-darwin-x64.tar.gz`    |
| macOS Apple     | `horn-assistant-<version>-darwin-arm64.tar.gz`  |
| Windows x86_64  | `horn-assistant-<version>-win64-x64.zip`        |

Extract the archive and run `horn-assistant` (`horn-assistant.cmd` on Windows) from the extracted directory.

## Development

The project is managed with [uv](https://docs.astral.sh/uv/).

```sh
uv sync            # create the environment
uv run pytest      # run the tests
uv run mypy        # type check
uv run horn-assistant --help
```

Build a standalone bundle for the current platform:

```sh
uv build
python scripts/build_standalone.py --wheel dist/horn_assistant-*.whl
```

## Releasing

Bump `version` in `pyproject.toml` (`uv version --bump minor`), commit, then push a matching tag:

```sh
git tag v0.2.0 && git push origin v0.2.0
```

The `Release` workflow builds the wheel, sdist and the standalone bundles for Linux, macOS and Windows,
and attaches them to a GitHub release.
