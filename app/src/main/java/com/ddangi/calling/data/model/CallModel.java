package com.ddangi.calling.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Mirrors a calls/{callId} Firestore document (top-level fields only — SDP/ICE subcollections
 * are added in Phase 3). Needs a public no-arg constructor for Firestore's POJO mapping.
 */
public class CallModel {

    private String callId;
    private String callerUid;
    private String calleeUid;
    private String type;
    private String status;

    @ServerTimestamp
    private Timestamp createdAt;
    private Timestamp acceptedAt;
    private Timestamp endedAt;

    private Long durationSeconds;
    private String endReason;

    public CallModel() {
        // Required by Firestore
    }

    @Exclude
    public String getCallId() {
        return callId;
    }

    @Exclude
    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getCallerUid() {
        return callerUid;
    }

    public void setCallerUid(String callerUid) {
        this.callerUid = callerUid;
    }

    public String getCalleeUid() {
        return calleeUid;
    }

    public void setCalleeUid(String calleeUid) {
        this.calleeUid = calleeUid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Exclude
    public CallType getCallType() {
        return type != null ? CallType.valueOf(type) : null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Exclude
    public CallStatus getCallStatus() {
        return status != null ? CallStatus.valueOf(status) : null;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Timestamp acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Timestamp getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Timestamp endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }
}
