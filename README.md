# Create Aeronautics: Transmission & Linkage

Adds universal joints, hydraulic links, and kinetic conversion bearings for connecting moving Aeronautics structures.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.226 or newer in the 21.1 line
- Create 6.0.10 or newer
- Sable 2.0.0 or newer
- Simulated 1.3.0 or newer

The mod targets Sable 2.x only. It does not include a Sable 1.x compatibility layer.

## Build and verification

Use JDK 21.

```powershell
.\gradlew.bat build --offline --no-daemon --console=plain
```

`check` also runs source-level compatibility and physics-control regression checks. The adjacent
`aeronautics_structure_tool` project is intentionally not coupled to implementation classes; verify its
compatibility with:

```powershell
.\gradlew.bat compileJava --offline --no-daemon --console=plain
```

Run that command from the `aeronautics_structure_tool` project directory.

## Compatibility

Blueprint and tool integrations depend on registry ids and persistent link references, not internal Java
classes. The maintained contract is documented in
[docs/architecture/compatibility-contract.md](docs/architecture/compatibility-contract.md).

## Architecture

The Sable constraint and assembly-move lifecycle is documented in
[docs/architecture/sable-constraint-lifecycle.md](docs/architecture/sable-constraint-lifecycle.md).
Ownership boundaries for the main runtime components are documented in
[docs/architecture/component-ownership.md](docs/architecture/component-ownership.md).

## License

CC BY-NC 4.0. See [LICENSE](LICENSE).
