package com.enxv.aerouniversaljoint.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AeroUniversalJointNetwork {
    private AeroUniversalJointNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AeroUniversalJointNetwork::registerPayloads);
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncHydraulicSelectionPayload.TYPE, SyncHydraulicSelectionPayload.STREAM_CODEC, SyncHydraulicSelectionPayload::handleClient);
        registrar.playToServer(SetDampingResistancePayload.TYPE, SetDampingResistancePayload.STREAM_CODEC, SetDampingResistancePayload::handleServer);
        registrar.playToServer(SetHydraulicSettingsPayload.TYPE, SetHydraulicSettingsPayload.STREAM_CODEC, SetHydraulicSettingsPayload::handleServer);
        registrar.playToServer(SetJointSpeedRatioPayload.TYPE, SetJointSpeedRatioPayload.STREAM_CODEC, SetJointSpeedRatioPayload::handleServer);
    }
}
