"""Dungeon rooms (architecture 15.4, ADR 0028): structure templates, the importer and the review gallery.

``rf rooms import`` turns ``.nbt``, ``.schem`` and ``.litematic`` files into rooms in ``content/rooms/``;
``rf rooms gallery`` renders rooms headless for review; ``rf rooms review`` records the keep and reject decisions.
``rf gen`` and ``rf validate`` read rooms through ``content.py`` and ``rooms/check.py``.
"""
