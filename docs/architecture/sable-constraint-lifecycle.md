# Sable Constraint Lifecycle

Hydraulic links use one owner for each rod constraint. The owner is chosen deterministically from the containing sub-level UUIDs, so both endpoints never create the same constraint.

1. A linked head reports the partner and any hinge sub-levels from `sable$getConnectionDependencies`. Sable uses these dependencies for connection and loading groups only.
2. The elected owner receives `sable$physicsTick`. It validates both endpoints and their hinge assemblies before creating or refreshing a constraint.
3. The owner creates a typed Sable constraint, then updates its frames and motor inside later physics ticks.
4. Any invalid endpoint, link removal, block removal, hinge replacement, or failed validation removes the owner-held constraint. A hinge constraint is removed before its hinge sub-level is removed.

The constraint handle is runtime-only. Persistent data contains link references and hinge sub-level identity, never a handle. This keeps save/load recovery independent from a previous physics-pipeline instance.

## Assembly Movement

Sable invokes `BlockSubLevelAssemblyListener.beforeMove` before moving a block and `afterMove` while the old block still exists. Linked endpoints use this as one deterministic transaction:

1. `beforeMove` records the old reference and marks the old block entity as moving.
2. The old entity's `remove` consumes that marker, clears only its local runtime state, and never drops or clears the partner link.
3. `afterMove` resolves the moved endpoint, remaps its own reference, then updates the loaded partner to the new position and sub-level identity.

The temporary player-selection remap cache expires after 600 server game ticks. It intentionally uses game time, not wall-clock time, so a paused or overloaded server cannot turn a successful assembly move into an unintended unlink.
