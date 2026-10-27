# Calling — Roadmap

Native Java Android app for exactly two people (a couple) to audio/video call each other.
Firebase Auth + Cloud Firestore, no backend server. Package `com.ddangi.calling`.

This file is the durable, in-repo record of phase progress and the decisions behind it, so any
future session — with or without prior chat context — can read this and know where things stand.

## Phase status

- **Phase 1 — done.** Project setup, Firebase wiring, login (email/password only, no sign-up —
  the 2 accounts are created manually in the Firebase console).
- **Phase 2 — done.** Partner-profile lookup (`UserRepository`), full Firestore call-document
  lifecycle (`RINGING → ACCEPTED/REJECTED → ENDED/MISSED`), Outgoing/Incoming call screens,
  foreground-only incoming-call detection on `HomeActivity`. `InCallActivity` was a stub with no
  media.
- **Phase 3+4 — this phase.** Real two-way WebRTC audio AND video calling (combined into one
  phase rather than audio-first), SDP/ICE signaling via new Firestore subcollections, a
  self-hosted coturn TURN server + Google STUN, baseline (one-time, non-adaptive) codec/bitrate
  tuning for slow-network resilience, and a version-controlled `firestore.rules` locking all data
  to the two known UIDs. See "Decisions" below.
- **Phase 5 — future.** FCM push wake-up for a killed/locked app; lock-screen full-screen
  incoming-call UI with ringtone/vibration.
- **Phase 6 — reserved, undefined.** Not yet scoped by any prior decision.
- **Phase 7 — future.** Call-history persistence + wiring `HomeActivity`'s already-present (but
  inert) RecyclerView/empty-state UI.
- **Phase 8 — future.** "Move to background, keep call alive" behavior for `InCallActivity`.
  Phase 3+4 deliberately does the opposite (ends the call on backgrounding) as a documented
  boundary — see Decisions.

## Decisions made in Phase 3+4

- **Audio + video shipped together**, not audio-first — a deliberate scope choice for this phase.
- **TURN pulled forward into this phase** (not deferred): self-hosted **coturn on an Oracle Cloud
  Always Free VM**, not Metered.ca. Metered.ca's current free offering is a 500MB/month trial, not
  a durable tier — exhausted in minutes/month at this app's relay bitrates. Oracle Always Free is
  free forever and comfortably covers 2-user traffic (credit card required at Oracle signup for
  identity verification only, not billed for Always-Free-eligible usage).
- **`firestore.rules` added now** (previously nonexistent/unversioned), locking `users/*`,
  `calls/*`, and the new SDP/ICE signaling subcollections to only the two known UIDs.
- **Only a one-time baseline** codec/bitrate/degradation config is applied this phase (Opus FEC/
  DTX/bitrate cap via SDP, audio RTP priority HIGH over video, video capped ~500kbps/24fps with
  `MAINTAIN_FRAMERATE` degradation). The full adaptive quality manager (stats polling, hysteresis,
  4-tier auto video-pause) is a separate future phase, not this one.
- **No foreground service yet.** `InCallActivity` stays foregrounded for the whole call; instead
  of leaving backgrounding in an undefined half-working state, `onStop()` proactively ends the
  call. Phase 8 owns making calls survive backgrounding properly.
- No external planning document exists elsewhere — this file plus inline code comments are the
  complete record.

## Key files by area

- Call signaling data layer: `app/src/main/java/com/ddangi/calling/data/firestore/CallRepository.java`,
  `app/src/main/java/com/ddangi/calling/data/model/CallModel.java`, `Constants.java`
- WebRTC: `app/src/main/java/com/ddangi/calling/webrtc/` (`PeerConnectionFactoryProvider`,
  `IceServerConfig`, `SdpMunger`, `WebRtcClient`, `CallEngine`)
- Call UI: `app/src/main/java/com/ddangi/calling/ui/call/` (`OutgoingCallActivity`,
  `IncomingCallActivity`, `InCallActivity`)
- Firestore security/deploy: `firestore.rules`, `firebase.json`, `firestore.indexes.json` (repo root)
- TURN/STUN credentials: `local.properties` (gitignored) → `BuildConfig` fields, never hardcoded

## Operational setup still required from the user (cannot be done from this environment)

- Create the Oracle Cloud Always Free VM, install/configure coturn, and put its URL/credentials
  into `local.properties`.
- Get the two real Firebase Auth UIDs and substitute them into `firestore.rules`.
- Run `firebase login` / `firebase deploy --only firestore:rules,firestore:indexes` (needs
  interactive auth).
- Manual two-device testing (same WiFi, then cross-network) per the test plan in the Phase 3+4
  plan file / PR description.
