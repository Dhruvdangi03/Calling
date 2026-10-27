package com.ddangi.calling.ui.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ddangi.calling.Constants;
import com.ddangi.calling.R;
import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.firestore.UserRepository;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.data.model.CallType;
import com.ddangi.calling.data.model.UserModel;
import com.ddangi.calling.databinding.ActivityInCallBinding;
import com.ddangi.calling.utils.PhotoResolver;
import com.ddangi.calling.webrtc.CallEngine;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;

import java.util.Map;

/**
 * Hosts the live WebRTC call once both sides have accepted: requests mic/camera permissions,
 * starts CallEngine, and renders local/remote video (or the partner's photo for an audio call).
 * Ends the call if the activity is backgrounded — making calls actually survive backgrounding is
 * Phase 8's job, not this one's; see docs/ROADMAP.md.
 */
public class InCallActivity extends AppCompatActivity {

    private ActivityInCallBinding binding;
    private CallRepository callRepository;
    private UserRepository userRepository;
    private ListenerRegistration callListener;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private CallEngine callEngine;

    private String callId;
    private String myUid;
    private CallModel pendingCall;

    private boolean endedLocally = false;
    private boolean engineStarted = false;
    private boolean permissionRequestInFlight = false;

    private boolean micEnabled = true;
    private boolean cameraEnabled = true;
    private boolean speakerEnabled = false;

    public static void start(Context context, String callId) {
        Intent intent = new Intent(context, InCallActivity.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        callRepository = new CallRepository();
        userRepository = new UserRepository();
        callId = getIntent().getStringExtra(Constants.EXTRA_CALL_ID);
        myUid = FirebaseAuth.getInstance().getUid();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

        setupControls();
        setupDraggableLocalPreview();
        loadPartnerPhoto();

        callListener = callRepository.listenForCallDoc(callId, new CallRepository.CallDocListener() {
            @Override
            public void onCallChanged(CallModel call) {
                if (call == null) {
                    return;
                }
                if (!endedLocally && call.getCallStatus() == CallStatus.ENDED) {
                    // The other side ended it.
                    finish();
                    return;
                }
                if (!engineStarted) {
                    engineStarted = true;
                    applyCallTypeUi(call.getCallType());
                    requestNeededPermissions(call);
                }
            }

            @Override
            public void onError(String message) {
                // Not actionable here.
            }
        });
    }

    private void requestNeededPermissions(CallModel call) {
        pendingCall = call;
        boolean needsCamera = call.getCallType() == CallType.VIDEO;
        String[] permissions = needsCamera
                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        permissionRequestInFlight = true;
        permissionLauncher.launch(permissions);
    }

    private void onPermissionResult(Map<String, Boolean> results) {
        permissionRequestInFlight = false;

        boolean audioGranted = hasPermission(Manifest.permission.RECORD_AUDIO);
        boolean cameraNeeded = pendingCall != null && pendingCall.getCallType() == CallType.VIDEO;
        boolean cameraGranted = !cameraNeeded || hasPermission(Manifest.permission.CAMERA);

        if (!audioGranted || !cameraGranted) {
            Toast.makeText(this, "Microphone/camera permission is required for calls.", Toast.LENGTH_LONG).show();
            endCall();
            return;
        }
        startCallEngine(pendingCall);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCallEngine(CallModel call) {
        boolean isVideo = call.getCallType() == CallType.VIDEO;

        callEngine = new CallEngine(this, new CallEngine.Listener() {
            @Override
            public void onIceConnectionStateChanged(PeerConnection.IceConnectionState state) {
                runOnUiThread(() -> updateConnectionLabel(state));
            }

            @Override
            public void onRemoteVideoTrack(VideoTrack track) {
                // WebRtcClient attaches the remote sink directly; nothing to do here.
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> binding.textConnectionStatus.setText(message));
            }
        });

        callEngine.start(callId, myUid, call.getCallerUid(), call.getCallType(),
                isVideo ? binding.rendererLocal : null,
                isVideo ? binding.rendererRemote : null);

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerEnabled);
    }

    private void updateConnectionLabel(PeerConnection.IceConnectionState state) {
        switch (state) {
            case CONNECTED:
            case COMPLETED:
                binding.textConnectionStatus.setText("Connected");
                break;
            case DISCONNECTED:
                binding.textConnectionStatus.setText("Reconnecting...");
                break;
            case FAILED:
                binding.textConnectionStatus.setText("Connection failed");
                break;
            default:
                binding.textConnectionStatus.setText("Connecting...");
        }
    }

    private void applyCallTypeUi(CallType callType) {
        boolean isVideo = callType == CallType.VIDEO;
        binding.rendererRemote.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        binding.cardLocalPreview.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        binding.layoutAudioOnly.setVisibility(isVideo ? View.GONE : View.VISIBLE);
        binding.buttonCamera.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        binding.buttonSwitchCamera.setVisibility(isVideo ? View.VISIBLE : View.GONE);
    }

    private void loadPartnerPhoto() {
        if (myUid == null) {
            return;
        }
        userRepository.fetchMeAndPartner(myUid, new UserRepository.UserWithPartnerCallback() {
            @Override
            public void onLoaded(UserModel me, UserModel partner) {
                binding.textPartnerNameAudio.setText(partner.getName());
                PhotoResolver.loadInto(binding.imagePartnerPhotoAudio, partner.getPhotoResName());
            }

            @Override
            public void onError(String message) {
                // Non-critical for the call itself; leave the placeholder photo/name.
            }
        });
    }

    private void setupControls() {
        binding.buttonEndCall.setOnClickListener(v -> endCall());

        binding.buttonMute.setOnClickListener(v -> {
            micEnabled = !micEnabled;
            if (callEngine != null) {
                callEngine.setMicEnabled(micEnabled);
            }
            binding.buttonMute.setIconResource(micEnabled ? R.drawable.ic_mic : R.drawable.ic_mic_off);
        });

        binding.buttonCamera.setOnClickListener(v -> {
            cameraEnabled = !cameraEnabled;
            if (callEngine != null) {
                callEngine.setCameraEnabled(cameraEnabled);
            }
            binding.buttonCamera.setIconResource(cameraEnabled ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
        });

        binding.buttonSwitchCamera.setOnClickListener(v -> {
            if (callEngine != null) {
                callEngine.switchCamera();
            }
        });

        binding.buttonSpeaker.setOnClickListener(v -> {
            speakerEnabled = !speakerEnabled;
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            audioManager.setSpeakerphoneOn(speakerEnabled);
            binding.buttonSpeaker.setIconResource(speakerEnabled ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);
        });
    }

    private void setupDraggableLocalPreview() {
        binding.cardLocalPreview.setOnTouchListener(new View.OnTouchListener() {
            private float dX;
            private float dY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        View parent = (View) view.getParent();
                        float newX = Math.max(0, Math.min(event.getRawX() + dX, parent.getWidth() - view.getWidth()));
                        float newY = Math.max(0, Math.min(event.getRawY() + dY, parent.getHeight() - view.getHeight()));
                        view.setX(newX);
                        view.setY(newY);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void endCall() {
        endedLocally = true;
        callRepository.updateStatus(callId, CallStatus.ENDED, "normal");
        finish();
    }

    @Override
    public void onBackPressed() {
        endCall();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Deliberate boundary for this phase: backgrounding mid-call ends it, rather than leaving
        // media running in an unsupported half-background state. Phase 8 owns doing this properly.
        // Guarded against the system permission-request dialog, which also triggers onStop.
        if (!endedLocally && !isFinishing() && !permissionRequestInFlight) {
            endCall();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callListener != null) {
            callListener.remove();
        }
        if (callEngine != null) {
            callEngine.dispose();
        }
    }
}
