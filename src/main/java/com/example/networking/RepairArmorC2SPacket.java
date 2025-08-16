package com.example.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public record RepairArmorC2SPacket() implements CustomPayload {
    public static final CustomPayload.Id<RepairArmorC2SPacket> ID = 
        new CustomPayload.Id<>(Identifier.of("repairarmor", "repair_armor"));
        
    public static final PacketCodec<PacketByteBuf, RepairArmorC2SPacket> CODEC = 
        PacketCodec.unit(new RepairArmorC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }
    
    public static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new RepairArmorC2SPacket());
    }
}
