package com.ddangi.calling.ui.call;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.databinding.ActivityInCallBinding;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Phase 2 stub: just proves the call doc's lifecycle works end to end (both sides land here on
 * accept, either side can end it, and the other side's screen reacts). Real audio in Phase 3,
 * video in Phase 4.
 */
public class InCallActivity extends AppCompatActivity {

    private ActivityInCallBinding binding;
    private CallRepository callRepository;
    private ListenerRegistration callListener;
    private String callId;
    private boolean endedLocally = false;

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
        callId = getIntent().getStringExtra(Constants.EXTRA_CALL_ID);

        binding.buttonEndCall.setOnClickListener(v -> endCall());

        callListener = callRepository.listenForCallDoc(callId, new CallRepository.CallDocListener() {
            @Override
            public void onCallChanged(CallModel call) {
                if (!endedLocally && call != null && call.getCallStatus() == CallStatus.ENDED) {
                    // The other side ended it.
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                // Not actionable here.
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
        // Real dialer-style "move to background, keep call alive" behavior lands in Phase 8.
        // For this stub, back just ends the call like the End button.
        endCall();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callListener != null) {
            callListener.remove();
        }
    }
}
