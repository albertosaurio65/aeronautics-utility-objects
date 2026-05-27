package com.enxv.aerouniversaljoint.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class TypedScrollValueBehaviour extends ScrollValueBehaviour {
    private final BehaviourType<?> type;

    public TypedScrollValueBehaviour(BehaviourType<?> type, Component label, SmartBlockEntity blockEntity,
                                     ValueBoxTransform slot) {
        super(label, blockEntity, slot);
        this.type = type;
    }

    @Override
    public BehaviourType<?> getType() {
        return this.type;
    }

    @Override
    public String getClipboardKey() {
        return this.type.getName();
    }

    @Override
    public boolean isSafeNBT() {
        return false;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
    }
}
