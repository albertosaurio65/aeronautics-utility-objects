package com.enxv.aerouniversaljoint.network;

import com.enxv.aerouniversaljoint.AeroUniversalJointMod;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Dedicated settings packet for the giant hydraulic cylinder. */
public record SetGiantHydraulicSettingsPayload(BlockPos pos, int flowLitresPerMinute, boolean vented,
                                                int targetLengthTenths, int pressureBar,
                                                int redstoneMinLengthTenths,
                                                int redstoneMaxLengthTenths) implements CustomPacketPayload {
    public static final Type<SetGiantHydraulicSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "set_giant_hydraulic_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGiantHydraulicSettingsPayload> STREAM_CODEC = StreamCodec.of(
            SetGiantHydraulicSettingsPayload::encode, SetGiantHydraulicSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, SetGiantHydraulicSettingsPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeVarInt(payload.flowLitresPerMinute());
        buffer.writeBoolean(payload.vented());
        buffer.writeVarInt(payload.targetLengthTenths());
        buffer.writeVarInt(payload.pressureBar());
        buffer.writeVarInt(payload.redstoneMinLengthTenths());
        buffer.writeVarInt(payload.redstoneMaxLengthTenths());
    }

    private static SetGiantHydraulicSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SetGiantHydraulicSettingsPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<SetGiantHydraulicSettingsPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetGiantHydraulicSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof HydraulicConnectionHeadMenu menu)
                    || !menu.isRodSettingsMode() || !menu.getBlockPos().equals(payload.pos())) {
                return;
            }
            HydraulicConnectionHeadBlockEntity head = menu.getBlockEntity();
            if (head != null && head.isGiantHydraulicLink()) {
                head.setGiantHydraulicSettingsAndMirror(payload.flowLitresPerMinute(), payload.vented(),
                        payload.targetLengthTenths(), payload.pressureBar(), payload.redstoneMinLengthTenths(),
                        payload.redstoneMaxLengthTenths());
            }
        });
    }
}
