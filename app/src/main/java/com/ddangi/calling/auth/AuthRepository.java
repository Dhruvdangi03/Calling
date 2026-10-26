package com.ddangi.calling.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Thin wrapper around FirebaseAuth. No sign-up flow: the 2 accounts are created manually in the Firebase console. */
public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);

        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;

    public AuthRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public void login(@NonNull String email, @NonNull String password, @NonNull AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        callback.onSuccess(user);
                    } else {
                        callback.onError("Login succeeded but no user was returned.");
                    }
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Login failed."));
    }

    public void logout() {
        firebaseAuth.signOut();
    }
}
