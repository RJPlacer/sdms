package com.sdms.sharing;

/** A same-Wi-Fi Athlete Track host found through local-network discovery. */
public record HostOffer(String name, String host, int pairingPort) {
    @Override public String toString() { return name + " (" + host + ")"; }
}
