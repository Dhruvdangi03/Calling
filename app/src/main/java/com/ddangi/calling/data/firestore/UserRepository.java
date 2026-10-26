package com.ddangi.calling.data.firestore;

import androidx.annotation.NonNull;

import com.ddangi.calling.Constants;
import com.ddangi.calling.data.model.UserModel;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {

    public interface UserWithPartnerCallback {
        void onLoaded(UserModel me, UserModel partner);

        void onError(String message);
    }

    private final FirebaseFirestore firestore;

    public UserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Loads my own users/{uid} doc, follows its partnerUid field, and loads the partner's doc
     * too. Since there are only ever 2 users, this is the only "pairing" lookup the app needs.
     */
    public void fetchMeAndPartner(@NonNull String myUid, @NonNull UserWithPartnerCallback callback) {
        firestore.collection(Constants.COLLECTION_USERS).document(myUid).get()
                .addOnSuccessListener(meSnapshot -> {
                    UserModel me = toUserModel(meSnapshot);
                    if (me == null) {
                        callback.onError("Your user profile doesn't exist yet in Firestore. " +
                                "Create a users/" + myUid + " document first.");
                        return;
                    }
                    String partnerUid = me.getPartnerUid();
                    if (partnerUid == null || partnerUid.isEmpty()) {
                        callback.onError("Your user profile has no partnerUid set yet.");
                        return;
                    }
                    firestore.collection(Constants.COLLECTION_USERS).document(partnerUid).get()
                            .addOnSuccessListener(partnerSnapshot -> {
                                UserModel partner = toUserModel(partnerSnapshot);
                                if (partner == null) {
                                    callback.onError("Partner profile users/" + partnerUid + " doesn't exist yet.");
                                    return;
                                }
                                callback.onLoaded(me, partner);
                            })
                            .addOnFailureListener(e -> callback.onError(describe(e)));
                })
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    private UserModel toUserModel(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            return null;
        }
        UserModel user = snapshot.toObject(UserModel.class);
        if (user != null) {
            user.setUid(snapshot.getId());
        }
        return user;
    }

    private String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Firestore error.";
    }
}
