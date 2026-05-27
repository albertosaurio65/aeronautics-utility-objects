package com.enxv.aerouniversaljoint.network;

import com.enxv.aerouniversaljoint.AeroUniversalJointMod;
import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockEntity;
import com.enxv.aerouniversaljoint.content.DampingStressBearingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDampingResistancePayload(BlockPos pos, int value) implements CustomPacketPayload {
    public static final Type<SetDampingResistancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "set_damping_resistance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDampingResistancePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SetDampingResistancePayload::pos,
            ByteBufCodecs.VAR_INT,
            SetDampingResistancePayload::value,
            SetDampingResistancePayload::new);

    @Override
    public Type<SetDampingResistancePayload> type() {
        return TYPE;
    }

    public static void handleServer(SetDampingResistancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof DampingStressBearingMenu menu) || !menu.getBlockPos().equals(payload.pos())) {
                return;
            }

            if (player.level().getBlockEntity(payload.pos()) instanceof DampingStressBearingBlockEntity bearing) {
                bearing.setResistanceValue(payload.value());
            }
        });
    }
}
