package com.ddangi.calling.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.model.CallModel;
import com.ddangi.calling.data.model.CallStatus;
import com.ddangi.calling.data.model.CallType;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
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

    public interface SdpListener {
        void onSdpAvailable(@NonNull String sdp);
    }

    public interface IceCandidateListener {
        void onCandidateAdded(@NonNull String candidate, @Nullable String sdpMid, int sdpMLineIndex);
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

    // --- SDP signaling (calls/{callId}/signaling/offer|answer) ---

    public void sendOffer(@NonNull String callId, @NonNull String sdp) {
        writeSdp(callId, Constants.DOC_OFFER, sdp);
    }

    public ListenerRegistration listenForOffer(@NonNull String callId, @NonNull SdpListener listener) {
        return listenForSdp(callId, Constants.DOC_OFFER, listener);
    }

    public void sendAnswer(@NonNull String callId, @NonNull String sdp) {
        writeSdp(callId, Constants.DOC_ANSWER, sdp);
    }

    public ListenerRegistration listenForAnswer(@NonNull String callId, @NonNull SdpListener listener) {
        return listenForSdp(callId, Constants.DOC_ANSWER, listener);
    }

    private void writeSdp(@NonNull String callId, @NonNull String docId, @NonNull String sdp) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_SDP, sdp);
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        firestore.collection(Constants.COLLECTION_CALLS).document(callId)
                .collection(Constants.SUBCOLLECTION_SIGNALING).document(docId)
                .set(data);
    }

    private ListenerRegistration listenForSdp(@NonNull String callId, @NonNull String docId, @NonNull SdpListener listener) {
        return firestore.collection(Constants.COLLECTION_CALLS).document(callId)
                .collection(Constants.SUBCOLLECTION_SIGNALING).document(docId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    String sdp = snapshot.getString(Constants.FIELD_SDP);
                    if (sdp != null) {
                        listener.onSdpAvailable(sdp);
                    }
                });
    }

    // --- ICE candidate exchange (calls/{callId}/callerCandidates|calleeCandidates) ---

    /**
     * fromCaller selects WHO GENERATED the candidate (not the local role): each side writes its
     * own candidates to its own subcollection and listens to the other side's.
     */
    public void addIceCandidate(@NonNull String callId, boolean fromCaller,
                                 @NonNull String candidate, @Nullable String sdpMid, int sdpMLineIndex) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_CANDIDATE, candidate);
        data.put(Constants.FIELD_SDP_MID, sdpMid);
        data.put(Constants.FIELD_SDP_MLINE_INDEX, sdpMLineIndex);
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        firestore.collection(Constants.COLLECTION_CALLS).document(callId)
                .collection(candidatesSubcollection(fromCaller))
                .add(data);
    }

    public ListenerRegistration listenForIceCandidates(@NonNull String callId, boolean fromCaller,
                                                        @NonNull IceCandidateListener listener) {
        return firestore.collection(Constants.COLLECTION_CALLS).document(callId)
                .collection(candidatesSubcollection(fromCaller))
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    for (DocumentChange change : snapshot.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.ADDED) {
                            continue;
                        }
                        DocumentSnapshot doc = change.getDocument();
                        String candidate = doc.getString(Constants.FIELD_CANDIDATE);
                        String sdpMid = doc.getString(Constants.FIELD_SDP_MID);
                        Long sdpMLineIndex = doc.getLong(Constants.FIELD_SDP_MLINE_INDEX);
                        if (candidate != null && sdpMLineIndex != null) {
                            listener.onCandidateAdded(candidate, sdpMid, sdpMLineIndex.intValue());
                        }
                    }
                });
    }

    private String candidatesSubcollection(boolean fromCaller) {
        return fromCaller ? Constants.SUBCOLLECTION_CALLER_CANDIDATES : Constants.SUBCOLLECTION_CALLEE_CANDIDATES;
    }

    private String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Firestore error.";
    }
}
