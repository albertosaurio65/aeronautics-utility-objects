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

public record SetHydraulicHingeLimitsPayload(BlockPos pos, int minAngle, int maxAngle) implements CustomPacketPayload {
    public static final Type<SetHydraulicHingeLimitsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "set_hydraulic_hinge_limits"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetHydraulicHingeLimitsPayload> STREAM_CODEC = StreamCodec.of(
            SetHydraulicHingeLimitsPayload::encode, SetHydraulicHingeLimitsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, SetHydraulicHingeLimitsPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeVarInt(payload.minAngle());
        buffer.writeVarInt(payload.maxAngle());
    }

    private static SetHydraulicHingeLimitsPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SetHydraulicHingeLimitsPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<SetHydraulicHingeLimitsPayload> type() { return TYPE; }

    public static void handleServer(SetHydraulicHingeLimitsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof HydraulicConnectionHeadMenu menu)
                    || menu.isRodSettingsMode() || !menu.getBlockPos().equals(payload.pos())
                    || !menu.isBrassHingeHead()) {
                return;
            }
            HydraulicConnectionHeadBlockEntity head = menu.getBlockEntity();
            if (head != null) {
                head.setHingeAngleLimits(payload.minAngle(), payload.maxAngle());
            }
        });
    }
}
