package com.enxv.aerouniversaljoint.network;

import com.enxv.aerouniversaljoint.AeroUniversalJointMod;
import com.enxv.aerouniversaljoint.content.JointBindingData;
import com.enxv.aerouniversaljoint.content.PendingHydraulicSelections;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record SyncHydraulicSelectionPayload(@Nullable JointBindingData.Selection selection) implements CustomPacketPayload {
    public static final Type<SyncHydraulicSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "sync_hydraulic_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHydraulicSelectionPayload> STREAM_CODEC = StreamCodec.of(
            SyncHydraulicSelectionPayload::encode,
            SyncHydraulicSelectionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, SyncHydraulicSelectionPayload payload) {
        JointBindingData.Selection selection = payload.selection();
        buffer.writeBoolean(selection != null);
        if (selection == null) {
            return;
        }

        buffer.writeResourceLocation(selection.dimensionId());
        buffer.writeBlockPos(selection.pos());
        buffer.writeBoolean(selection.subLevelId() != null);
        if (selection.subLevelId() != null) {
            buffer.writeUUID(selection.subLevelId());
        }
        buffer.writeBoolean(selection.creativeHydraulic());
        buffer.writeBoolean(selection.giantHydraulic());
    }

    private static SyncHydraulicSelectionPayload decode(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return new SyncHydraulicSelectionPayload(null);
        }

        ResourceLocation dimensionId = buffer.readResourceLocation();
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean creativeHydraulic = buffer.readBoolean();
        boolean giantHydraulic = buffer.readBoolean();
        return new SyncHydraulicSelectionPayload(
                new JointBindingData.Selection(dimensionId, pos, subLevelId, creativeHydraulic, giantHydraulic));
    }

    @Override
    public Type<SyncHydraulicSelectionPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncHydraulicSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            if (payload.selection() == null) {
                PendingHydraulicSelections.clearClient(player);
            } else {
                PendingHydraulicSelections.writeClient(player, payload.selection());
            }
        });
    }

    public static void send(ServerPlayer player, @Nullable JointBindingData.Selection selection) {
        PacketDistributor.sendToPlayer(player, new SyncHydraulicSelectionPayload(selection));
    }
}
