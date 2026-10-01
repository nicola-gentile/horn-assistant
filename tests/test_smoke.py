from typer.testing import CliRunner

from horn_assistant import __version__
from horn_assistant.cli import app


def test_version() -> None:
    result = CliRunner().invoke(app, ["--version"])
    assert result.exit_code == 0
    assert result.stdout.strip() == __version__
