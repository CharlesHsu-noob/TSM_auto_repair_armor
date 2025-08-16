package com.example.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UnequipArmorC2SPacket() implements CustomPayload {
    public static final CustomPayload.Id<UnequipArmorC2SPacket> ID = 
        new CustomPayload.Id<>(Identifier.of("repairarmor", "unequip_armor"));
    
    public static final PacketCodec<PacketByteBuf, UnequipArmorC2SPacket> CODEC = 
        PacketCodec.unit(new UnequipArmorC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, (packet, context) -> {
            context.player().getServer().execute(() -> 
                UnequipArmorPacketHandler.receive(packet, context)
            );
        });
    }
}
