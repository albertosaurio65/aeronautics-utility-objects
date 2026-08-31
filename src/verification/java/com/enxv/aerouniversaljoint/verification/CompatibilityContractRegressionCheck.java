package com.enxv.aerouniversaljoint.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompatibilityContractRegressionCheck {
    private CompatibilityContractRegressionCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        String blockEntities = read(root, "src/main/java/com/enxv/aerouniversaljoint/ModBlockEntities.java");
        String universalJoint = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/UniversalJointBlockEntity.java");
        String hydraulicHead = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity.java");
        String moveRemapper = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/RecentMoveRemapper.java");
        String moveHandler = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/SubLevelMoveHandler.java");
        String universalJointBlock = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/UniversalJointBlock.java");
        String hydraulicHeadBlock = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlock.java");
        String commonEvents = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/AeroUniversalJointCommonEvents.java");
        String dampingBearing = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/DampingStressBearingBlockEntity.java");
        String dampingOutput = read(root, "src/main/java/com/enxv/aerouniversaljoint/content/DampingOutputKinetics.java");
        String hydraulicSettingsState = read(root,
                "src/main/java/com/enxv/aerouniversaljoint/content/hydraulic/HydraulicSettingsState.java");
        String modMetadata = read(root, "src/main/templates/META-INF/neoforge.mods.toml");
        String gradleProperties = read(root, "gradle.properties");

        requireContains(blockEntities, "\"universal_joint\"", "universal joint block-entity id");
        requireContains(blockEntities, "\"hydraulic_connection_head\"", "hydraulic head block-entity id");
        requireLinkReferenceTags(universalJoint, "universal joint");
        requireLinkReferenceTags(hydraulicHead, "hydraulic head");
        requireContains(modMetadata, "modId=\"sable\"", "Sable dependency");
        requireContains(modMetadata, "versionRange=\"${sable_version_range}\"", "Sable version-range template");
        requireContains(gradleProperties, "sable_version_range=[2.0.0,)", "Sable 2.x minimum dependency");
        requireMoveLifecycle(moveRemapper, moveHandler, universalJoint, hydraulicHead, universalJointBlock, hydraulicHeadBlock,
                commonEvents);
        requireHingeBlueprintIsolation(hydraulicHead);
        requireNotContains(universalJoint, "TAG_SPEED_RATIO", "disabled speed-ratio persistence");
        requireContains(dampingBearing, "\"DampingStressBearingOutput\"", "ExtraKinetics save-name compatibility");
        requireContains(dampingOutput, "\"GeneratedSpeed\"", "damping generated-speed client tag");
        requireContains(dampingOutput, "\"PublishedStressCapacity\"", "damping stress-capacity client tag");
        requireNotContains(dampingBearing, "class DampingOutputKinetics", "nested damping output implementation");
        requireContains(hydraulicSettingsState, "class HydraulicSettingsState", "hydraulic persistent settings owner");
        requireBalancedHydraulicImpulseApplication(hydraulicHead);
    }

    private static String read(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
    }

    private static void requireLinkReferenceTags(String source, String subject) {
        requireContains(source, "TAG_LINKED_POS = \"LinkedPos\"", subject + " LinkedPos tag");
        requireContains(source, "TAG_LINKED_SUB_LEVEL = \"LinkedSubLevel\"", subject + " LinkedSubLevel tag");
    }

    private static void requireMoveLifecycle(String remapper, String handler, String universalJoint,
                                             String hydraulicHead, String universalJointBlock, String hydraulicHeadBlock,
                                             String commonEvents) {
        requireContains(remapper, "ENTRY_TTL_TICKS", "game-tick move-remap expiry");
        requireContains(handler, "RecentMoveRemapper.prepare", "shared move preparation");
        requireContains(handler, "RecentMoveRemapper.record", "shared move completion");
        requireContains(universalJoint, "boolean preservingLinkForSubLevelMove", "universal-joint move transaction");
        requireContains(hydraulicHead, "boolean preservingLinkForSubLevelMove", "hydraulic move transaction");
        requireContains(universalJointBlock, "SubLevelMoveHandler.beforeMove", "universal-joint shared move handler");
        requireContains(hydraulicHeadBlock, "SubLevelMoveHandler.beforeMove", "hydraulic shared move handler");
        requireContains(commonEvents, "RecentMoveRemapper.pruneExpired", "per-tick move-remap cleanup");
        requireContains(commonEvents, "RecentMoveRemapper.clear", "server-stop move-remap cleanup");
        requireNotContains(remapper, "System.currentTimeMillis", "wall-clock move-remap expiry");
        requireNotContains(universalJoint, "System.currentTimeMillis", "universal-joint wall-clock move grace period");
        requireNotContains(hydraulicHead, "System.currentTimeMillis", "hydraulic wall-clock move grace period");
    }

    private static void requireHingeBlueprintIsolation(String hydraulicHead) {
        int ownershipCheck = hydraulicHead.indexOf("if (!this.isHingeAssemblyOwnedByCurrentParent())");
        int hingeLookup = hydraulicHead.indexOf("ServerSubLevel hingeSubLevel = this.getHingeServerSubLevel();");
        requireCondition(ownershipCheck >= 0 && hingeLookup > ownershipCheck,
                "hinge ownership must be checked before a persisted hinge sublevel is used");
        requireContains(hydraulicHead, "this.discardForeignHingeAssemblyReference();",
                "foreign hinge references must be discarded locally");
        requireContains(hydraulicHead, "TAG_HINGE_OWNER_POS = \"HingeOwnerPos\"",
                "runtime hinge owner position tag");
        requireContains(hydraulicHead, "this.worldPosition.equals(this.hingeOwnerPos)",
                "runtime hinge owner position check");
        requireContains(hydraulicHead, "return this.isHingeAssemblyOwnedByCurrentParent() ? this.hingeSubLevelId : null;",
                "public hinge lookup must not expose a foreign runtime hinge");

        int discardStart = hydraulicHead.indexOf("private void discardForeignHingeAssemblyReference()");
        int discardEnd = hydraulicHead.indexOf("private void removeHingeConstraintHandle()", discardStart);
        requireCondition(discardStart >= 0 && discardEnd > discardStart,
                "foreign hinge-reference cleanup boundary");
        requireNotContains(hydraulicHead.substring(discardStart, discardEnd), "removeSubLevel",
                "foreign hinge cleanup must not remove another structure's sublevel");

        int dependencyStart = hydraulicHead.indexOf("public @Nullable Iterable<SubLevel> sable$getConnectionDependencies()");
        int dependencyEnd = hydraulicHead.indexOf("public void sable$physicsTick", dependencyStart);
        requireCondition(dependencyStart >= 0 && dependencyEnd > dependencyStart,
                "hinge dependency boundary");
        String dependencyMethod = hydraulicHead.substring(dependencyStart, dependencyEnd);
        requireContains(dependencyMethod, "this.getOwnedHingeSubLevel()",
                "Sable dependencies must expose only owned hinge sublevels");
        requireNotContains(dependencyMethod, "this.getHingeSubLevel()",
                "Sable dependencies must not expose a copied hinge sublevel");
    }

    private static void requireBalancedHydraulicImpulseApplication(String hydraulicHead) {
        int methodStart = hydraulicHead.indexOf("private void applyLengthImpulse(");
        int methodEnd = hydraulicHead.indexOf("private static void syncSelectionToClient", methodStart);
        requireCondition(methodStart >= 0 && methodEnd > methodStart,
                "hydraulic impulse application boundary");
        String method = hydraulicHead.substring(methodStart, methodEnd);
        int validation = method.indexOf("!otherHandle.isValid()");
        int ownApplication = method.indexOf("ownHandle.applyForcesAndReset");
        requireCondition(validation >= 0 && ownApplication > validation,
                "both hydraulic impulse recipients must be validated before applying either impulse");
    }

    private static void requireContains(String source, String expected, String description) {
        if (!source.contains(expected)) {
            throw new AssertionError("Missing compatibility contract: " + description);
        }
    }

    private static void requireNotContains(String source, String forbidden, String description) {
        if (source.contains(forbidden)) {
            throw new AssertionError("Unexpected implementation detail: " + description);
        }
    }

    private static void requireCondition(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Missing compatibility contract: " + description);
        }
    }
}
