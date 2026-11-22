import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  writeBatch,
  serverTimestamp
} from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));
const rules = readFileSync(resolve(here, "../firestore.rules"), "utf8");
const projectId = "demo-mumbai-share-auto";
const passengerUid = "passenger_001";
const driverUid = "driver_001";
const adminUid = "admin_001";
const routeId = "ROUTE_TEST_001";
const negotiationId = "FARE_TEST_001";
const phone = "+919876543210";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { rules }
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

function user(uid, claims = {}) {
  return env.authenticatedContext(uid, claims).firestore();
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

async function seedPassengerAndDriver() {
  await seed(`users/${passengerUid}`, {
    uid: passengerUid,
    phoneNumber: phone,
    role: "PASSENGER",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  await seed(`users/${driverUid}`, {
    uid: driverUid,
    phoneNumber: "+919876543211",
    role: "DRIVER",
    driverAutoRegNumber: "MH02AB1234",
    createdAt: new Date(),
    updatedAt: new Date()
  });
}

async function seedRouteAndAgreement() {
  await seedPassengerAndDriver();
  await seed(`active_routes/${routeId}`, {
    postingId: routeId,
    driverUid,
    pickupStation: "Andheri",
    pickupSide: "EAST",
    designatedStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    viaPoints: ["MIDC"],
    totalBookingFare: 150,
    totalCapacitySeats: 3,
    availableSeats: 3,
    activeQueueCount: 0,
    queueSequence: 0,
    isActive: true,
    isLegallyVerified: false,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  await seed(`fare_negotiations/${negotiationId}`, {
    bookingId: negotiationId,
    routeId,
    passengerUid,
    driverUid,
    passengerCount: 2,
    pickupStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    events: [
      {
        eventId: "event_1",
        sequence: 1,
        actor: "PASSENGER",
        type: "PASSENGER_PROPOSED",
        totalFareInr: 100,
        createdAtEpochMs: 1000
      },
      {
        eventId: "event_2",
        sequence: 2,
        actor: "DRIVER",
        type: "DRIVER_ACCEPTED",
        totalFareInr: 100,
        createdAtEpochMs: 2000
      }
    ],
    status: "AGREED",
    agreedTotalFareInr: 100,
    createdAt: new Date(),
    updatedAt: new Date()
  });
}

function seatLockPayload(fare = 100) {
  return {
    lockId: passengerUid,
    postingId: routeId,
    passengerUid,
    driverUid,
    seatsLocked: 2,
    agreedTotalFare: fare,
    negotiationId,
    queuePosition: 1,
    status: "LOCKED",
    lockedAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
}

test("new account cannot self-create a DRIVER profile", async () => {
  const db = user(passengerUid, { phone_number: phone });
  await assertFails(setDoc(doc(db, `users/${passengerUid}`), {
    uid: passengerUid,
    phoneNumber: phone,
    role: "DRIVER",
    driverAutoRegNumber: "MH02AB1234",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});

test("new passenger profile and owner-only driver application are allowed", async () => {
  const db = user(passengerUid, { phone_number: phone });
  await assertSucceeds(setDoc(doc(db, `users/${passengerUid}`), {
    uid: passengerUid,
    phoneNumber: phone,
    role: "PASSENGER",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(db, `driver_applications/${passengerUid}`), {
    uid: passengerUid,
    phoneNumber: phone,
    autoRegNumber: "MH02AB1234",
    status: "PENDING",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
  const other = user("other_user", { phone_number: "+919876543212" });
  await assertFails(getDoc(doc(other, `driver_applications/${passengerUid}`)));
});

test("ordinary account cannot change its own role after creation", async () => {
  await seed(`users/${passengerUid}`, {
    uid: passengerUid,
    phoneNumber: phone,
    role: "PASSENGER",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const db = user(passengerUid, { phone_number: phone });
  await assertFails(updateDoc(doc(db, `users/${passengerUid}`), {
    role: "DRIVER",
    driverAutoRegNumber: "MH02AB1234",
    updatedAt: serverTimestamp()
  }));
});

test("trusted admin can approve a driver application and promote its profile", async () => {
  await seed(`admins/${adminUid}`, { enabled: true });
  await seed(`users/${passengerUid}`, {
    uid: passengerUid,
    phoneNumber: phone,
    role: "PASSENGER",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  await seed(`driver_applications/${passengerUid}`, {
    uid: passengerUid,
    phoneNumber: phone,
    autoRegNumber: "MH02AB1234",
    status: "PENDING",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const admin = user(adminUid);
  await assertSucceeds(updateDoc(doc(admin, `driver_applications/${passengerUid}`), {
    status: "APPROVED",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(admin, `users/${passengerUid}`), {
    role: "DRIVER",
    driverAutoRegNumber: "MH02AB1234",
    updatedAt: serverTimestamp()
  }));
});

test("seat lock accepts only the exact agreed fare and participant/route binding", async () => {
  await seedRouteAndAgreement();
  const passenger = user(passengerUid, { phone_number: phone });
  const batch = writeBatch(passenger);
  batch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 1,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  batch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), seatLockPayload(100));
  await assertSucceeds(batch.commit());
});

test("seat lock rejects a client-substituted fare", async () => {
  await seedRouteAndAgreement();
  const passenger = user(passengerUid, { phone_number: phone });
  const batch = writeBatch(passenger);
  batch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 1,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  batch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), seatLockPayload(125));
  await assertFails(batch.commit());
});

test("seat release decrements active queue count without rewinding queue sequence", async () => {
  await seedRouteAndAgreement();
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  const batch = writeBatch(passenger);
  batch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 3,
    activeQueueCount: 0,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  batch.update(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    updatedAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
});
