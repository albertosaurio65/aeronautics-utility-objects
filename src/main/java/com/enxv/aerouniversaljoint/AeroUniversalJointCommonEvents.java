package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.content.PendingRodSelections;
import com.enxv.aerouniversaljoint.content.PendingHydraulicSelections;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class AeroUniversalJointCommonEvents {
    private AeroUniversalJointCommonEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AeroUniversalJointCommonEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(AeroUniversalJointCommonEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(AeroUniversalJointCommonEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(AeroUniversalJointCommonEvents::onPlayerLoggedOut);
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        clearSelections(event.getOriginal());
        clearSelections(event.getEntity());
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearSelections(event.getEntity());
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearSelections(event.getEntity());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearSelections(event.getEntity());
    }

    private static void clearSelections(net.minecraft.world.entity.player.Player player) {
        PendingRodSelections.clear(player);
        PendingRodSelections.clearClient(player);
        PendingHydraulicSelections.clear(player);
        PendingHydraulicSelections.clearClient(player);
    }
}
