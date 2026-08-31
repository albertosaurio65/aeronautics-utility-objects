# Compatibility Contract

The following values are externally consumed and must remain stable across internal refactors.

## Registry identifiers

- `aeronautics_utility_objects:universal_joint`
- `aeronautics_utility_objects:hydraulic_connection_head`

`create_aeronautics_toolgun` identifies these block entities by registry id when saving and placing blueprints.

## Persistent link format

Linked universal joints and hydraulic heads persist their partner through these NBT keys:

- `LinkedPos`: partner position, encoded as a normal NBT block position.
- `LinkedSubLevel`: optional partner sub-level UUID.

The keys and their coordinate meaning are part of the blueprint compatibility contract. Internal state may be reorganized, but a loaded legacy tag must retain the same effective link.

## Runtime Hinge State

`HingeSubLevel`, `HingeLinkPos`, `HingeParentSubLevel`, and `HingeOwnerPos` are internal runtime recovery data for hinged hydraulic heads. They are not blueprint references. A copied endpoint verifies that its recorded hinge parent and owner position match its current assembly before using those fields; on a mismatch it discards only its local reference and creates a new hinge assembly. It never removes the referenced sub-level, because that resource may belong to an already loaded structure. Legacy runtime data without `HingeOwnerPos` is rebuilt once after loading.

## Network and player-facing identifiers

Keep the existing menu ids, payload ids, translation keys, item ids, and block ids. Changes to implementation packages are allowed when public entry points and serialized data remain compatible.
