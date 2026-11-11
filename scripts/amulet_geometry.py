"""Avoid coplanar faces in the small layered amulet item model (z-fighting)."""

MIN_GAP = .018  # model coordinates: > 1/1000 block at original scale
MOVE = .025


def first_near_coplanar(parts, gap=MIN_GAP):
    """Return the first overlapping projected face pair that is too close in depth.

    Check both face orientations: item render layers/transformations need not cull
    the back face, and different materials must never compete for one depth.
    """
    for i, first in enumerate(parts):
        for j in range(i + 1, len(parts)):
            second = parts[j]
            for axis in range(3):
                plane = [n for n in range(3) if n != axis]
                if not all(min(first['to'][k], second['to'][k]) - max(first['from'][k], second['from'][k]) > 1e-6 for k in plane):
                    continue
                for a in ('from', 'to'):
                    for b in ('from', 'to'):
                        if abs(first[a][axis] - second[b][axis]) < gap - 1e-9:
                            return (i, j, axis, a, b)
    return None


def separate_faces(parts):
    """Expand only conflicting faces of the later layer, preserving the design.

    Later layers (gold highlights, etc.) take precedence over earlier ones. A
    quarter of a pixel or larger redesign is not necessary: the current model's
    biggest change is 0.05 of one model unit, i.e. 1/320 of a block.
    """
    for _ in range(1000):
        conflict = first_near_coplanar(parts)
        if conflict is None:
            return
        _, j, axis, _, side = conflict
        amount = -MOVE if side == 'from' else MOVE
        parts[j][side][axis] = round(parts[j][side][axis] + amount, 6)
    raise ValueError('Could not separate layered amulet faces')
