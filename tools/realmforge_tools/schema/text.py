"""Texts: ``content/texts/<group>.yaml``, player-facing texts that belong to no other content type (key names,
messages). Each becomes the translation key ``<group>.realmforge.<name>`` and a Java constant (architecture 6.3)."""

from __future__ import annotations

from typing import Annotated

from pydantic import Field, StringConstraints

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import LocalizedText, StrictModel

TextName = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*$", max_length=64)]
# Dots allowed, for a key format the game prescribes: Options > Controls reads a key category's name from
# ``key.category.<namespace>.<path>``, so the group ``key.category`` names ours.
TextGroupId = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$", max_length=64),
    Field(description="Lowercase snake_case words joined by dots; the key prefix. Must equal the file name."),
]


class TextGroupDef(StrictModel):
    """A group of texts sharing the translation key prefix ``<id>.realmforge.``, for example ``key`` for key
    mapping names (``key.realmforge.night_vision``) or ``message`` for chat and action bar messages."""

    id: TextGroupId
    texts: dict[TextName, LocalizedText] = Field(
        min_length=1,
        description="Text name (lowercase snake_case) -> text; the key is <id>.realmforge.<name>.",
    )


def text_key(group: str, name: str) -> str:
    """The translation key of a text: ``<group>.realmforge.<name>``."""
    return f"{group}.{MOD_ID}.{name}"


def text_constant(group: str, name: str) -> str:
    """The name of a text's constant in ``GeneratedTexts``: ``<GROUP>_<NAME>``, dots in the group as underscores."""
    return f"{group}_{name}".replace(".", "_").upper()
