"""Trusted, bounded cleanup of the renderer's fixed writable tmpfs roots.

Call only while the serialized job slot is held and every parser process has
been killed and reaped. Any failure must poison the server before another job.
The parser never supplies a path to this module.
"""
import os
import stat
import time


_ROOTS = ("/tmp", "/dev/shm")
_DEADLINE_SECONDS = 2
_MAX_DEPTH = 128
_MAX_ENTRIES = 100000


class EphemeralCleanupFailure(RuntimeError):
    def __init__(self, reason):
        super().__init__("Ephemeral cleanup failed")
        self.reason = reason


def _check(deadline, clock):
    if clock() >= deadline:
        raise EphemeralCleanupFailure("deadline")


def _open_directory(name, parent_descriptor, expected_device, deadline, clock,
                    allow_root_owner=False):
    _check(deadline, clock)
    pinned = os.open(name, os.O_PATH | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
                     dir_fd=parent_descriptor)
    try:
        metadata = os.fstat(pinned)
        if (not stat.S_ISDIR(metadata.st_mode)
                or (expected_device is not None and metadata.st_dev != expected_device)):
            raise EphemeralCleanupFailure("filesystem-boundary")
        if metadata.st_uid != os.geteuid() and not (allow_root_owner and metadata.st_uid == 0):
            raise EphemeralCleanupFailure("foreign-owner")
        if metadata.st_uid == os.geteuid():
            # O_PATH permits pinning a mode-000 directory but cannot be fchmod'ed.
            # This self-generated proc descriptor names the validated pinned inode;
            # it never follows an entry or symlink supplied by the parser.
            _check(deadline, clock)
            os.chmod("/proc/self/fd/" + str(pinned), stat.S_IRWXU)
        _check(deadline, clock)
        descriptor = os.open(".", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
                             dir_fd=pinned)
        try:
            opened = os.fstat(descriptor)
            if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
                raise EphemeralCleanupFailure("filesystem-boundary")
        except BaseException:
            os.close(descriptor)
            raise
        return descriptor, metadata.st_dev
    finally:
        os.close(pinned)


def _purge_directory(descriptor, device, depth, budget, deadline, clock, max_depth, max_entries):
    _check(deadline, clock)
    if depth > max_depth:
        raise EphemeralCleanupFailure("depth")
    with os.scandir(descriptor) as entries:
        for entry in entries:
            _check(deadline, clock)
            budget[0] += 1
            if budget[0] > max_entries:
                raise EphemeralCleanupFailure("entries")
            metadata = os.stat(entry.name, dir_fd=descriptor, follow_symlinks=False)
            if metadata.st_uid != os.geteuid():
                raise EphemeralCleanupFailure("foreign-owner")
            if metadata.st_dev != device:
                raise EphemeralCleanupFailure("filesystem-boundary")
            if stat.S_ISDIR(metadata.st_mode):
                if depth >= max_depth:
                    raise EphemeralCleanupFailure("depth")
                child, _ = _open_directory(entry.name, descriptor, device, deadline, clock)
                try:
                    _purge_directory(child, device, depth + 1, budget, deadline, clock,
                                     max_depth, max_entries)
                finally:
                    os.close(child)
                _check(deadline, clock)
                os.rmdir(entry.name, dir_fd=descriptor)
            else:
                # unlink removes only this directory entry, including symlinks,
                # hardlinks, FIFOs and sockets. No untrusted file is ever opened.
                _check(deadline, clock)
                os.unlink(entry.name, dir_fd=descriptor)
    _check(deadline, clock)


def _purge_roots(roots, *, clock=time.monotonic, timeout=_DEADLINE_SECONDS,
                 max_depth=_MAX_DEPTH, max_entries=_MAX_ENTRIES):
    """Private fixture seam; production callers always use the fixed public API."""
    deadline = clock() + timeout
    budget = [0]
    try:
        for root in roots:
            descriptor, device = _open_directory(root, None, None, deadline, clock,
                                                  allow_root_owner=True)
            try:
                _purge_directory(descriptor, device, 0, budget, deadline, clock,
                                 max_depth, max_entries)
            finally:
                os.close(descriptor)
    except EphemeralCleanupFailure:
        raise
    except (OSError, ValueError, NotImplementedError):
        raise EphemeralCleanupFailure("filesystem-operation") from None


def purge_ephemeral_storage():
    _purge_roots(_ROOTS)
