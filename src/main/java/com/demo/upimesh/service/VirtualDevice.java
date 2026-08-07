package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One simulated phone participating in the Bluetooth-style gossip mesh. */
public class VirtualDevice {

    private final String deviceId;
    private final boolean hasInternet;
    private final Map<String, MeshPacket> heldPackets = new ConcurrentHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean hasInternet() {
        return hasInternet;
    }

    public void receive(MeshPacket packet) {
        if (packet.getTtl() > 0) {
            heldPackets.put(packet.getPacketId(), packet);
        }
    }

    public Map<String, MeshPacket> getHeldPackets() {
        return new LinkedHashMap<>(heldPackets);
    }

    public void clear() {
        heldPackets.clear();
    }

    public int packetCount() {
        return heldPackets.size();
    }
}
