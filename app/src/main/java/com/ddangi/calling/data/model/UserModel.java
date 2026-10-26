package com.ddangi.calling.data.model;

import com.google.firebase.firestore.Exclude;

/** Mirrors a users/{uid} Firestore document. Needs a public no-arg constructor for Firestore's POJO mapping. */
public class UserModel {

    private String uid;
    private String name;
    private String fcmToken;
    private String partnerUid;
    private String photoResName;

    public UserModel() {
        // Required by Firestore
    }

    @Exclude
    public String getUid() {
        return uid;
    }

    /** Not stored in the document itself; set from the document ID after a read. */
    @Exclude
    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getPartnerUid() {
        return partnerUid;
    }

    public void setPartnerUid(String partnerUid) {
        this.partnerUid = partnerUid;
    }

    public String getPhotoResName() {
        return photoResName;
    }

    public void setPhotoResName(String photoResName) {
        this.photoResName = photoResName;
    }
}
