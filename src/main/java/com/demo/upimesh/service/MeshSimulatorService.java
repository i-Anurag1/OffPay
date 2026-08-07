package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simulates the Bluetooth mesh on a single laptop. Five virtual devices:
 * phone-alice (the sender's own phone), two strangers just relaying packets,
 * phone-bridge (has "internet" and periodically uploads), and phone-carol
 * (the receiver's own phone, mostly for symmetry/demo completeness).
 *
 * "Within Bluetooth range" is simplified to "everyone" - in reality this
 * would depend on physical proximity as people walk past each other.
 */
@Service
public class MeshSimulatorService {

    private final Map<String, VirtualDevice> devices = new LinkedHashMap<>();

    public MeshSimulatorService() {
        seedDevices();
    }

    private void seedDevices() {
        devices.clear();
        devices.put("phone-alice", new VirtualDevice("phone-alice", false));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false));
        devices.put("phone-bridge", new VirtualDevice("phone-bridge", true));
        devices.put("phone-carol", new VirtualDevice("phone-carol", false));
    }

    public void injectPacket(String originDeviceId, MeshPacket packet) {
        VirtualDevice origin = devices.get(originDeviceId);
        if (origin == null) {
            throw new IllegalArgumentException("Unknown device: " + originDeviceId);
        }
        origin.receive(packet);
    }

    /**
     * One gossip round: every device that holds a packet broadcasts it to
     * every other device. TTL decrements once per hop. A device that already
     * holds a given packetId doesn't re-add it (no infinite duplication),
     * but the TTL on its copy is not refreshed by receiving it again.
     */
    public void runGossipRound() {
        Map<String, MeshPacket> toBroadcast = new LinkedHashMap<>();
        for (VirtualDevice device : devices.values()) {
            for (MeshPacket packet : device.getHeldPackets().values()) {
                toBroadcast.putIfAbsent(packet.getPacketId(), packet);
            }
        }

        for (MeshPacket packet : toBroadcast.values()) {
            MeshPacket hopped = new MeshPacket(
                packet.getPacketId(),
                packet.getTtl(),
                packet.getCreatedAt(),
                packet.getCiphertext(),
                packet.getSenderPublicKeyBase64(),
                packet.getSignatureBase64()
            );
            hopped.decrementTtl();
            if (hopped.getTtl() <= 0) {
                continue;
            }
            for (VirtualDevice device : devices.values()) {
                if (!device.getHeldPackets().containsKey(packet.getPacketId())) {
                    device.receive(hopped);
                }
            }
        }
    }

    public Map<String, VirtualDevice> getDevices() {
        return devices;
    }

    public void reset() {
        seedDevices();
    }
}
