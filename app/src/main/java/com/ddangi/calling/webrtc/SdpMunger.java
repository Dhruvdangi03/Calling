package com.ddangi.calling.webrtc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a locally-created offer/answer's Opus fmtp line to favor call resilience on slow or
 * lossy networks: in-band FEC, DTX (near-silent bitrate during pauses), and a capped average
 * bitrate so one call never saturates a weak uplink. Applied once, before setLocalDescription —
 * never touches the remote SDP.
 */
public final class SdpMunger {

    private static final Pattern OPUS_RTPMAP = Pattern.compile("a=rtpmap:(\\d+) opus/48000");
    private static final String TUNING_PARAMS = "maxaveragebitrate=32000;useinbandfec=1;usedtx=1";

    private SdpMunger() {
    }

    public static String preferResilientOpus(String sdp) {
        Matcher rtpmapMatcher = OPUS_RTPMAP.matcher(sdp);
        if (!rtpmapMatcher.find()) {
            return sdp; // No Opus line found; leave the SDP untouched.
        }
        String payloadType = rtpmapMatcher.group(1);

        Pattern fmtpPattern = Pattern.compile("a=fmtp:" + payloadType + "[^\\r\\n]*");
        Matcher fmtpMatcher = fmtpPattern.matcher(sdp);
        if (fmtpMatcher.find()) {
            String existingLine = fmtpMatcher.group();
            if (existingLine.contains("useinbandfec")) {
                return sdp; // Already tuned.
            }
            return sdp.replace(existingLine, existingLine + ";" + TUNING_PARAMS);
        }

        String rtpmapLine = rtpmapMatcher.group();
        String newFmtpLine = "a=fmtp:" + payloadType + " " + TUNING_PARAMS;
        return sdp.replace(rtpmapLine, rtpmapLine + "\r\n" + newFmtpLine);
    }
}
