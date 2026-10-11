"""Building blocks shared by all content models."""

from __future__ import annotations

from typing import Annotated

from pydantic import AfterValidator, BaseModel, ConfigDict, Field, StringConstraints


class StrictModel(BaseModel):
    """Base for content models: unknown keys and implicit type coercion are errors."""

    model_config = ConfigDict(extra="forbid", strict=True, frozen=True)


# Our own content ids: the path part of `realmforge:<id>`. Also used for Java constant and file names.
ContentId = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z][a-z0-9_]*$", max_length=64),
    Field(description="Lowercase snake_case id; becomes realmforge:<id>. Must equal the file name."),
]

# A fully qualified reference to any registry entry, e.g. `minecraft:ingredients`. The namespace is mandatory.
ResourceId = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z0-9_.-]+:[a-z0-9_./-]+$"),
    Field(description="Namespaced id, e.g. minecraft:stone. Validated against the ID universe."),
]


def _require_unique(values: list[str]) -> list[str]:
    duplicates = sorted({value for value in values if values.count(value) > 1})
    if duplicates:
        raise ValueError(f"duplicate entries: {', '.join(duplicates)}")
    return values


# A list of ResourceIds without repeats. Duplicates are usually a typo, and some game APIs reject them outright
# (adding an item to the same creative tab twice throws in BuildCreativeModeTabContentsEvent).
UniqueResourceIds = Annotated[
    list[ResourceId],
    AfterValidator(_require_unique),
    Field(json_schema_extra={"uniqueItems": True}),
]

NonEmptyText = Annotated[str, StringConstraints(min_length=1, strip_whitespace=False)]


class LocalizedText(StrictModel):
    """A player-facing text in every supported locale. en_us is required; missing locales fall back to it."""

    en_us: NonEmptyText = Field(description="English (US). Required.")
    cs_cz: NonEmptyText | None = Field(default=None, description="Czech. Optional.")

    def translations(self) -> dict[str, str]:
        return {locale: text for locale, text in self.model_dump().items() if text is not None}
