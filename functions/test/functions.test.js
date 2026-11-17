const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { test, before, after } = require("node:test");
const { FieldValue } = require("firebase-admin/firestore");

process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "demo-no-project";

const { OWNER_ADMIN_EMAIL, getDb } = require("../src/config");
const { createSmmOrderHandler, cancelSmmOrderHandler } = require("../src/orders");
const {
  adminProcessDepositHandler,
  adminAdjustUserAccountHandler,
  adminCancelAndRefundOrderHandler,
  adminUpdateOrderStatusHandler,
  adminTestProviderConnectionHandler,
  adminSyncOwnerUidHandler,
} = require("../src/admin");
const {
  createPaymentCheckoutHandler,
  stripeWebhookHandler,
  razorpayWebhookHandler,
} = require("../src/payments");

let db;

function ownerAuth(uid = "owner_uid_1", verified = true) {
  return {
    uid,
    token: {
      email: OWNER_ADMIN_EMAIL,
      email_verified: verified,
    },
  };
}

function userAuth(uid, email = `${uid}@binsmm.test`) {
  return {
    uid,
    token: {
      email,
      email_verified: true,
    },
  };
}

function makeMockRes() {
  const res = {
    statusCode: 200,
    body: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(obj) {
      this.body = obj;
      return this;
    },
  };
  return res;
}

async function seedUser(uid, balance = 0, status = "active") {
  await db.collection("users").doc(uid).set({
    userId: uid,
    displayName: `User ${uid}`,
    email: `${uid}@binsmm.test`,
    role: "user",
    accountStatus: status,
    balance,
    totalOrders: 0,
    pendingOrders: 0,
    completedOrders: 0,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

async function seedService(serviceId, { ratePer1000 = 2.0, integrationStatus = "provider_linked", providerId = "prv_live_1", active = true } = {}) {
  await db.collection("services").doc(serviceId).set({
    name: `Service ${serviceId}`,
    category: "instagram",
    description: "Test service",
    ratePer1000,
    providerCostPer1000: ratePer1000 * 0.6,
    minQuantity: 100,
    maxQuantity: 100000,
    averageTime: "1 hour",
    refillSupported: true,
    active,
    providerId,
    providerServiceId: "101",
    integrationStatus,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

before(() => {
  db = getDb();
});

after(async () => {
  if (db && typeof db.terminate === "function") {
    await db.terminate();
  }
});

test("createSmmOrderHandler enforces auth, idempotencyKey, DEMO block, suspended block, and insufficient balance", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const uid = `u_guard_${suffix}`;
  const suspUid = `u_susp_${suffix}`;
  const demoSvcId = `svc_demo_${suffix}`;
  const liveSvcId = `svc_live_${suffix}`;

  await seedUser(uid, 1.0, "active");
  await seedUser(suspUid, 100.0, "suspended");
  await seedService(demoSvcId, { integrationStatus: "template_unconnected", providerId: "unconfigured", ratePer1000: 2.0 });
  await seedService(liveSvcId, { integrationStatus: "provider_linked", providerId: `prv_${suffix}`, ratePer1000: 5.0 });

  await assert.rejects(
    () => createSmmOrderHandler({ auth: null, data: { serviceId: liveSvcId, targetLink: "https://instagram.com/a", quantity: 1000, idempotencyKey: "key_12345678" } }, { db }),
    (err) => err.code === "unauthenticated"
  );

  await assert.rejects(
    () => createSmmOrderHandler({ auth: userAuth(uid), data: { serviceId: liveSvcId, targetLink: "https://instagram.com/a", quantity: 1000, idempotencyKey: "short" } }, { db }),
    (err) => err.code === "invalid-argument"
  );

  await assert.rejects(
    () => createSmmOrderHandler({ auth: userAuth(uid), data: { serviceId: demoSvcId, targetLink: "https://instagram.com/a", quantity: 1000, idempotencyKey: `idem_demo_${suffix}` } }, { db }),
    (err) => err.code === "failed-precondition" && /DEMO/i.test(err.message)
  );

  await assert.rejects(
    () => createSmmOrderHandler({ auth: userAuth(suspUid), data: { serviceId: liveSvcId, targetLink: "https://instagram.com/a", quantity: 1000, idempotencyKey: `idem_susp_${suffix}` } }, { db }),
    (err) => err.code === "permission-denied"
  );

  await assert.rejects(
    () => createSmmOrderHandler({ auth: userAuth(uid), data: { serviceId: liveSvcId, targetLink: "https://instagram.com/a", quantity: 1000, idempotencyKey: `idem_low_${suffix}` } }, { db }),
    (err) => err.code === "failed-precondition" && /Insufficient balance/i.test(err.message)
  );
});

test("createSmmOrderHandler executes atomic debit, prevents duplicate idempotency replay, prevents concurrent overdraft, and dispatches provider API via server secret", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const uid = `u_atomic_${suffix}`;
  const svcId = `svc_atomic_${suffix}`;
  const prvId = `prv_atomic_${suffix}`;

  // User starts with $5.00; service costs $4.00 per 1000 -> only ONE 1000-qty order can succeed!
  await seedUser(uid, 5.0, "active");
  await seedService(svcId, { ratePer1000: 4.0, integrationStatus: "provider_linked", providerId: prvId });
  await db.collection("providers").doc(prvId).set({
    name: "Test Provider",
    baseUrl: "https://mock-smm.example/api/v2",
    apiKeyMasked: "srv***999",
    apiKeyConfigured: true,
    active: true,
    lastBalance: "50.00 USD",
    lastSyncStatus: "Connected",
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  let dispatchCallCount = 0;
  let capturedBody = "";
  const mockFetch = async (_url, opts) => {
    dispatchCallCount++;
    capturedBody = opts.body;
    return {
      ok: true,
      status: 200,
      json: async () => ({ order: 998877 }),
    };
  };

  const idemKey = `order_req_1_${suffix}`;
  const firstRes = await createSmmOrderHandler(
    {
      auth: userAuth(uid),
      data: {
        serviceId: svcId,
        targetLink: "https://instagram.com/binsmm_official",
        quantity: 1000,
        idempotencyKey: idemKey,
      },
    },
    {
      db,
      fetchImpl: mockFetch,
      resolveProviderApiKey: (id) => (id === prvId ? "SECRET_PROVIDER_KEY_XYZ" : null),
    }
  );

  assert.equal(firstRes.duplicate, false);
  assert.equal(firstRes.charge, 4.0);
  assert.equal(firstRes.newBalance, 1.0);
  assert.equal(firstRes.status, "processing");
  assert.equal(dispatchCallCount, 1);
  assert.match(capturedBody, /key=SECRET_PROVIDER_KEY_XYZ/);
  assert.match(capturedBody, /action=add/);

  // Verify Firestore order, transaction, and user balance
  const orderSnap = await db.collection("orders").doc(firstRes.orderId).get();
  assert.equal(orderSnap.exists, true);
  assert.equal(orderSnap.data().providerOrderId, "998877");
  assert.equal(orderSnap.data().status, "processing");

  const userAfterFirst = (await db.collection("users").doc(uid).get()).data();
  assert.equal(userAfterFirst.balance, 1.0);
  assert.equal(userAfterFirst.totalOrders, 1);
  assert.equal(userAfterFirst.pendingOrders, 1);

  // Replay with SAME idempotencyKey MUST return duplicate=true and MUST NOT debit again or call provider again
  const replayRes = await createSmmOrderHandler(
    {
      auth: userAuth(uid),
      data: {
        serviceId: svcId,
        targetLink: "https://instagram.com/binsmm_official",
        quantity: 1000,
        idempotencyKey: idemKey,
      },
    },
    { db, fetchImpl: mockFetch }
  );
  assert.equal(replayRes.duplicate, true);
  assert.equal(replayRes.orderId, firstRes.orderId);
  assert.equal(dispatchCallCount, 1);

  // Concurrent race test: user has $5.00 reset and fires 2 parallel orders of $4.00 with DIFFERENT keys
  const raceUid = `u_race_${suffix}`;
  await seedUser(raceUid, 5.0, "active");
  const outcomes = await Promise.allSettled([
    createSmmOrderHandler(
      { auth: userAuth(raceUid), data: { serviceId: svcId, targetLink: "https://instagram.com/race1", quantity: 1000, idempotencyKey: `race_a_${suffix}` } },
      { db, resolveProviderApiKey: () => null }
    ),
    createSmmOrderHandler(
      { auth: userAuth(raceUid), data: { serviceId: svcId, targetLink: "https://instagram.com/race2", quantity: 1000, idempotencyKey: `race_b_${suffix}` } },
      { db, resolveProviderApiKey: () => null }
    ),
  ]);

  const fulfilled = outcomes.filter((o) => o.status === "fulfilled");
  const rejected = outcomes.filter((o) => o.status === "rejected");
  assert.equal(fulfilled.length, 1, "Only one concurrent order should succeed with $5.00 balance");
  assert.equal(rejected.length, 1, "Second concurrent order must fail due to atomic balance check");
  assert.equal(rejected[0].reason.code, "failed-precondition");

  const raceUserAfter = (await db.collection("users").doc(raceUid).get()).data();
  assert.equal(raceUserAfter.balance, 1.0);
  assert.equal(raceUserAfter.totalOrders, 1);
});

test("cancelSmmOrderHandler cancels user own pending order and blocks unauthorized/terminal cancellations", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const uid = `u_cancel_${suffix}`;
  const otherUid = `u_other_${suffix}`;
  const svcId = `svc_cancel_${suffix}`;

  await seedUser(uid, 10.0, "active");
  await seedUser(otherUid, 10.0, "active");
  await seedService(svcId, { ratePer1000: 2.0, integrationStatus: "provider_linked", providerId: "prv_none" });

  const order = await createSmmOrderHandler(
    { auth: userAuth(uid), data: { serviceId: svcId, targetLink: "https://instagram.com/cancelme", quantity: 1000, idempotencyKey: `idem_c_${suffix}` } },
    { db, resolveProviderApiKey: () => null }
  );

  // Other user cannot cancel
  await assert.rejects(
    () => cancelSmmOrderHandler({ auth: userAuth(otherUid), data: { orderId: order.orderId } }, { db }),
    (err) => err.code === "permission-denied"
  );

  // Owner cancels own pending order
  const cancelRes = await cancelSmmOrderHandler({ auth: userAuth(uid), data: { orderId: order.orderId } }, { db });
  assert.equal(cancelRes.status, "cancelled");

  const userAfter = (await db.collection("users").doc(uid).get()).data();
  assert.equal(userAfter.pendingOrders, 0);

  // Cannot cancel again
  await assert.rejects(
    () => cancelSmmOrderHandler({ auth: userAuth(uid), data: { orderId: order.orderId } }, { db }),
    (err) => err.code === "failed-precondition"
  );
});

test("Owner-only Admin Cloud Functions enforce verified bauddinsk0@gmail.com and atomically handle deposits, refunds, adjustments, order statuses, and provider test", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const uid = `u_adm_${suffix}`;
  const txId = `dep_${suffix}`;
  const svcId = `svc_adm_${suffix}`;
  const prvId = `prv_adm_${suffix}`;

  await seedUser(uid, 0.0, "active");
  await seedService(svcId, { ratePer1000: 3.0, integrationStatus: "provider_linked", providerId: prvId });
  await db.collection("transactions").doc(txId).set({
    userId: uid,
    userEmail: `${uid}@binsmm.test`,
    type: "deposit_request",
    amount: 25.0,
    gateway: "manual_request",
    referenceNote: "UTR-9911",
    status: "pending_verification",
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  // Non-owner and unverified owner must be denied
  await assert.rejects(
    () => adminProcessDepositHandler({ auth: userAuth(uid), data: { transactionId: txId, approve: true } }, { db }),
    (err) => err.code === "permission-denied"
  );
  await assert.rejects(
    () => adminProcessDepositHandler({ auth: ownerAuth("spoof_uid", false), data: { transactionId: txId, approve: true } }, { db }),
    (err) => err.code === "permission-denied"
  );

  // Verified owner approves deposit atomically
  const approveRes = await adminProcessDepositHandler(
    { auth: ownerAuth(), data: { transactionId: txId, approve: true } },
    { db }
  );
  assert.equal(approveRes.status, "completed");
  assert.equal(approveRes.newBalance, 25.0);

  // Replay approval prevented
  await assert.rejects(
    () => adminProcessDepositHandler({ auth: ownerAuth(), data: { transactionId: txId, approve: true } }, { db }),
    (err) => err.code === "failed-precondition"
  );

  // Create order ($3.00 -> balance $22.00) then admin cancels & refunds ($25.00)
  const ordRes = await createSmmOrderHandler(
    { auth: userAuth(uid), data: { serviceId: svcId, targetLink: "https://instagram.com/adm", quantity: 1000, idempotencyKey: `idem_adm_${suffix}` } },
    { db, resolveProviderApiKey: () => null }
  );
  assert.equal(ordRes.newBalance, 22.0);

  const refundRes = await adminCancelAndRefundOrderHandler(
    { auth: ownerAuth(), data: { orderId: ordRes.orderId, refundToWallet: true, reason: "Provider offline" } },
    { db }
  );
  assert.equal(refundRes.refundedAmount, 3.0);
  assert.equal((await db.collection("users").doc(uid).get()).data().balance, 25.0);

  // Double-refund prevented
  await assert.rejects(
    () => adminCancelAndRefundOrderHandler({ auth: ownerAuth(), data: { orderId: ordRes.orderId, refundToWallet: true, reason: "Retry" } }, { db }),
    (err) => err.code === "failed-precondition"
  );

  // Create another order and test adminUpdateOrderStatus -> completed
  const ord2 = await createSmmOrderHandler(
    { auth: userAuth(uid), data: { serviceId: svcId, targetLink: "https://instagram.com/adm2", quantity: 1000, idempotencyKey: `idem_adm2_${suffix}` } },
    { db, resolveProviderApiKey: () => null }
  );
  await adminUpdateOrderStatusHandler(
    { auth: ownerAuth(), data: { orderId: ord2.orderId, newStatus: "completed", providerOrderId: "EXT-555" } },
    { db }
  );
  const userAfterComp = (await db.collection("users").doc(uid).get()).data();
  assert.equal(userAfterComp.completedOrders, 1);
  assert.equal(userAfterComp.pendingOrders, 0);

  // Admin adjusts user account status + balance
  const adjRes = await adminAdjustUserAccountHandler(
    { auth: ownerAuth(), data: { targetUserId: uid, newStatus: "suspended", newBalance: 10.0, adjustmentNote: "Manual audit" } },
    { db }
  );
  assert.equal(adjRes.accountStatus, "suspended");
  assert.equal(adjRes.balance, 10.0);

  // Admin tests provider connection server-side without saving raw key in Firestore
  const prvRes = await adminTestProviderConnectionHandler(
    {
      auth: ownerAuth(),
      data: {
        providerId: prvId,
        name: "Live SMM Panel",
        baseUrl: "https://panel.example/api/v2",
        transientApiKey: "my_super_secret_key_99",
        active: true,
        runLiveTest: true,
      },
    },
    {
      db,
      resolveProviderApiKey: () => null,
      fetchImpl: async () => ({
        ok: true,
        status: 200,
        json: async () => ({ balance: "142.50", currency: "USD" }),
      }),
    }
  );
  assert.equal(prvRes.active, true);
  assert.equal(prvRes.lastBalance, "142.50 USD");
  assert.equal(prvRes.apiKeyMasked, "my_***_99");

  const storedPrv = (await db.collection("providers").doc(prvId).get()).data();
  assert.equal(storedPrv.apiKeyMasked, "my_***_99");
  assert.equal(storedPrv.apiKey, undefined, "Raw API key must never be stored in Firestore");
});

test("Stripe & Razorpay webhooks verify HMAC-SHA256 signatures, credit wallet atomically, and prevent replay attacks", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const uid = `u_pay_${suffix}`;
  await seedUser(uid, 5.0, "active");

  // Unconfigured checkout returns failed-precondition honestly
  await assert.rejects(
    () => createPaymentCheckoutHandler({ auth: userAuth(uid), data: { gateway: "stripe", amount: 25.0 } }, { db }),
    (err) => err.code === "failed-precondition"
  );

  // Stripe webhook: missing secret -> 503
  const missingRes = makeMockRes();
  await stripeWebhookHandler({ method: "POST", headers: {}, body: {} }, missingRes, { db });
  assert.equal(missingRes.statusCode, 503);

  // Stripe webhook: invalid signature -> 401
  const stripeSecret = "whsec_test_binsmm_secret";
  const stripePayloadObj = {
    id: `evt_stripe_${suffix}`,
    type: "payment_intent.succeeded",
    data: {
      object: {
        id: `pi_${suffix}`,
        amount_received: 2500,
        metadata: { userId: uid },
      },
    },
  };
  const stripeRaw = Buffer.from(JSON.stringify(stripePayloadObj), "utf8");
  const badRes = makeMockRes();
  await stripeWebhookHandler(
    { method: "POST", headers: { "stripe-signature": "t=12345,v1=deadbeef" }, rawBody: stripeRaw, body: stripePayloadObj },
    badRes,
    { db, webhookSecret: stripeSecret }
  );
  assert.equal(badRes.statusCode, 401);

  // Stripe webhook: valid signature -> 200 and credits $25.00 (total $30.00)
  const ts = Math.floor(Date.now() / 1000);
  const stripeSig = crypto.createHmac("sha256", stripeSecret).update(`${ts}.${stripeRaw.toString("utf8")}`).digest("hex");
  const okRes = makeMockRes();
  await stripeWebhookHandler(
    { method: "POST", headers: { "stripe-signature": `t=${ts},v1=${stripeSig}` }, rawBody: stripeRaw, body: stripePayloadObj },
    okRes,
    { db, webhookSecret: stripeSecret }
  );
  assert.equal(okRes.statusCode, 200);
  assert.equal(okRes.body.alreadyProcessed, false);
  assert.equal(okRes.body.newBalance, 30.0);

  // Replay of same Stripe event -> alreadyProcessed: true, balance remains $30.00
  const replayRes = makeMockRes();
  await stripeWebhookHandler(
    { method: "POST", headers: { "stripe-signature": `t=${ts},v1=${stripeSig}` }, rawBody: stripeRaw, body: stripePayloadObj },
    replayRes,
    { db, webhookSecret: stripeSecret }
  );
  assert.equal(replayRes.statusCode, 200);
  assert.equal(replayRes.body.alreadyProcessed, true);
  assert.equal((await db.collection("users").doc(uid).get()).data().balance, 30.0);

  // Razorpay webhook: valid HMAC-SHA256 credits wallet and blocks replay
  const rzpSecret = "rzp_whsec_test_secret";
  const rzpPayloadObj = {
    event: "payment.captured",
    payload: {
      payment: {
        entity: {
          id: `pay_${suffix}`,
          amount: 1500,
          notes: { userId: uid },
        },
      },
    },
  };
  const rzpRaw = Buffer.from(JSON.stringify(rzpPayloadObj), "utf8");
  const rzpSig = crypto.createHmac("sha256", rzpSecret).update(rzpRaw.toString("utf8")).digest("hex");

  const rzpBad = makeMockRes();
  await razorpayWebhookHandler(
    { method: "POST", headers: { "x-razorpay-signature": "tampered" }, rawBody: rzpRaw, body: rzpPayloadObj },
    rzpBad,
    { db, webhookSecret: rzpSecret }
  );
  assert.equal(rzpBad.statusCode, 401);

  const rzpOk = makeMockRes();
  await razorpayWebhookHandler(
    { method: "POST", headers: { "x-razorpay-signature": rzpSig, "x-razorpay-event-id": `ev_rzp_${suffix}` }, rawBody: rzpRaw, body: rzpPayloadObj },
    rzpOk,
    { db, webhookSecret: rzpSecret }
  );
  assert.equal(rzpOk.statusCode, 200);
  assert.equal(rzpOk.body.newBalance, 45.0);

  const rzpReplay = makeMockRes();
  await razorpayWebhookHandler(
    { method: "POST", headers: { "x-razorpay-signature": rzpSig, "x-razorpay-event-id": `ev_rzp_${suffix}` }, rawBody: rzpRaw, body: rzpPayloadObj },
    rzpReplay,
    { db, webhookSecret: rzpSecret }
  );
  assert.equal(rzpReplay.statusCode, 200);
  assert.equal(rzpReplay.body.alreadyProcessed, true);
  assert.equal((await db.collection("users").doc(uid).get()).data().balance, 45.0);
});

test("adminSyncOwnerUidHandler pins verified owner UID so Admin privileges survive verified email update and supports extended order statuses", async () => {
  const suffix = crypto.randomUUID().slice(0, 8);
  const pinnedUid = `owner_pin_${suffix}`;
  const nonOwnerUid = `non_owner_${suffix}`;
  const uid = `u_ext_${suffix}`;
  const svcId = `svc_ext_${suffix}`;

  await seedUser(uid, 20.0, "active");
  await seedService(svcId, { ratePer1000: 2.0, integrationStatus: "provider_linked", providerId: "prv_none" });

  // Regular user cannot call adminSyncOwnerUidHandler
  await assert.rejects(
    () => adminSyncOwnerUidHandler({ auth: userAuth(nonOwnerUid), data: {} }, { db }),
    (err) => err.code === "permission-denied"
  );

  // Verified owner pins their UID
  const pinRes = await adminSyncOwnerUidHandler(
    { auth: ownerAuth(pinnedUid, true), data: {} },
    { db }
  );
  assert.equal(pinRes.pinned, true);
  assert.equal(pinRes.uid, pinnedUid);

  const pinDoc = (await db.collection("admins").doc(pinnedUid).get()).data();
  assert.equal(pinDoc.isVerifiedOwnerPin, true);

  // Even after changing verified Admin email, the pinned owner UID remains authorized for Admin functions
  const order = await createSmmOrderHandler(
    { auth: userAuth(uid), data: { serviceId: svcId, targetLink: "https://instagram.com/ext", quantity: 1000, idempotencyKey: `idem_ext_${suffix}` } },
    { db, resolveProviderApiKey: () => null }
  );

  const updated = await adminUpdateOrderStatusHandler(
    {
      auth: { uid: pinnedUid, token: { email: `renamed-owner-${suffix}@binsmm.app`, email_verified: true } },
      data: { orderId: order.orderId, newStatus: "partial", providerOrderId: "EXT-PARTIAL" },
    },
    { db }
  );
  assert.equal(updated.status, "partial");

  // Unpinned non-owner stays blocked even if trying a different email
  await assert.rejects(
    () =>
      adminUpdateOrderStatusHandler(
        {
          auth: { uid: nonOwnerUid, token: { email: `renamed-owner-${suffix}@binsmm.app`, email_verified: true } },
          data: { orderId: order.orderId, newStatus: "failed" },
        },
        { db }
      ),
    (err) => err.code === "permission-denied"
  );
});

