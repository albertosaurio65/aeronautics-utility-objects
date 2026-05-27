package com.enxv.aerouniversaljoint.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class TypedScrollOptionBehaviour<E extends Enum<E> & INamedIconOptions> extends ScrollOptionBehaviour<E> {
    private final BehaviourType<?> type;

    public TypedScrollOptionBehaviour(BehaviourType<?> type, Class<E> enumClass, Component label,
                                      SmartBlockEntity blockEntity, ValueBoxTransform slot) {
        super(enumClass, label, blockEntity, slot);
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
