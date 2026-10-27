package com.ddangi.calling;

public final class Constants {

    private Constants() {
    }

    // Firestore collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_CALLS = "calls";

    // users/{uid} fields
    public static final String FIELD_PARTNER_UID = "partnerUid";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_FCM_TOKEN = "fcmToken";
    public static final String FIELD_PHOTO_RES_NAME = "photoResName";

    // calls/{callId} fields
    public static final String FIELD_CALLER_UID = "callerUid";
    public static final String FIELD_CALLEE_UID = "calleeUid";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_ACCEPTED_AT = "acceptedAt";
    public static final String FIELD_ENDED_AT = "endedAt";
    public static final String FIELD_END_REASON = "endReason";

    // calls/{callId}/signaling/{offer|answer} documents
    public static final String SUBCOLLECTION_SIGNALING = "signaling";
    public static final String DOC_OFFER = "offer";
    public static final String DOC_ANSWER = "answer";
    public static final String FIELD_SDP = "sdp";

    // calls/{callId}/callerCandidates and calleeCandidates documents
    public static final String SUBCOLLECTION_CALLER_CANDIDATES = "callerCandidates";
    public static final String SUBCOLLECTION_CALLEE_CANDIDATES = "calleeCandidates";
    public static final String FIELD_CANDIDATE = "candidate";
    public static final String FIELD_SDP_MID = "sdpMid";
    public static final String FIELD_SDP_MLINE_INDEX = "sdpMLineIndex";

    // Intent extras shared between HomeActivity and the call screens
    public static final String EXTRA_CALL_ID = "extra_call_id";
    public static final String EXTRA_CALL_TYPE = "extra_call_type";
    public static final String EXTRA_PARTNER_UID = "extra_partner_uid";
    public static final String EXTRA_PARTNER_NAME = "extra_partner_name";
    public static final String EXTRA_PARTNER_PHOTO_RES_NAME = "extra_partner_photo_res_name";
}
