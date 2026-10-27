package com.ddangi.calling.webrtc;

import android.content.Context;

import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Collections;
import java.util.List;

/**
 * Wraps a single PeerConnection for one call: local audio/video capture, the baseline
 * codec/bitrate tuning for slow-network resilience, and remote track rendering. One instance per
 * call; discarded via dispose() when the call ends.
 */
public class WebRtcClient {

    public interface Listener {
        void onLocalSdpCreated(SessionDescription sdp);

        void onLocalIceCandidate(IceCandidate candidate);

        void onRemoteVideoTrack(VideoTrack track);

        void onIceConnectionStateChanged(PeerConnection.IceConnectionState state);

        void onError(String message);
    }

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int VIDEO_FPS = 24;
    private static final int VIDEO_MAX_BITRATE_BPS = 500_000;

    // RtpParameters.Encoding#networkPriority is a raw int in this library version (no enum
    // wrapper), matching native WebRTC's Priority enum ordinal directly: VERY_LOW=0, LOW=1,
    // MEDIUM=2, HIGH=3.
    private static final int NETWORK_PRIORITY_HIGH = 3;

    private final Context appContext;
    private final PeerConnectionFactoryProvider factoryProvider;
    private final Listener listener;

    private PeerConnection peerConnection;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoSource videoSource;
    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private SurfaceViewRenderer remoteRenderer;

    public WebRtcClient(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.factoryProvider = PeerConnectionFactoryProvider.getInstance(appContext);
        this.listener = listener;
    }

    public void createPeerConnection(List<PeerConnection.IceServer> iceServers) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = factoryProvider.getFactory().createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                listener.onLocalIceCandidate(candidate);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                listener.onIceConnectionStateChanged(state);
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                MediaStreamTrack track = receiver.track();
                if (track instanceof VideoTrack) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    if (remoteRenderer != null) {
                        videoTrack.addSink(remoteRenderer);
                    }
                    listener.onRemoteVideoTrack(videoTrack);
                }
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // Unified Plan delivers tracks via onAddTrack above; nothing to do here.
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
            }

            @Override
            public void onRenegotiationNeeded() {
            }
        });

        if (peerConnection == null) {
            listener.onError("Failed to create the call connection.");
        }
    }

    /** Captures local audio (always) and, if videoEnabled, local video; attaches both to the peer connection. */
    public void startLocalMedia(boolean videoEnabled, @Nullable SurfaceViewRenderer localRenderer) {
        PeerConnectionFactory factory = factoryProvider.getFactory();

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("local_audio", audioSource);
        RtpSender audioSender = peerConnection.addTrack(localAudioTrack, Collections.singletonList("local_stream"));
        applyAudioPriority(audioSender);

        if (!videoEnabled) {
            return;
        }

        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) {
            listener.onError("No camera available on this device.");
            return;
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", factoryProvider.getEglBase().getEglBaseContext());
        videoSource = factory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        videoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);

        localVideoTrack = factory.createVideoTrack("local_video", videoSource);
        if (localRenderer != null) {
            localRenderer.init(factoryProvider.getEglBase().getEglBaseContext(), null);
            localRenderer.setMirror(true);
            localVideoTrack.addSink(localRenderer);
        }

        RtpSender videoSender = peerConnection.addTrack(localVideoTrack, Collections.singletonList("local_stream"));
        applyVideoCap(videoSender);
    }

    private void applyAudioPriority(RtpSender sender) {
        RtpParameters parameters = sender.getParameters();
        if (parameters == null || parameters.encodings.isEmpty()) {
            return;
        }
        for (RtpParameters.Encoding encoding : parameters.encodings) {
            encoding.networkPriority = NETWORK_PRIORITY_HIGH;
            encoding.bitratePriority = 2.0;
        }
        sender.setParameters(parameters);
    }

    private void applyVideoCap(RtpSender sender) {
        RtpParameters parameters = sender.getParameters();
        if (parameters == null || parameters.encodings.isEmpty()) {
            return;
        }
        for (RtpParameters.Encoding encoding : parameters.encodings) {
            encoding.maxBitrateBps = VIDEO_MAX_BITRATE_BPS;
            encoding.maxFramerate = VIDEO_FPS;
        }
        parameters.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE;
        sender.setParameters(parameters);
    }

    @Nullable
    private CameraVideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(appContext);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        for (String deviceName : enumerator.getDeviceNames()) {
            CameraVideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) {
                return capturer;
            }
        }
        return null;
    }

    public void attachRemoteRenderer(SurfaceViewRenderer renderer) {
        this.remoteRenderer = renderer;
        renderer.init(factoryProvider.getEglBase().getEglBaseContext(), null);
    }

    public void createOffer() {
        peerConnection.createOffer(sdpCreateObserver(), new MediaConstraints());
    }

    public void createAnswer() {
        peerConnection.createAnswer(sdpCreateObserver(), new MediaConstraints());
    }

    private SdpObserver sdpCreateObserver() {
        return new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                SessionDescription tuned = new SessionDescription(sdp.type, SdpMunger.preferResilientOpus(sdp.description));
                peerConnection.setLocalDescription(sdpSetObserver(), tuned);
                listener.onLocalSdpCreated(tuned);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
                listener.onError("Failed to create SDP: " + error);
            }

            @Override
            public void onSetFailure(String error) {
            }
        };
    }

    private SdpObserver sdpSetObserver() {
        return new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
            }

            @Override
            public void onSetFailure(String error) {
                listener.onError("Failed to set local SDP: " + error);
            }
        };
    }

    public void setRemoteDescription(SessionDescription sdp) {
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
            }

            @Override
            public void onSetFailure(String error) {
                listener.onError("Failed to set remote SDP: " + error);
            }
        }, sdp);
    }

    public void addRemoteIceCandidate(IceCandidate candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    public void setMicEnabled(boolean enabled) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }

    public void setCameraEnabled(boolean enabled) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled);
        }
    }

    public void switchCamera() {
        if (videoCapturer == null) {
            return;
        }
        videoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFrontCamera) {
            }

            @Override
            public void onCameraSwitchError(String errorDescription) {
                listener.onError("Camera switch failed: " + errorDescription);
            }
        });
    }

    public void dispose() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException ignored) {
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (remoteRenderer != null) {
            remoteRenderer.release();
            remoteRenderer = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
    }
}
