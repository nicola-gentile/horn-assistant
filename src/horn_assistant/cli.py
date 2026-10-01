"""Command line entry point."""

from typing import Annotated

import typer

from horn_assistant import __version__

app = typer.Typer(no_args_is_help=True, help="CHC solver agnostic preprocessor.")


def _version_callback(value: bool) -> None:
    if value:
        typer.echo(__version__)
        raise typer.Exit()


@app.callback()
def main(
    version: Annotated[
        bool,
        typer.Option("--version", callback=_version_callback, is_eager=True, help="Show the version and exit."),
    ] = False,
) -> None:
    """CHC solver agnostic preprocessor."""
