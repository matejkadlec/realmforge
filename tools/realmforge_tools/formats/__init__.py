"""Byte-exact, dependency-free writers for the file formats the generators emit.

Every writer is deterministic across machines: the same input always produces the same bytes, so
``rf gen`` followed by ``git diff --exit-code`` is reliable locally and in CI.
"""
