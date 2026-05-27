package com.enxv.aerouniversaljoint.network;

import com.enxv.aerouniversaljoint.AeroUniversalJointMod;
import com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity;
import com.enxv.aerouniversaljoint.content.UniversalJointMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetJointSpeedRatioPayload(BlockPos pos, float ratio) implements CustomPacketPayload {
    public static final Type<SetJointSpeedRatioPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroUniversalJointMod.MOD_ID, "set_joint_speed_ratio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetJointSpeedRatioPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SetJointSpeedRatioPayload::pos,
            ByteBufCodecs.FLOAT,
            SetJointSpeedRatioPayload::ratio,
            SetJointSpeedRatioPayload::new);

    @Override
    public Type<SetJointSpeedRatioPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetJointSpeedRatioPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof UniversalJointMenu menu) || !menu.getBlockPos().equals(payload.pos())) {
                return;
            }

            if (player.level().getBlockEntity(payload.pos()) instanceof UniversalJointBlockEntity joint) {
                joint.setSpeedRatio(payload.ratio());
            }
        });
    }
}
