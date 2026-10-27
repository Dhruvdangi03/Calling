package com.ddangi.calling.webrtc;

import android.content.Context;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.audio.JavaAudioDeviceModule;

/**
 * One PeerConnectionFactory + EglBase per process. WebRTC's native init is a global one-time
 * operation, so this is a true singleton rather than a per-call object like WebRtcClient.
 */
public final class PeerConnectionFactoryProvider {

    private static PeerConnectionFactoryProvider instance;

    private final EglBase eglBase;
    private final PeerConnectionFactory factory;

    private PeerConnectionFactoryProvider(Context appContext) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .createInitializationOptions());

        eglBase = EglBase.create();

        JavaAudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                        eglBase.getEglBaseContext(), /* enableIntelVp8Encoder= */ true, /* enableH264HighProfile= */ true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory();
    }

    public static synchronized PeerConnectionFactoryProvider getInstance(Context context) {
        if (instance == null) {
            instance = new PeerConnectionFactoryProvider(context.getApplicationContext());
        }
        return instance;
    }

    public PeerConnectionFactory getFactory() {
        return factory;
    }

    public EglBase getEglBase() {
        return eglBase;
    }
}
