package com.ddangi.calling.ui.call;

import android.animation.ValueAnimator;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.data.model.CallType;
import com.ddangi.calling.databinding.ActivityOutgoingCallBinding;
import com.ddangi.calling.utils.PhotoResolver;
import com.ddangi.calling.utils.PulseAnimator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Shown by the caller right after tapping Audio/Video call on Home. Creates the call doc,
 * waits for the callee to accept/reject, and lets the caller cancel while it's still ringing.
 * No WebRTC yet — that's Phase 3.
 */
public class OutgoingCallActivity extends AppCompatActivity {

    private ActivityOutgoingCallBinding binding;
    private CallRepository callRepository;
    private ListenerRegistration callListener;
    private ValueAnimator pulseAnimator;

    private String callId;
    private boolean navigatedToInCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOutgoingCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        callRepository = new CallRepository();

        String partnerUid = getIntent().getStringExtra(Constants.EXTRA_PARTNER_UID);
        String partnerName = getIntent().getStringExtra(Constants.EXTRA_PARTNER_NAME);
        String partnerPhotoResName = getIntent().getStringExtra(Constants.EXTRA_PARTNER_PHOTO_RES_NAME);
        CallType type = CallType.valueOf(getIntent().getStringExtra(Constants.EXTRA_CALL_TYPE));

        binding.textPartnerName.setText(partnerName);
        binding.textCallStatus.setText(type == CallType.VIDEO ? "Video calling..." : "Calling...");
        PhotoResolver.loadInto(binding.imagePartnerPhoto, partnerPhotoResName);
        pulseAnimator = PulseAnimator.start(binding.viewPulseRing);

        binding.buttonCancel.setOnClickListener(v -> cancelAndFinish());

        String myUid = FirebaseAuth.getInstance().getUid();
        callRepository.createCall(myUid, partnerUid, type, new CallRepository.CreateCallCallback() {
            @Override
            public void onCreated(String createdCallId) {
                callId = createdCallId;
                listenForResponse();
            }

            @Override
            public void onError(String message) {
                binding.textCallStatus.setText(message);
            }
        });
    }

    private void listenForResponse() {
        callListener = callRepository.listenForCallDoc(callId, new CallRepository.CallDocListener() {
            @Override
            public void onCallChanged(CallModel call) {
                if (call == null) {
                    return;
                }
                CallStatus status = call.getCallStatus();
                if (status == CallStatus.ACCEPTED) {
                    goToInCall();
                } else if (status == CallStatus.REJECTED) {
                    binding.textCallStatus.setText("Declined");
                    finishShortlyAfterDecline();
                } else if (status == CallStatus.ENDED) {
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                binding.textCallStatus.setText(message);
            }
        });
    }

    private void goToInCall() {
        if (navigatedToInCall) {
            return;
        }
        navigatedToInCall = true;
        InCallActivity.start(this, callId);
        finish();
    }

    private void finishShortlyAfterDecline() {
        binding.getRoot().postDelayed(this::finish, 1200);
    }

    private void cancelAndFinish() {
        if (callId != null) {
            callRepository.updateStatus(callId, CallStatus.ENDED, "cancelled");
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        cancelAndFinish();
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
