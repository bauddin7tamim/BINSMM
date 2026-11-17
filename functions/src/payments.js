const crypto = require("crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const {
  STRIPE_SECRET_KEY,
  STRIPE_WEBHOOK_SECRET,
  RAZORPAY_KEY_ID,
  RAZORPAY_KEY_SECRET,
  RAZORPAY_WEBHOOK_SECRET,
  FieldValue,
  getDb,
  readSecretValue,
  assertSignedIn,
  roundCurrency,
  randomId,
} = require("./config");

function verifyStripeSignature(rawBodyBuffer, sigHeader, webhookSecret, toleranceSeconds = 300) {
  if (!sigHeader || !webhookSecret || !rawBodyBuffer) return false;
  const parts = String(sigHeader).split(",").map((s) => s.trim());
  const timestampPart = parts.find((p) => p.startsWith("t="));
  const v1Parts = parts.filter((p) => p.startsWith("v1=")).map((p) => p.slice(3));
  if (!timestampPart || v1Parts.length === 0) return false;

  const ts = parseInt(timestampPart.slice(2), 10);
  if (!Number.isFinite(ts)) return false;
  const nowSec = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSec - ts) > toleranceSeconds) return false;

  const signedPayload = `${ts}.${Buffer.isBuffer(rawBodyBuffer) ? rawBodyBuffer.toString("utf8") : String(rawBodyBuffer)}`;
  const expectedSig = crypto.createHmac("sha256", webhookSecret).update(signedPayload).digest("hex");
  const expectedBuf = Buffer.from(expectedSig, "utf8");

  return v1Parts.some((candidate) => {
    const candidateBuf = Buffer.from(candidate, "utf8");
    return candidateBuf.length === expectedBuf.length && crypto.timingSafeEqual(candidateBuf, expectedBuf);
  });
}

function verifyRazorpaySignature(rawBodyBuffer, sigHeader, webhookSecret) {
  if (!sigHeader || !webhookSecret || !rawBodyBuffer) return false;
  const bodyStr = Buffer.isBuffer(rawBodyBuffer) ? rawBodyBuffer.toString("utf8") : String(rawBodyBuffer);
  const expectedSig = crypto.createHmac("sha256", webhookSecret).update(bodyStr).digest("hex");
  const expectedBuf = Buffer.from(expectedSig, "utf8");
  const actualBuf = Buffer.from(String(sigHeader).trim(), "utf8");
  return actualBuf.length === expectedBuf.length && crypto.timingSafeEqual(actualBuf, expectedBuf);
}

/**
 * Credits a user's wallet atomically from a verified webhook event,
 * using /webhook_events/{eventKey} for strict replay/duplicate protection.
 */
async function creditWalletFromVerifiedWebhook(db, { eventKey, txDocId, userId, amount, gateway, referenceNote }) {
  const safeAmount = roundCurrency(amount);
  if (safeAmount <= 0) {
    throw new Error("Webhook credit amount must be positive.");
  }

  const eventRef = db.collection("webhook_events").doc(eventKey);
  const userRef = db.collection("users").doc(userId);
  const txRef = db.collection("transactions").doc(txDocId);

  return db.runTransaction(async (tx) => {
    const eventSnap = await tx.get(eventRef);
    if (eventSnap.exists) {
      return { alreadyProcessed: true };
    }

    const userSnap = await tx.get(userRef);
    if (!userSnap.exists) {
      throw new Error(`Target user ${userId} does not exist.`);
    }
    const user = userSnap.data() || {};
    const existingTxSnap = await tx.get(txRef);
    if (existingTxSnap.exists && existingTxSnap.data()?.status === "completed") {
      tx.set(eventRef, {
        eventKey,
        txDocId,
        userId,
        gateway,
        amount: safeAmount,
        processedAt: FieldValue.serverTimestamp(),
      });
      return { alreadyProcessed: true };
    }

    const newBalance = roundCurrency((user.balance || 0) + safeAmount);
    tx.update(userRef, {
      balance: newBalance,
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(
      txRef,
      {
        userId,
        userEmail: String(user.email || `${userId}@binsmm.app`).slice(0, 200),
        type: "deposit_request",
        amount: safeAmount,
        gateway,
        referenceNote: String(referenceNote).slice(0, 300),
        status: "completed",
        createdAt: existingTxSnap.exists ? existingTxSnap.data().createdAt : FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    tx.set(eventRef, {
      eventKey,
      txDocId,
      userId,
      gateway,
      amount: safeAmount,
      processedAt: FieldValue.serverTimestamp(),
    });

    const notifId = randomId("ntf_", 14);
    tx.set(userRef.collection("notifications").doc(notifId), {
      title: "Deposit Credited",
      message: `$${safeAmount.toFixed(2)} verified via ${gateway} webhook and credited to your wallet.`,
      type: "wallet",
      read: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return { alreadyProcessed: false, newBalance, amount: safeAmount };
  });
}

/**
 * Callable endpoint to create a server-side Stripe PaymentIntent or Razorpay Order
 * ONLY when real gateway secrets exist in Cloud Secret Manager.
 */
async function createPaymentCheckoutHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const fetchImpl = deps.fetchImpl || globalThis.fetch;
  const caller = assertSignedIn(request.auth);
  const data = request.data || {};

  const gateway = String(data.gateway || "").trim().toLowerCase();
  const amount = roundCurrency(data.amount);

  if (amount <= 0 || amount > 100000) {
    throw new HttpsError("invalid-argument", "Amount must be between $0.01 and $100,000.");
  }

  const userSnap = await db.collection("users").doc(caller.uid).get();
  if (!userSnap.exists || userSnap.data()?.accountStatus === "suspended") {
    throw new HttpsError("permission-denied", "Active BINSMM account required.");
  }

  if (gateway === "stripe") {
    const stripeSecret = deps.stripeSecretKey || readSecretValue(STRIPE_SECRET_KEY, "STRIPE_SECRET_KEY");
    if (!stripeSecret) {
      throw new HttpsError(
        "failed-precondition",
        "Stripe payment gateway is not configured on the backend (STRIPE_SECRET_KEY missing in Secret Manager)."
      );
    }

    const amountCents = Math.round(amount * 100);
    const body = new URLSearchParams({
      amount: String(amountCents),
      currency: "usd",
      "metadata[userId]": caller.uid,
      "metadata[app]": "binsmm",
    });

    const resp = await fetchImpl("https://api.stripe.com/v1/payment_intents", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${stripeSecret}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: body.toString(),
    });
    const json = await resp.json().catch(() => ({}));
    if (!resp.ok || !json.id || !json.client_secret) {
      throw new HttpsError("internal", json?.error?.message || "Failed to create Stripe PaymentIntent.");
    }

    await db.collection("transactions").doc(`stripe_${json.id}`).set({
      userId: caller.uid,
      userEmail: caller.email,
      type: "deposit_request",
      amount,
      gateway: "stripe",
      referenceNote: `Stripe PaymentIntent ${json.id} (awaiting webhook)`,
      status: "pending_verification",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return {
      gateway: "stripe",
      paymentIntentId: json.id,
      clientSecret: json.client_secret,
      amount,
    };
  }

  if (gateway === "razorpay") {
    const keyId = deps.razorpayKeyId || readSecretValue(RAZORPAY_KEY_ID, "RAZORPAY_KEY_ID");
    const keySecret = deps.razorpayKeySecret || readSecretValue(RAZORPAY_KEY_SECRET, "RAZORPAY_KEY_SECRET");
    if (!keyId || !keySecret) {
      throw new HttpsError(
        "failed-precondition",
        "Razorpay gateway is not configured on the backend (RAZORPAY_KEY_ID/SECRET missing in Secret Manager)."
      );
    }

    const amountPaise = Math.round(amount * 100);
    const authHeader = "Basic " + Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const resp = await fetchImpl("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount: amountPaise,
        currency: "INR",
        receipt: randomId("rcpt_", 12),
        notes: { userId: caller.uid, app: "binsmm" },
      }),
    });
    const json = await resp.json().catch(() => ({}));
    if (!resp.ok || !json.id) {
      throw new HttpsError("internal", json?.error?.description || "Failed to create Razorpay order.");
    }

    await db.collection("transactions").doc(`rzp_ord_${json.id}`).set({
      userId: caller.uid,
      userEmail: caller.email,
      type: "deposit_request",
      amount,
      gateway: "razorpay",
      referenceNote: `Razorpay Order ${json.id} (awaiting webhook)`,
      status: "pending_verification",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return {
      gateway: "razorpay",
      orderId: json.id,
      keyId,
      amount,
    };
  }

  throw new HttpsError("invalid-argument", "Unsupported payment gateway.");
}

/**
 * Stripe Webhook HTTP handler with HMAC-SHA256 signature verification & atomic replay protection.
 */
async function stripeWebhookHandler(req, res, deps = {}) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  const db = deps.db || getDb();
  const webhookSecret = deps.webhookSecret || readSecretValue(STRIPE_WEBHOOK_SECRET, "STRIPE_WEBHOOK_SECRET");
  if (!webhookSecret) {
    res.status(503).json({ error: "Stripe webhook secret not configured on server." });
    return;
  }

  const rawBody = req.rawBody || Buffer.from(typeof req.body === "string" ? req.body : JSON.stringify(req.body || {}));
  const sigHeader = req.get ? req.get("stripe-signature") : req.headers?.["stripe-signature"];

  if (!verifyStripeSignature(rawBody, sigHeader, webhookSecret)) {
    res.status(401).json({ error: "Invalid Stripe webhook signature." });
    return;
  }

  const event = typeof req.body === "object" && req.body !== null ? req.body : JSON.parse(rawBody.toString("utf8"));
  if (event.type !== "payment_intent.succeeded") {
    res.status(200).json({ received: true, ignored: event.type });
    return;
  }

  const pi = event.data?.object || {};
  const userId = pi.metadata?.userId;
  const amount = Number(pi.amount_received || pi.amount || 0) / 100;

  if (!userId || amount <= 0 || !pi.id || !event.id) {
    res.status(400).json({ error: "Malformed Stripe payment_intent.succeeded payload." });
    return;
  }

  try {
    const result = await creditWalletFromVerifiedWebhook(db, {
      eventKey: `stripe_${event.id}`,
      txDocId: `stripe_${pi.id}`,
      userId,
      amount,
      gateway: "stripe",
      referenceNote: `Stripe verified PaymentIntent ${pi.id}`,
    });
    res.status(200).json({ received: true, ...result });
  } catch (err) {
    res.status(400).json({ error: String(err.message || err) });
  }
}

/**
 * Razorpay Webhook HTTP handler with HMAC-SHA256 signature verification & atomic replay protection.
 */
async function razorpayWebhookHandler(req, res, deps = {}) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  const db = deps.db || getDb();
  const webhookSecret = deps.webhookSecret || readSecretValue(RAZORPAY_WEBHOOK_SECRET, "RAZORPAY_WEBHOOK_SECRET");
  if (!webhookSecret) {
    res.status(503).json({ error: "Razorpay webhook secret not configured on server." });
    return;
  }

  const rawBody = req.rawBody || Buffer.from(typeof req.body === "string" ? req.body : JSON.stringify(req.body || {}));
  const sigHeader = req.get ? req.get("x-razorpay-signature") : req.headers?.["x-razorpay-signature"];

  if (!verifyRazorpaySignature(rawBody, sigHeader, webhookSecret)) {
    res.status(401).json({ error: "Invalid Razorpay webhook signature." });
    return;
  }

  const payload = typeof req.body === "object" && req.body !== null ? req.body : JSON.parse(rawBody.toString("utf8"));
  if (payload.event !== "payment.captured") {
    res.status(200).json({ received: true, ignored: payload.event });
    return;
  }

  const payment = payload.payload?.payment?.entity || {};
  const userId = payment.notes?.userId;
  const amount = Number(payment.amount || 0) / 100;
  const eventId = (req.get ? req.get("x-razorpay-event-id") : req.headers?.["x-razorpay-event-id"]) || payment.id;

  if (!userId || amount <= 0 || !payment.id || !eventId) {
    res.status(400).json({ error: "Malformed Razorpay payment.captured payload." });
    return;
  }

  try {
    const result = await creditWalletFromVerifiedWebhook(db, {
      eventKey: `razorpay_${eventId}`,
      txDocId: `rzp_${payment.id}`,
      userId,
      amount,
      gateway: "razorpay",
      referenceNote: `Razorpay verified payment ${payment.id}`,
    });
    res.status(200).json({ received: true, ...result });
  } catch (err) {
    res.status(400).json({ error: String(err.message || err) });
  }
}

module.exports = {
  verifyStripeSignature,
  verifyRazorpaySignature,
  createPaymentCheckoutHandler,
  stripeWebhookHandler,
  razorpayWebhookHandler,
};
