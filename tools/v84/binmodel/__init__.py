"""Derive what the MapleStory client decodes for a given opcode, from the client binary alone.

The point of deriving it from the binary rather than from our own PacketCreator: a model built from
our code can only ever agree with our code. A model built from the client can DISAGREE, and a
disagreement is a candidate v84 delta.

    images.py    the two client images and the VA<->offset rule
    reach.py     which functions can consume packet bytes at all (pruning)
    dispatch.py  opcode -> handler, by abstract interpretation of the dispatcher chain
    cfgtrace.py  handler -> the byte sequences the client reads, per control-flow path
    model.py     the two joined together

See tools/v84/derive-binary-models.py for the driver, and
docs/work-plan/tickets/41-binary-derived-packet-models.md for the self-check and its limits.
"""
