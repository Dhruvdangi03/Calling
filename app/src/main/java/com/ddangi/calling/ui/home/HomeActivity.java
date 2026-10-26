package com.ddangi.calling.ui.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ddangi.calling.Constants;
import com.ddangi.calling.auth.AuthRepository;
import com.ddangi.calling.auth.LoginActivity;
import com.ddangi.calling.data.firestore.CallRepository;
import com.ddangi.calling.data.firestore.UserRepository;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallType;
import com.ddangi.calling.data.model.UserModel;
import com.ddangi.calling.databinding.ActivityHomeBinding;
import com.ddangi.calling.ui.call.IncomingCallActivity;
import com.ddangi.calling.ui.call.OutgoingCallActivity;
import com.ddangi.calling.utils.PhotoResolver;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Phase 2: shows the partner's name/photo, lets either call button start an outgoing call, and
 * watches for incoming calls while this screen is in the foreground. Push-based wake-up for a
 * killed/locked app is added in Phase 5; call history is wired up in Phase 7.
 */
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private CallRepository callRepository;

    private String myUid;
    private UserModel partner;

    private ListenerRegistration incomingCallListener;
    private String lastHandledIncomingCallId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository();
        userRepository = new UserRepository();
        callRepository = new CallRepository();

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }
        myUid = currentUser.getUid();

        loadPartner();

        binding.buttonAudioCall.setOnClickListener(v -> startOutgoingCall(CallType.AUDIO));
        binding.buttonVideoCall.setOnClickListener(v -> startOutgoingCall(CallType.VIDEO));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (myUid != null) {
            incomingCallListener = callRepository.listenForIncomingCalls(myUid, new CallRepository.CallDocListener() {
                @Override
                public void onCallChanged(CallModel call) {
                    if (call != null) {
                        handleIncomingCall(call);
                    }
                }

                @Override
                public void onError(String message) {
                    // Transient listener errors aren't actionable from here; the next snapshot retries.
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (incomingCallListener != null) {
            incomingCallListener.remove();
            incomingCallListener = null;
        }
    }

    private void loadPartner() {
        userRepository.fetchMeAndPartner(myUid, new UserRepository.UserWithPartnerCallback() {
            @Override
            public void onLoaded(UserModel me, UserModel loadedPartner) {
                partner = loadedPartner;
                binding.textPartnerName.setText(partner.getName());
                PhotoResolver.loadInto(binding.imagePartnerPhoto, partner.getPhotoResName());
                binding.buttonAudioCall.setEnabled(true);
                binding.buttonVideoCall.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                binding.textPartnerName.setText(message);
            }
        });
    }

    private void startOutgoingCall(CallType type) {
        if (partner == null) {
            return;
        }
        Intent intent = new Intent(this, OutgoingCallActivity.class);
        intent.putExtra(Constants.EXTRA_PARTNER_UID, partner.getUid());
        intent.putExtra(Constants.EXTRA_PARTNER_NAME, partner.getName());
        intent.putExtra(Constants.EXTRA_PARTNER_PHOTO_RES_NAME, partner.getPhotoResName());
        intent.putExtra(Constants.EXTRA_CALL_TYPE, type.name());
        startActivity(intent);
    }

    private void handleIncomingCall(CallModel call) {
        if (partner == null || call.getCallId().equals(lastHandledIncomingCallId)) {
            return;
        }
        lastHandledIncomingCallId = call.getCallId();

        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, call.getCallId());
        intent.putExtra(Constants.EXTRA_PARTNER_NAME, partner.getName());
        intent.putExtra(Constants.EXTRA_PARTNER_PHOTO_RES_NAME, partner.getPhotoResName());
        intent.putExtra(Constants.EXTRA_CALL_TYPE, call.getType());
        startActivity(intent);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
