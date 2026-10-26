package com.ddangi.calling.ui.call;

import android.animation.ValueAnimator;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.data.model.CallType;
import com.ddangi.calling.databinding.ActivityIncomingCallBinding;
import com.ddangi.calling.utils.PhotoResolver;
import com.ddangi.calling.utils.PulseAnimator;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Shown when HomeActivity's foreground listener spots a new ringing call for us. Lock-screen /
 * killed-app presentation (full-screen intent, ringtone, vibration) is added in Phase 5 — this
 * phase only needs the Accept/Decline flow to work while the app is in the foreground.
 */
public class IncomingCallActivity extends AppCompatActivity {

    private ActivityIncomingCallBinding binding;
    private CallRepository callRepository;
    private ListenerRegistration callListener;
    private ValueAnimator pulseAnimator;

    private String callId;
    private boolean navigatedToInCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIncomingCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        callRepository = new CallRepository();

        callId = getIntent().getStringExtra(Constants.EXTRA_CALL_ID);
        String partnerName = getIntent().getStringExtra(Constants.EXTRA_PARTNER_NAME);
        String partnerPhotoResName = getIntent().getStringExtra(Constants.EXTRA_PARTNER_PHOTO_RES_NAME);
        CallType type = CallType.valueOf(getIntent().getStringExtra(Constants.EXTRA_CALL_TYPE));

        binding.textPartnerName.setText(partnerName);
        binding.textCallStatus.setText(type == CallType.VIDEO ? "Incoming video call" : "Incoming call");
        PhotoResolver.loadInto(binding.imagePartnerPhoto, partnerPhotoResName);
        pulseAnimator = PulseAnimator.start(binding.viewPulseRing);

        binding.buttonAccept.setOnClickListener(v -> accept());
        binding.buttonDecline.setOnClickListener(v -> decline());

        listenForCancellation();
    }

    private void listenForCancellation() {
        callListener = callRepository.listenForCallDoc(callId, new CallRepository.CallDocListener() {
            @Override
            public void onCallChanged(CallModel call) {
                if (call == null || navigatedToInCall) {
                    return;
                }
                CallStatus status = call.getCallStatus();
                if (status == CallStatus.ENDED) {
                    // Caller cancelled before we responded.
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                // Not actionable here; the Accept/Decline buttons still work off local state.
            }
        });
    }

    private void accept() {
        callRepository.updateStatus(callId, CallStatus.ACCEPTED);
        navigatedToInCall = true;
        InCallActivity.start(this, callId);
        finish();
    }

    private void decline() {
        callRepository.updateStatus(callId, CallStatus.REJECTED);
        finish();
    }

    @Override
    public void onBackPressed() {
        decline();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callListener != null) {
            callListener.remove();
        }
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }
}
