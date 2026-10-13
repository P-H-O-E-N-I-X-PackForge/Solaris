package net.phoenixvine.solaris.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;

import java.util.HashMap;
import java.util.Map;

public record S2CSyncFeatureStatePacket(
                                        Map<String, Integer> tiers,
                                        Map<String, Boolean> features,
                                        Map<String, Map<String, SolarisFeatureState>> states)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSyncFeatureStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_sync_feature_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncFeatureStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                // Encode tiers map
                buf.writeVarInt(packet.tiers().size());
                for (Map.Entry<String, Integer> entry : packet.tiers().entrySet()) {
                    buf.writeUtf(entry.getKey(), 128);
                    buf.writeVarInt(entry.getValue());
                }

                // Encode features map
                buf.writeVarInt(packet.features().size());
                for (Map.Entry<String, Boolean> entry : packet.features().entrySet()) {
                    buf.writeUtf(entry.getKey(), 64);
                    buf.writeBoolean(entry.getValue());
                }

                // Encode nested states map
                buf.writeVarInt(packet.states().size());
                for (Map.Entry<String, Map<String, SolarisFeatureState>> dimensionEntry : packet.states().entrySet()) {
                    buf.writeUtf(dimensionEntry.getKey(), 128);
                    buf.writeVarInt(dimensionEntry.getValue().size());
                    for (Map.Entry<String, SolarisFeatureState> featureEntry : dimensionEntry.getValue().entrySet()) {
                        buf.writeUtf(featureEntry.getKey(), 64);
                        buf.writeEnum(featureEntry.getValue());
                    }
                }
            },
            buf -> {
                // Decode tiers map
                int tierCount = buf.readVarInt();
                Map<String, Integer> tierMap = new HashMap<>();
                for (int i = 0; i < tierCount; i++) {
                    tierMap.put(buf.readUtf(128), buf.readVarInt());
                }

                // Decode features map
                int featureCount = buf.readVarInt();
                Map<String, Boolean> featureMap = new HashMap<>();
                for (int i = 0; i < featureCount; i++) {
                    featureMap.put(buf.readUtf(64), buf.readBoolean());
                }

                // Decode nested states map
                int dimensionCount = buf.readVarInt();
                Map<String, Map<String, SolarisFeatureState>> stateMap = new HashMap<>();
                for (int i = 0; i < dimensionCount; i++) {
                    String dimension = buf.readUtf(128);
                    int perDimensionCount = buf.readVarInt();
                    Map<String, SolarisFeatureState> perDimension = new HashMap<>();
                    for (int j = 0; j < perDimensionCount; j++) {
                        String featureId = buf.readUtf(64);
                        perDimension.put(featureId, buf.readEnum(SolarisFeatureState.class));
                    }
                    stateMap.put(dimension, perDimension);
                }

                return new S2CSyncFeatureStatePacket(tierMap, featureMap, stateMap);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSyncFeatureStatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;

            for (Map.Entry<String, Integer> entry : packet.tiers().entrySet()) {
                SolarisAPI.setTier(ResourceLocation.parse(entry.getKey()), entry.getValue());
            }
            for (Map.Entry<String, Boolean> entry : packet.features().entrySet()) {
                SolarisAPI.setFeatureEnabled(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Map<String, SolarisFeatureState>> dimensionEntry : packet.states().entrySet()) {
                ResourceLocation dimension = ResourceLocation.parse(dimensionEntry.getKey());
                for (Map.Entry<String, SolarisFeatureState> featureEntry : dimensionEntry.getValue().entrySet()) {
                    SolarisAPI.setFeatureState(featureEntry.getKey(), dimension, featureEntry.getValue());
                }
            }
        });
    }
}
