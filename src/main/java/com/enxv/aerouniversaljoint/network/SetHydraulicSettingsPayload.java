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

public record SetHydraulicSettingsPayload(BlockPos pos, int stretchResistance, boolean freeMode,
                                           int expectedLengthTenths, int returnForce,
                                           int redstoneMinLengthTenths,
                                           int redstoneMaxLengthTenths) implements CustomPacketPayload {
    public static final Type<SetHydraulicSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "set_hydraulic_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetHydraulicSettingsPayload> STREAM_CODEC = StreamCodec.of(
            SetHydraulicSettingsPayload::encode,
            SetHydraulicSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, SetHydraulicSettingsPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeVarInt(payload.stretchResistance());
        buffer.writeBoolean(payload.freeMode());
        buffer.writeVarInt(payload.expectedLengthTenths());
        buffer.writeVarInt(payload.returnForce());
        buffer.writeVarInt(payload.redstoneMinLengthTenths());
        buffer.writeVarInt(payload.redstoneMaxLengthTenths());
    }

    private static SetHydraulicSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SetHydraulicSettingsPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    @Override
    public Type<SetHydraulicSettingsPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetHydraulicSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof HydraulicConnectionHeadMenu menu) || !menu.getBlockPos().equals(payload.pos())) {
                return;
            }

            if (player.level().getBlockEntity(payload.pos()) instanceof HydraulicConnectionHeadBlockEntity head) {
                if (head.isCreativeLink()) {
                    head.setSettingsAndMirror(0, false, payload.expectedLengthTenths(), 0);
                    head.setRedstoneLengthRangeAndMirror(payload.redstoneMinLengthTenths(), payload.redstoneMaxLengthTenths());
                    return;
                }
                if (head.isExpectedLengthControlledByRegulator()) {
                    head.setSettingsAndMirror(payload.stretchResistance(), false,
                            head.getExpectedLengthTenths(), payload.returnForce());
                    head.setRedstoneLengthRangeAndMirror(payload.redstoneMinLengthTenths(), payload.redstoneMaxLengthTenths());
                    return;
                }

                head.setSettingsAndMirror(payload.stretchResistance(), payload.freeMode(),
                        payload.expectedLengthTenths(), payload.returnForce());
                head.setRedstoneLengthRangeAndMirror(payload.redstoneMinLengthTenths(), payload.redstoneMaxLengthTenths());
            }
        });
    }
}
