# android-nack1

Base: pcm720/udpfsd at `58d7c8f11ac196d4a5ca7b65b78743fbeedfdd80`.

The Android log recorded repeated `NACK retransmit from 347 sent 0 packets`
after window ACK timeouts. Two transport defects can stall recovery:

1. `retransmitFrom` breaks on a retained packet preceding the requested
   sequence, so a NACK into the middle of the ring sends nothing.
2. `onPeerNack` does not acknowledge the packets preceding the expected
   sequence. If a full window was received but its ACK was lost, a NACK for
   the next unsent sequence cannot advance the window.

The patch locates the exact retained sequence before retransmitting and treats
only NACKs within the current window (including its one-past-last sequence) as
cumulative progress. Old and future NACKs do not prune data or reset retries.

NACK semantics are defined by Neutrino's `iop/udpfs/UDPRDMA.md` and its reference
server `pc/udpfs_server.py` (reviewed at commit
`7be8de2798c99af433c91993e021746a54d6800d`): a NACK carries the next expected
sequence, which acknowledges the preceding sequence.

CI copies `nack_regression_test.go` into the pinned source and first requires
the unpatched retransmit/progress tests to fail for the expected reasons. It
then applies the patch and requires the whole transport test suite to pass
with `go test -race`. The APK includes the tested implementation, labelled
`58d7c8f11ac19-android-nack1`; build-info records the patch SHA-256.

The tests establish transport recovery in these scenarios. They cannot prove
that every real-device freeze has the same cause; physical Android/PS2 testing
and a fresh log are still needed if the symptom persists.
