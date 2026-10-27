package com.ddangi.calling.webrtc;

import android.content.Context;

import androidx.annotation.Nullable;

import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.model.CallType;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

/**
 * Glues WebRtcClient to Firestore signaling for a single call. The caller side creates the
 * offer, the callee side creates the answer; both sides write their own generated ICE candidates
 * to their own subcollection and listen to the other side's.
 */
public class CallEngine {

    public interface Listener {
        void onIceConnectionStateChanged(PeerConnection.IceConnectionState state);

        void onRemoteVideoTrack(VideoTrack track);

        void onError(String message);
    }

    private final CallRepository callRepository = new CallRepository();
    private final WebRtcClient webRtcClient;
    private final Listener listener;

    private String callId;
    private boolean isCaller;

    private ListenerRegistration offerListener;
    private ListenerRegistration answerListener;
    private ListenerRegistration remoteCandidatesListener;

    public CallEngine(Context context, Listener listener) {
        this.listener = listener;
        this.webRtcClient = new WebRtcClient(context, new WebRtcClient.Listener() {
            @Override
            public void onLocalSdpCreated(SessionDescription sdp) {
                if (isCaller) {
                    callRepository.sendOffer(callId, sdp.description);
                } else {
                    callRepository.sendAnswer(callId, sdp.description);
                }
            }

            @Override
            public void onLocalIceCandidate(IceCandidate candidate) {
                callRepository.addIceCandidate(callId, isCaller, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
            }

            @Override
            public void onRemoteVideoTrack(VideoTrack track) {
                listener.onRemoteVideoTrack(track);
            }

            @Override
            public void onIceConnectionStateChanged(PeerConnection.IceConnectionState state) {
                listener.onIceConnectionStateChanged(state);
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        });
    }

    public void start(String callId, String myUid, String callerUid, CallType type,
                       @Nullable SurfaceViewRenderer localRenderer, @Nullable SurfaceViewRenderer remoteRenderer) {
        this.callId = callId;
        this.isCaller = myUid.equals(callerUid);

        webRtcClient.createPeerConnection(IceServerConfig.buildIceServers());

        if (remoteRenderer != null) {
            webRtcClient.attachRemoteRenderer(remoteRenderer);
        }
        webRtcClient.startLocalMedia(type == CallType.VIDEO, localRenderer);

        // The other side's candidates always live in the collection opposite our own role.
        remoteCandidatesListener = callRepository.listenForIceCandidates(callId, !isCaller,
                (candidate, sdpMid, sdpMLineIndex) ->
                        webRtcClient.addRemoteIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate)));

        if (isCaller) {
            webRtcClient.createOffer();
            answerListener = callRepository.listenForAnswer(callId, sdp ->
                    webRtcClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, sdp)));
        } else {
            offerListener = callRepository.listenForOffer(callId, sdp -> {
                webRtcClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, sdp));
                webRtcClient.createAnswer();
            });
        }
    }

    public void setMicEnabled(boolean enabled) {
        webRtcClient.setMicEnabled(enabled);
    }

    public void setCameraEnabled(boolean enabled) {
        webRtcClient.setCameraEnabled(enabled);
    }

    public void switchCamera() {
        webRtcClient.switchCamera();
    }

    public void dispose() {
        if (offerListener != null) {
            offerListener.remove();
        }
        if (answerListener != null) {
            answerListener.remove();
        }
        if (remoteCandidatesListener != null) {
            remoteCandidatesListener.remove();
        }
        webRtcClient.dispose();
    }
}
