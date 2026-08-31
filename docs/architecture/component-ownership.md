# Component Ownership

The large block entities remain lifecycle coordinators. Their collaborators own rules that do not need a
world, a Sable constraint handle, or a block-entity synchronization call.

| Component | Owns | Does not own |
| --- | --- | --- |
| `HydraulicConnectionHeadBlockEntity` | Link references, NBT key orchestration, Sable constraints, hinge assemblies, endpoint mirroring, and physics interpolation | Persistent setting ranges and sorting rules |
| `HydraulicSettingsState` | Persisted stretch resistance, free mode, requested length, redstone range, return force, and normalization | NBT keys, client sync, creative-link overrides, or physics state |
| `HydraulicSettings` | Static limits, level mappings, formatting, and scalar clamping | Mutable endpoint settings |
| `HydraulicLengthControl` | Pure approach, return-force, and impulse calculations | Entity state or Sable APIs |
| `DampingStressBearingBlockEntity` | Sable angular-velocity sampling, bearing assembly recovery, resistance application, and suppression detection | Create generator publication cadence |
| `DampingOutputKinetics` | Create/Simulated generator state, source validation, output publication, safe reversal, client output tags | Sable physics sampling and bearing assembly state |
| `SubLevelMoveHandler` | Deterministic before/after assembly move handoff | Link constraint ownership |
| `RecentMoveRemapper` | Short-lived player-selection remaps using server game time | Persistent link data |

## Persistence Boundary

`HydraulicSettingsState` is deliberately passed primitive values by the block entity during NBT read and write.
The block entity retains NBT key names so the external serialization contract remains visible in the class that
owns link persistence. Runtime values such as effective target length and effective return force are not user
settings; they remain with the physics coordinator and are serialized independently for smooth save/load recovery.

Hinged hydraulic heads additionally retain a generated hinge sub-level for normal world recovery. Its parent
sub-level UUID and owner block position are ownership guards, not portable references. The position guard matters
when both structures are in the main world, where both parent IDs are absent. A copied endpoint must discard a
mismatched hinge reference locally and build a fresh hinge; deleting the referenced sub-level would damage the
original structure. Older saves without the owner-position guard rebuild their runtime hinge once on load.

## Kinetic Output Boundary

`DampingOutputKinetics` keeps the existing `GeneratedSpeed` and `PublishedStressCapacity` client tags. The parent
continues to expose the existing ExtraKinetics save name, `DampingStressBearingOutput`, so Create/Simulated network
and save behavior remain stable while the implementation class is no longer nested.
