package com.ddangi.calling.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.data.model.CallType;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class CallRepository {

    public interface CreateCallCallback {
        void onCreated(String callId);

        void onError(String message);
    }

    public interface CallDocListener {
        /** Called with null if the call document was deleted (never happens in normal flow, but defensive). */
        void onCallChanged(@Nullable CallModel call);

        void onError(String message);
    }

    private final FirebaseFirestore firestore;

    public CallRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Creates a new calls/{callId} document with status=RINGING. The callId is generated
     * client-side so the caller already knows it before the callee has written anything
     * (needed from Phase 3 onward, to attach SDP/ICE subcollections immediately).
     */
    public void createCall(@NonNull String callerUid, @NonNull String calleeUid, @NonNull CallType type,
                            @NonNull CreateCallCallback callback) {
        DocumentReference callDoc = firestore.collection(Constants.COLLECTION_CALLS).document();
        String callId = callDoc.getId();

        CallModel call = new CallModel();
        call.setCallerUid(callerUid);
        call.setCalleeUid(calleeUid);
        call.setType(type.name());
        call.setStatus(CallStatus.RINGING.name());
        // createdAt is @ServerTimestamp-annotated and left null here; Firestore fills it in on write.

        callDoc.set(call)
                .addOnSuccessListener(unused -> callback.onCreated(callId))
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    public void updateStatus(@NonNull String callId, @NonNull CallStatus status) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.FIELD_STATUS, status.name());

        if (status == CallStatus.ACCEPTED) {
            updates.put(Constants.FIELD_ACCEPTED_AT, FieldValue.serverTimestamp());
        } else if (status == CallStatus.ENDED || status == CallStatus.REJECTED || status == CallStatus.MISSED) {
            updates.put(Constants.FIELD_ENDED_AT, FieldValue.serverTimestamp());
        }

        firestore.collection(Constants.COLLECTION_CALLS).document(callId).update(updates);
    }

    public void updateStatus(@NonNull String callId, @NonNull CallStatus status, @NonNull String endReason) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.FIELD_STATUS, status.name());
        updates.put(Constants.FIELD_END_REASON, endReason);
        updates.put(Constants.FIELD_ENDED_AT, FieldValue.serverTimestamp());

        firestore.collection(Constants.COLLECTION_CALLS).document(callId).update(updates);
    }

    /** Listens to one call document — used by Outgoing/Incoming/InCall screens to react to status changes. */
    public ListenerRegistration listenForCallDoc(@NonNull String callId, @NonNull CallDocListener listener) {
        return firestore.collection(Constants.COLLECTION_CALLS).document(callId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onCallChanged(null);
                        return;
                    }
                    CallModel call = snapshot.toObject(CallModel.class);
                    if (call != null) {
                        call.setCallId(snapshot.getId());
                    }
                    listener.onCallChanged(call);
                });
    }

    /**
     * Persistent listener for incoming calls, meant to run whenever HomeActivity is foregrounded.
     * Only catches calls while the app is in the foreground — Phase 5 adds push-based wake-up for
     * when the app is killed or the phone is locked.
     */
    public ListenerRegistration listenForIncomingCalls(@NonNull String myUid, @NonNull CallDocListener listener) {
        return firestore.collection(Constants.COLLECTION_CALLS)
                .whereEqualTo(Constants.FIELD_CALLEE_UID, myUid)
                .whereEqualTo(Constants.FIELD_STATUS, CallStatus.RINGING.name())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }
                    // Only ever 2 users and one call at a time in practice; take the first match.
                    CallModel call = snapshot.getDocuments().get(0).toObject(CallModel.class);
                    if (call != null) {
                        call.setCallId(snapshot.getDocuments().get(0).getId());
                        listener.onCallChanged(call);
                    }
                });
    }

    private String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Firestore error.";
    }
}
