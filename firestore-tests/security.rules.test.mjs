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
  serverTimestamp,
  deleteField
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

async function seedRouteAndAgreement({
  availableSeats = 3,
  activeQueueCount = 0,
  queueSequence = 0
} = {}) {
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
    availableSeats,
    activeQueueCount,
    queueSequence,
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
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
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


test("fare negotiation history must remain in exact append-only order", async () => {
  await seedRouteAndAgreement();
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
        type: "DRIVER_COUNTERED",
        totalFareInr: 120,
        createdAtEpochMs: 2000
      }
    ],
    status: "COUNTERED",
    agreedTotalFareInr: null,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  const reorderedHistory = [
    {
      eventId: "event_2",
      sequence: 2,
      actor: "DRIVER",
      type: "DRIVER_COUNTERED",
      totalFareInr: 120,
      createdAtEpochMs: 2000
    },
    {
      eventId: "event_1",
      sequence: 1,
      actor: "PASSENGER",
      type: "PASSENGER_PROPOSED",
      totalFareInr: 100,
      createdAtEpochMs: 1000
    },
    {
      eventId: "event_3",
      sequence: 3,
      actor: "PASSENGER",
      type: "PASSENGER_ACCEPTED_COUNTER",
      totalFareInr: 120,
      createdAtEpochMs: 3000
    }
  ];
  await assertFails(updateDoc(doc(passenger, `fare_negotiations/${negotiationId}`), {
    events: reorderedHistory,
    status: "AGREED",
    agreedTotalFareInr: 120,
    updatedAt: serverTimestamp()
  }));
});

test("driver cannot publish vehicle registration in public route document", async () => {
  await seedPassengerAndDriver();
  const driver = user(driverUid, { phone_number: "+919876543211" });
  await assertFails(setDoc(doc(driver, `active_routes/${routeId}`), {
    postingId: routeId,
    driverUid,
    driverAutoRegNumber: "MH02AB1234",
    pickupStation: "Andheri",
    pickupSide: "EAST",
    designatedStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    viaPoints: [],
    totalBookingFare: 150,
    totalCapacitySeats: 3,
    availableSeats: 3,
    activeQueueCount: 0,
    queueSequence: 0,
    isActive: true,
    isLegallyVerified: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});


test("passenger can accept a driver counter while preserving negotiation history", async () => {
  await seedRouteAndAgreement();
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
        type: "DRIVER_COUNTERED",
        totalFareInr: 120,
        createdAtEpochMs: 2000
      }
    ],
    status: "COUNTERED",
    agreedTotalFareInr: null,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  await assertSucceeds(updateDoc(doc(passenger, `fare_negotiations/${negotiationId}`), {
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
        type: "DRIVER_COUNTERED",
        totalFareInr: 120,
        createdAtEpochMs: 2000
      },
      {
        eventId: "event_3",
        sequence: 3,
        actor: "PASSENGER",
        type: "PASSENGER_ACCEPTED_COUNTER",
        totalFareInr: 120,
        createdAtEpochMs: 3000
      }
    ],
    status: "AGREED",
    agreedTotalFareInr: 120,
    updatedAt: serverTimestamp()
  }));
});


test("approved route driver can reveal vehicle number only to a currently locked passenger", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const driver = user(driverUid, { phone_number: "+919876543211" });
  const lockRef = doc(driver, `active_routes/${routeId}/seat_locks/${passengerUid}`);
  await assertFails(updateDoc(lockRef, {
    driverAutoRegNumber: "MH02XY9999",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(lockRef, {
    driverAutoRegNumber: "MH02AB1234",
    updatedAt: serverTimestamp()
  }));
  const passenger = user(passengerUid, { phone_number: phone });
  const revealed = await getDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`));
  assert.equal(revealed.data().driverAutoRegNumber, "MH02AB1234");
  const unrelatedPassenger = user("unrelated_passenger", { phone_number: "+919876543212" });
  await assertFails(getDoc(doc(unrelatedPassenger, `active_routes/${routeId}/seat_locks/${passengerUid}`)));
});

test("passenger cannot forge the driver's vehicle number in their seat lock", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  await assertFails(updateDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    driverAutoRegNumber: "MH02AB1234",
    updatedAt: serverTimestamp()
  }));
});

test("released seat lock removes previously shared vehicle number", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    driverAutoRegNumber: "MH02AB1234",
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
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
  const released = await getDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`));
  assert.equal(released.data().driverAutoRegNumber, undefined);
});


test("trusted admin can revoke a driver role after rejecting the application", async () => {
  await seedPassengerAndDriver();
  await seed(`admins/${adminUid}`, { enabled: true });
  await seed(`driver_applications/${driverUid}`, {
    uid: driverUid,
    phoneNumber: "+919876543211",
    autoRegNumber: "MH02AB1234",
    status: "APPROVED",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const admin = user(adminUid);
  await assertSucceeds(updateDoc(doc(admin, `driver_applications/${driverUid}`), {
    status: "REJECTED",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(admin, `users/${driverUid}`), {
    role: "PASSENGER",
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  }));
  const driver = user(driverUid, { phone_number: "+919876543211" });
  const profile = await getDoc(doc(driver, `users/${driverUid}`));
  assert.equal(profile.data().role, "PASSENGER");
  assert.equal(profile.data().driverAutoRegNumber, undefined);
});

test("standalone LOCKED to RELEASED update on seat_locks without active_routes update fails", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  await assertFails(updateDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  }));
});

test("standalone RELEASED to LOCKED update on seat_locks without active_routes update fails", async () => {
  await seedRouteAndAgreement({ availableSeats: 3, activeQueueCount: 0, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    queuePosition: 1,
    status: "RELEASED",
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  await assertFails(setDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    queuePosition: 1
  }));
  await assertFails(updateDoc(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    queuePosition: 2,
    status: "LOCKED",
    lockedAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});

test("seat lock with mismatched queuePosition and active_routes queueSequence fails", async () => {
  await seedRouteAndAgreement();
  const passenger = user(passengerUid, { phone_number: phone });
  const batch = writeBatch(passenger);
  batch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 1,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  batch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    queuePosition: 2
  });
  await assertFails(batch.commit());
});

test("initial fare negotiation requires events[0].sequence == 1 and rejects 0 or 2", async () => {
  await seedRouteAndAgreement();
  const passenger = user(passengerUid, { phone_number: phone });
  const buildOffer = (bookingId, sequence) => ({
    bookingId,
    routeId,
    passengerUid,
    driverUid,
    passengerCount: 2,
    pickupStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    events: [
      {
        eventId: "event_1",
        sequence,
        actor: "PASSENGER",
        type: "PASSENGER_PROPOSED",
        totalFareInr: 100,
        createdAtEpochMs: 1000
      }
    ],
    status: "OPEN",
    agreedTotalFareInr: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });

  await assertFails(setDoc(doc(passenger, "fare_negotiations/FARE_INIT_ZERO"), buildOffer("FARE_INIT_ZERO", 0)));
  await assertFails(setDoc(doc(passenger, "fare_negotiations/FARE_INIT_TWO"), buildOffer("FARE_INIT_TWO", 2)));
  await assertSucceeds(setDoc(doc(passenger, "fare_negotiations/FARE_INIT_ONE"), buildOffer("FARE_INIT_ONE", 1)));
});

test("paired RELEASED to LOCKED re-lock succeeds and increments queueSequence monotonically", async () => {
  await seedRouteAndAgreement({ availableSeats: 3, activeQueueCount: 0, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    queuePosition: 1,
    status: "RELEASED",
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });
  const batch = writeBatch(passenger);
  batch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 1,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  batch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    queuePosition: 2
  });
  await assertSucceeds(batch.commit());
});

test("zero available seats and overbooking requests fail", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  const passenger = user(passengerUid, { phone_number: phone });
  const overbookBatch = writeBatch(passenger);
  overbookBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: -1,
    activeQueueCount: 2,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  overbookBatch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    queuePosition: 2
  });
  await assertFails(overbookBatch.commit());

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
    availableSeats: 0,
    activeQueueCount: 1,
    queueSequence: 1,
    isActive: true,
    isLegallyVerified: false,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const zeroSeatsBatch = writeBatch(passenger);
  zeroSeatsBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 0,
    activeQueueCount: 2,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  zeroSeatsBatch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    seatsLocked: 1,
    queuePosition: 2
  });
  await assertFails(zeroSeatsBatch.commit());
});

test("maximum vehicle capacity over 3 seats fails across route creation and negotiation", async () => {
  await seedPassengerAndDriver();
  const driver = user(driverUid, { phone_number: "+919876543211" });
  await assertFails(setDoc(doc(driver, `active_routes/${routeId}`), {
    postingId: routeId,
    driverUid,
    pickupStation: "Andheri",
    pickupSide: "EAST",
    designatedStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    viaPoints: [],
    totalBookingFare: 150,
    totalCapacitySeats: 4,
    availableSeats: 4,
    activeQueueCount: 0,
    queueSequence: 0,
    isActive: true,
    isLegallyVerified: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));

  await seedRouteAndAgreement({ availableSeats: 3, activeQueueCount: 0, queueSequence: 0 });
  const passenger = user(passengerUid, { phone_number: phone });
  await assertFails(setDoc(doc(passenger, "fare_negotiations/FARE_OVER_CAP"), {
    bookingId: "FARE_OVER_CAP",
    routeId,
    passengerUid,
    driverUid,
    passengerCount: 4,
    pickupStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    events: [
      {
        eventId: "event_1",
        sequence: 1,
        actor: "PASSENGER",
        type: "PASSENGER_PROPOSED",
        totalFareInr: 150,
        createdAtEpochMs: 1000
      }
    ],
    status: "OPEN",
    agreedTotalFareInr: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});

test("multiple passengers allocate monotonic queue positions even after an earlier release", async () => {
  await seedRouteAndAgreement({ availableSeats: 3, activeQueueCount: 0, queueSequence: 0 });
  const passenger2Uid = "passenger_002";
  const passenger2Phone = "+919876543219";
  await seed(`users/${passenger2Uid}`, {
    uid: passenger2Uid,
    phoneNumber: passenger2Phone,
    role: "PASSENGER",
    createdAt: new Date(),
    updatedAt: new Date()
  });
  await seed(`fare_negotiations/${negotiationId}`, {
    bookingId: negotiationId,
    routeId,
    passengerUid,
    driverUid,
    passengerCount: 1,
    pickupStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    events: [
      { eventId: "e1", sequence: 1, actor: "PASSENGER", type: "PASSENGER_PROPOSED", totalFareInr: 90, createdAtEpochMs: 1000 },
      { eventId: "e2", sequence: 2, actor: "DRIVER", type: "DRIVER_ACCEPTED", totalFareInr: 90, createdAtEpochMs: 2000 }
    ],
    status: "AGREED",
    agreedTotalFareInr: 90,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const neg2Id = "FARE_TEST_002";
  await seed(`fare_negotiations/${neg2Id}`, {
    bookingId: neg2Id,
    routeId,
    passengerUid: passenger2Uid,
    driverUid,
    passengerCount: 1,
    pickupStand: "Andheri East Auto Deck Stand",
    destination: "SEEPZ",
    events: [
      { eventId: "e1", sequence: 1, actor: "PASSENGER", type: "PASSENGER_PROPOSED", totalFareInr: 95, createdAtEpochMs: 1000 },
      { eventId: "e2", sequence: 2, actor: "DRIVER", type: "DRIVER_ACCEPTED", totalFareInr: 95, createdAtEpochMs: 2000 }
    ],
    status: "AGREED",
    agreedTotalFareInr: 95,
    createdAt: new Date(),
    updatedAt: new Date()
  });

  const p1 = user(passengerUid, { phone_number: phone });
  const lock1Batch = writeBatch(p1);
  lock1Batch.update(doc(p1, `active_routes/${routeId}`), {
    availableSeats: 2,
    activeQueueCount: 1,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  lock1Batch.set(doc(p1, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(90),
    seatsLocked: 1,
    queuePosition: 1
  });
  await assertSucceeds(lock1Batch.commit());

  const p2 = user(passenger2Uid, { phone_number: passenger2Phone });
  const lock2Batch = writeBatch(p2);
  lock2Batch.update(doc(p2, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 2,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  lock2Batch.set(doc(p2, `active_routes/${routeId}/seat_locks/${passenger2Uid}`), {
    lockId: passenger2Uid,
    postingId: routeId,
    passengerUid: passenger2Uid,
    driverUid,
    seatsLocked: 1,
    agreedTotalFare: 95,
    negotiationId: neg2Id,
    queuePosition: 2,
    status: "LOCKED",
    lockedAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await assertSucceeds(lock2Batch.commit());

  const release1Batch = writeBatch(p1);
  release1Batch.update(doc(p1, `active_routes/${routeId}`), {
    availableSeats: 2,
    activeQueueCount: 1,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  release1Batch.update(doc(p1, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  });
  await assertSucceeds(release1Batch.commit());

  const relock1Batch = writeBatch(p1);
  relock1Batch.update(doc(p1, `active_routes/${routeId}`), {
    availableSeats: 1,
    activeQueueCount: 2,
    queueSequence: 3,
    updatedAt: serverTimestamp()
  });
  relock1Batch.set(doc(p1, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(90),
    seatsLocked: 1,
    queuePosition: 3
  });
  await assertSucceeds(relock1Batch.commit());
});

test("duplicate release, duplicate lock, rewound queueSequence, and wrong accounting fail", async () => {
  await seedRouteAndAgreement({ availableSeats: 1, activeQueueCount: 1, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const passenger = user(passengerUid, { phone_number: phone });

  // Rewound queueSequence on release fails
  const rewindBatch = writeBatch(passenger);
  rewindBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 3,
    activeQueueCount: 0,
    queueSequence: 0,
    updatedAt: serverTimestamp()
  });
  rewindBatch.update(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  });
  await assertFails(rewindBatch.commit());

  // Wrong availableSeats accounting on release fails
  const wrongSeatsBatch = writeBatch(passenger);
  wrongSeatsBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 2,
    activeQueueCount: 0,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  wrongSeatsBatch.update(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    driverAutoRegNumber: deleteField(),
    updatedAt: serverTimestamp()
  });
  await assertFails(wrongSeatsBatch.commit());

  // Duplicate lock on already LOCKED seat_locks doc fails
  const dupLockBatch = writeBatch(passenger);
  dupLockBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 0,
    activeQueueCount: 2,
    queueSequence: 2,
    updatedAt: serverTimestamp()
  });
  dupLockBatch.set(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    ...seatLockPayload(100),
    seatsLocked: 1,
    queuePosition: 2
  });
  await assertFails(dupLockBatch.commit());

  // Duplicate release when already RELEASED fails
  await seedRouteAndAgreement({ availableSeats: 3, activeQueueCount: 0, queueSequence: 1 });
  await seed(`active_routes/${routeId}/seat_locks/${passengerUid}`, {
    ...seatLockPayload(100),
    status: "RELEASED",
    lockedAt: new Date(),
    updatedAt: new Date()
  });
  const dupReleaseBatch = writeBatch(passenger);
  dupReleaseBatch.update(doc(passenger, `active_routes/${routeId}`), {
    availableSeats: 3,
    activeQueueCount: 0,
    queueSequence: 1,
    updatedAt: serverTimestamp()
  });
  dupReleaseBatch.update(doc(passenger, `active_routes/${routeId}/seat_locks/${passengerUid}`), {
    status: "RELEASED",
    updatedAt: serverTimestamp()
  });
  await assertFails(dupReleaseBatch.commit());
});

test("duplicate negotiation event sequence and unauthorized participant updates fail", async () => {
  await seedRouteAndAgreement();
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
      }
    ],
    status: "OPEN",
    agreedTotalFareInr: null,
    createdAt: new Date(),
    updatedAt: new Date()
  });
  const driver = user(driverUid, { phone_number: "+919876543211" });
  await assertFails(updateDoc(doc(driver, `fare_negotiations/${negotiationId}`), {
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
        sequence: 1,
        actor: "DRIVER",
        type: "DRIVER_ACCEPTED",
        totalFareInr: 100,
        createdAtEpochMs: 2000
      }
    ],
    status: "AGREED",
    agreedTotalFareInr: 100,
    updatedAt: serverTimestamp()
  }));

  const other = user("other_passenger", { phone_number: "+919876543215" });
  await assertFails(updateDoc(doc(other, `fare_negotiations/${negotiationId}`), {
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
        actor: "PASSENGER",
        type: "CANCELLED",
        totalFareInr: null,
        createdAtEpochMs: 2000
      }
    ],
    status: "CANCELLED",
    agreedTotalFareInr: null,
    updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(other, `active_routes/${routeId}`), {
    isActive: false,
    updatedAt: serverTimestamp()
  }));
});
