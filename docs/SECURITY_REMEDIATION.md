# Security Remediation and Firebase Operations

This document describes the security model introduced by the P0 remediation branch. It is an operational checklist, not proof that a production Firebase project has been migrated.

## Driver onboarding and approval

- Phone OTP verifies control of a phone number only. It does not verify a driver, vehicle ownership, permit, fitness certificate, insurance, or legal passenger capacity.
- Every newly created `users/{uid}` profile must have `role: "PASSENGER"`.
- Selecting Driver in the Android onboarding flow creates a `driver_applications/{uid}` document with `status: "PENDING"`; it does not grant driver permissions.
- Only a trusted project administrator may approve an application. Approval requires independent verification of the driver's identity, vehicle registration, ownership/authorization, permit, insurance, fitness and applicable local rules.
- After review, a trusted administrator changes the application status to `APPROVED` and promotes the matching profile to `role: "DRIVER"`, copying the verified `autoRegNumber` into `driverAutoRegNumber`.
- Rejected applications can be resubmitted by the account owner. A new application does not grant access to driver routes.
- Never set `isLegallyVerified` to true based only on a phone OTP or a syntactically valid registration plate.

## Trusted administrator provisioning

The `admins/{uid}` collection is deliberately inaccessible to client SDKs. Provision admin documents only through Firebase Console or a trusted Admin SDK process. The document ID must be the administrator's Firebase Auth UID and its data must include:

```json
{ "enabled": true }
```

Do not create admin documents from the Android client. Remove or disable access immediately when an administrator leaves the project.

## Required pre-deployment migration

The new rules prevent future client-side driver self-promotion, but Firestore rules do not retroactively validate existing documents. Before deploying the rules to a project that has already accepted users or routes:

1. Export or back up Firestore data.
2. Review every existing `users` document with `role == "DRIVER"`. Until independently verified, downgrade it to `role: "PASSENGER"` and remove `driverAutoRegNumber`. Re-promote only after the driver is reviewed.
3. Remove `driverAutoRegNumber` from all existing `active_routes` documents. New route documents no longer publish vehicle registration numbers to every signed-in user.
4. For each existing route, calculate the number of current `seat_locks` with `status == "LOCKED"` and set `activeQueueCount` to that count. Set `queueSequence` to at least the maximum existing `queuePosition`. Preserve the route's actual `availableSeats`; reconcile discrepancies before reopening it.
5. Review stale or abandoned active routes and close them rather than silently advertising them.
6. Deploy the updated Android build and Firestore rules together. Older clients that still publish public vehicle plates or attempt direct seat locks are expected to be denied.

Do not deploy the new rules to a live project before this migration and backup are complete.

## Fare agreement and seat reservation

A seat reservation must reference the exact `fare_negotiations/{bookingId}` document. The transaction verifies that:

- The negotiation exists and is `AGREED`.
- The authenticated user is its passenger.
- The driver UID and route ID match the published route.
- The agreed whole-group fare matches exactly.
- The negotiated passenger count equals the seats being reserved.
- The route remains active and has sufficient available seats.

The Firestore rules independently enforce the same relationship. A client-supplied fare alone is never accepted as proof of agreement.

## Queue accounting

- `activeQueueCount` represents currently locked passenger parties.
- `queueSequence` is a monotonically increasing sequence used to allocate queue positions.
- Releasing a lock restores the seats and decrements `activeQueueCount`, but never rewinds `queueSequence`.
- This prevents duplicate queue positions after an earlier passenger releases a reservation.

## Privacy

Live route documents are readable by signed-in users and must not contain phone numbers or vehicle registration numbers. User profiles and driver applications are owner-only, with trusted administrator access for review. Do not place contact details, OTPs, payment credentials, or government identity documents in public route documents.

## Verification still required before production

- Run Firestore Rules Unit Testing against the deployed rules using the Firebase Emulator Suite.
- Verify role escalation is denied for ordinary authenticated users.
- Verify driver application creation, owner-only reads, admin-only approval, and approved profile promotion.
- Run two-account end-to-end tests for passenger offer, driver accept/counter/decline, passenger response, reservation, concurrent overbooking, cancellation and release.
- Verify a release decrements active queue count while preserving queue sequence.
- Test migration against a copy of production Firestore data.
- Confirm App Check, SMS abuse controls, Firebase quotas, backup/restore and privacy disclosures in the actual Firebase project.

A green Android build does not replace Firestore emulator tests or a live two-account verification.
