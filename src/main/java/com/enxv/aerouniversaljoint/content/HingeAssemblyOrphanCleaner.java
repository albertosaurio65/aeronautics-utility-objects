package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlocks;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Removes hidden hinge sublevels left behind by an interrupted block removal or move. */
public final class HingeAssemblyOrphanCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger("aeronautics_utility_objects/orphan-cleanup");
    private static final Set<HydraulicConnectionHeadBlockEntity> LIVE_HEADS =
            ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Integer> ORPHAN_SIGHTINGS = new ConcurrentHashMap<>();

    private HingeAssemblyOrphanCleaner() {
    }

    public static void register(HydraulicConnectionHeadBlockEntity head) {
        LIVE_HEADS.add(head);
    }

    public static void unregister(HydraulicConnectionHeadBlockEntity head) {
        LIVE_HEADS.remove(head);
    }

    public static void cleanup(MinecraftServer server) {
        int removedCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            Set<UUID> owned = ConcurrentHashMap.newKeySet();
            for (HydraulicConnectionHeadBlockEntity head : LIVE_HEADS) {
                if (head.getLevel() == level && !head.isRemoved()) {
                    UUID id = head.getHingeSubLevelId();
                    if (id != null) {
                        owned.add(id);
                    }
                }
            }

            ArrayList<ServerSubLevel> orphans = new ArrayList<>();
            Set<UUID> currentOrphans = ConcurrentHashMap.newKeySet();
            for (ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved() || owned.contains(subLevel.getUniqueId())) {
                    continue;
                }
                if (subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO)
                        .is(ModBlocks.HYDRAULIC_HINGE_LINK.get())) {
                    orphans.add(subLevel);
                    currentOrphans.add(subLevel.getUniqueId());
                }
            }
            ORPHAN_SIGHTINGS.keySet().removeIf(id -> !currentOrphans.contains(id));
            for (ServerSubLevel orphan : orphans) {
                int sightings = ORPHAN_SIGHTINGS.merge(orphan.getUniqueId(), 1, Integer::sum);
                if (sightings < 2 || orphan.isRemoved()) {
                    continue;
                }
                container.removeSubLevel(orphan, SubLevelRemovalReason.REMOVED);
                ORPHAN_SIGHTINGS.remove(orphan.getUniqueId());
                removedCount++;
            }
        }
        if (removedCount > 0) {
            LOGGER.info("Removed {} orphan hydraulic hinge sublevel(s)", removedCount);
        }
    }

    public static void clear() {
        LIVE_HEADS.clear();
        ORPHAN_SIGHTINGS.clear();
    }
}
