"""Build a self-contained horn-assistant distribution embedding a portable PyPy.

The resulting archive contains a PyPy interpreter with horn-assistant and its
dependencies installed, plus a launcher script, so it runs without a system Python.

Usage:
    python scripts/build_standalone.py --wheel dist/horn_assistant-*.whl --out-dir standalone
"""

from __future__ import annotations

import argparse
import json
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

PYPY_VERSIONS_URL = "https://downloads.python.org/pypy/versions.json"
DEFAULT_PYPY_VERSION = "8.0.0"
DEFAULT_PYTHON_VERSION = "3.12"

POSIX_LAUNCHER = """#!/bin/sh
HERE="$(cd "$(dirname "$0")" && pwd)"
exec "$HERE/pypy/bin/pypy3" -m horn_assistant "$@"
"""

WINDOWS_LAUNCHER = """@echo off
"%~dp0pypy\\pypy3.exe" -m horn_assistant %*
"""


def host_target() -> tuple[str, str]:
    """Return the (platform, arch) pair as named in PyPy's versions.json."""
    machine = platform.machine().lower()
    arch = {"x86_64": "x64", "amd64": "x64", "arm64": "arm64", "aarch64": "aarch64"}.get(machine)
    if arch is None:
        raise SystemExit(f"unsupported architecture: {machine}")
    if sys.platform.startswith("linux"):
        return "linux", arch
    if sys.platform == "darwin":
        return "darwin", "arm64" if arch in ("arm64", "aarch64") else arch
    if sys.platform == "win32":
        return "win64", arch
    raise SystemExit(f"unsupported platform: {sys.platform}")


def pypy_download_url(pypy_version: str, python_version: str, plat: str, arch: str) -> str:
    with urllib.request.urlopen(PYPY_VERSIONS_URL) as response:
        releases: list[dict[str, Any]] = json.load(response)
    for release in releases:
        if release["pypy_version"] != pypy_version or not release["python_version"].startswith(python_version + "."):
            continue
        for file in release["files"]:
            if file["platform"] == plat and file["arch"] == arch:
                return str(file["download_url"])
    raise SystemExit(f"no PyPy {pypy_version} (Python {python_version}) build for {plat}/{arch}")


def download_and_extract(url: str, dest: Path) -> None:
    """Download a PyPy archive and extract its single top-level directory to `dest`."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        archive = tmp_path / url.rsplit("/", 1)[-1]
        print(f"Downloading {url}")
        urllib.request.urlretrieve(url, archive)
        extract_dir = tmp_path / "extract"
        if archive.suffix == ".zip":
            with zipfile.ZipFile(archive) as zf:
                zf.extractall(extract_dir)
        else:
            with tarfile.open(archive) as tf:
                tf.extractall(extract_dir, filter="tar")
        (root,) = list(extract_dir.iterdir())
        shutil.move(root, dest)


def pypy_executable(pypy_dir: Path) -> Path:
    if sys.platform == "win32":
        return pypy_dir / "pypy3.exe"
    return pypy_dir / "bin" / "pypy3"


def write_launcher(bundle_dir: Path) -> None:
    if sys.platform == "win32":
        (bundle_dir / "horn-assistant.cmd").write_text(WINDOWS_LAUNCHER, newline="\r\n")
    else:
        launcher = bundle_dir / "horn-assistant"
        launcher.write_text(POSIX_LAUNCHER)
        launcher.chmod(0o755)


def make_archive(bundle_dir: Path, out_dir: Path) -> Path:
    if sys.platform == "win32":
        return Path(shutil.make_archive(str(out_dir / bundle_dir.name), "zip", bundle_dir.parent, bundle_dir.name))
    return Path(shutil.make_archive(str(out_dir / bundle_dir.name), "gztar", bundle_dir.parent, bundle_dir.name))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--wheel", type=Path, required=True, help="horn-assistant wheel to install")
    parser.add_argument("--out-dir", type=Path, default=Path("standalone"))
    parser.add_argument("--pypy-version", default=DEFAULT_PYPY_VERSION)
    parser.add_argument("--python-version", default=DEFAULT_PYTHON_VERSION)
    parser.add_argument("--name", help="bundle name (default: horn-assistant-<version>-<platform>-<arch>)")
    args = parser.parse_args()

    wheel: Path = args.wheel.resolve()
    out_dir: Path = args.out_dir.resolve()
    plat, arch = host_target()
    version = wheel.name.split("-")[1]
    name = args.name or f"horn-assistant-{version}-{plat}-{arch}"

    work_dir = out_dir / "build"
    bundle_dir = work_dir / name
    shutil.rmtree(bundle_dir, ignore_errors=True)
    bundle_dir.mkdir(parents=True)

    pypy_dir = bundle_dir / "pypy"
    download_and_extract(pypy_download_url(args.pypy_version, args.python_version, plat, arch), pypy_dir)
    pypy = pypy_executable(pypy_dir)

    subprocess.run([pypy, "-m", "ensurepip", "--default-pip"], check=True)
    subprocess.run([pypy, "-m", "pip", "install", "--no-cache-dir", "--no-warn-script-location", wheel], check=True)
    write_launcher(bundle_dir)
    subprocess.run([pypy, "-m", "horn_assistant", "--version"], check=True)

    archive = make_archive(bundle_dir, out_dir)
    print(f"Created {archive}")


if __name__ == "__main__":
    main()
