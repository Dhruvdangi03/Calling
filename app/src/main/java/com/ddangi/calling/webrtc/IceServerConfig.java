package com.ddangi.calling.webrtc;

import com.ddangi.calling.BuildConfig;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ICE server list from BuildConfig fields sourced from local.properties (see
 * app/build.gradle). Degrades gracefully to STUN-only if no TURN credentials are configured —
 * that's enough for same-network calls, just not across restrictive/separate networks.
 */
public final class IceServerConfig {

    private IceServerConfig() {
    }

    public static List<PeerConnection.IceServer> buildIceServers() {
        List<PeerConnection.IceServer> servers = new ArrayList<>();

        if (!BuildConfig.STUN_URL.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(BuildConfig.STUN_URL).createIceServer());
        }

        boolean hasTurnCredentials = !BuildConfig.TURN_USERNAME.isEmpty() && !BuildConfig.TURN_CREDENTIAL.isEmpty();

        if (hasTurnCredentials && !BuildConfig.TURN_URL_UDP.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(BuildConfig.TURN_URL_UDP)
                    .setUsername(BuildConfig.TURN_USERNAME)
                    .setPassword(BuildConfig.TURN_CREDENTIAL)
                    .createIceServer());
        }

        if (hasTurnCredentials && !BuildConfig.TURN_URL_TCP.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(BuildConfig.TURN_URL_TCP)
                    .setUsername(BuildConfig.TURN_USERNAME)
                    .setPassword(BuildConfig.TURN_CREDENTIAL)
                    .createIceServer());
        }

        return servers;
    }
}
