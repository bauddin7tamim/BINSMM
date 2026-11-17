const { HttpsError } = require("firebase-functions/v2/https");
const {
  FieldValue,
  getDb,
  resolveProviderApiKey,
  assertSignedIn,
  assertValidId,
  roundCurrency,
  randomId,
} = require("./config");

const IDEMPOTENCY_REGEX = /^[a-zA-Z0-9_-]{8,128}$/;

/**
 * Atomic server-side SMM order creation with idempotency protection,
 * authoritative pricing lookup, balance verification, and wallet deduction.
 */
async function createSmmOrderHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const fetchImpl = deps.fetchImpl || globalThis.fetch;
  const resolveKey = deps.resolveProviderApiKey || resolveProviderApiKey;

  const caller = assertSignedIn(request.auth);
  const data = request.data || {};

  const serviceId = assertValidId(String(data.serviceId || "").trim(), "serviceId");
  const targetLink = String(data.targetLink || "").trim();
  const quantity = Number(data.quantity);
  const idempotencyKey = String(data.idempotencyKey || "").trim();

  if (targetLink.length < 5 || targetLink.length > 500) {
    throw new HttpsError("invalid-argument", "Target link must be between 5 and 500 characters.");
  }
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 50000000) {
    throw new HttpsError("invalid-argument", "Quantity must be a valid positive integer.");
  }
  if (!IDEMPOTENCY_REGEX.test(idempotencyKey)) {
    throw new HttpsError(
      "invalid-argument",
      "A valid idempotencyKey (8-128 alphanumeric/hyphen/underscore chars) is required to prevent duplicate orders."
    );
  }

  const idemDocId = `${caller.uid}_${idempotencyKey}`;
  const idemRef = db.collection("order_idempotency").doc(idemDocId);
  const userRef = db.collection("users").doc(caller.uid);
  const serviceRef = db.collection("services").doc(serviceId);

  const txResult = await db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      const existingData = idemSnap.data() || {};
      return {
        duplicate: true,
        orderId: existingData.orderId,
        charge: existingData.charge || 0,
        status: existingData.status || "pending",
        providerId: existingData.providerId || "unconfigured",
        providerServiceId: existingData.providerServiceId || "",
      };
    }

    const [userSnap, serviceSnap] = await Promise.all([tx.get(userRef), tx.get(serviceRef)]);

    if (!userSnap.exists) {
      throw new HttpsError("failed-precondition", "User profile not found. Complete registration first.");
    }
    const user = userSnap.data() || {};
    if (user.accountStatus === "suspended") {
      throw new HttpsError("permission-denied", "Your account is suspended. Ordering is disabled.");
    }

    if (!serviceSnap.exists) {
      throw new HttpsError("not-found", "Selected service no longer exists.");
    }
    const service = serviceSnap.data() || {};
    if (!service.active) {
      throw new HttpsError("failed-precondition", "This service is currently disabled by Admin.");
    }
    if (
      service.integrationStatus !== "provider_linked" ||
      !service.providerId ||
      String(service.providerId).toLowerCase() === "unconfigured"
    ) {
      throw new HttpsError(
        "failed-precondition",
        "DEMO service cannot process real orders. Connect a live SMM Provider API in Admin first."
      );
    }

    const minQty = Number(service.minQuantity || 1);
    const maxQty = Number(service.maxQuantity || 50000000);
    if (quantity < minQty || quantity > maxQty) {
      throw new HttpsError("invalid-argument", `Quantity must be between ${minQty} and ${maxQty}.`);
    }

    const ratePer1000 = Number(service.ratePer1000 || 0);
    const charge = roundCurrency((quantity / 1000) * ratePer1000);
    const currentBalance = roundCurrency(user.balance || 0);

    if (charge > 0 && currentBalance < charge) {
      throw new HttpsError(
        "failed-precondition",
        `Insufficient balance ($${currentBalance.toFixed(2)} available, $${charge.toFixed(2)} required).`
      );
    }

    const orderId = randomId("ord_");
    const orderRef = db.collection("orders").doc(orderId);
    const newBalance = roundCurrency(Math.max(0, currentBalance - charge));

    const orderPayload = {
      userId: caller.uid,
      userEmail: String(user.email || caller.email).slice(0, 200),
      serviceId,
      serviceName: String(service.name || "SMM Service").slice(0, 160),
      category: String(service.category || "instagram"),
      targetLink: targetLink.slice(0, 500),
      quantity,
      charge,
      status: "pending",
      providerOrderId: "",
      integrationNote: `Atomic wallet debit completed. Provider ${service.providerId} dispatch queued.`,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };

    tx.set(orderRef, orderPayload);

    if (charge > 0) {
      const txId = randomId("tx_");
      tx.set(db.collection("transactions").doc(txId), {
        userId: caller.uid,
        userEmail: String(user.email || caller.email).slice(0, 200),
        type: "order_charge",
        amount: charge,
        gateway: "wallet",
        referenceNote: `Order #${orderId} (${String(service.name || "").slice(0, 80)})`,
        status: "completed",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    tx.update(userRef, {
      balance: newBalance,
      totalOrders: FieldValue.increment(1),
      pendingOrders: FieldValue.increment(1),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(idemRef, {
      orderId,
      userId: caller.uid,
      serviceId,
      charge,
      status: "pending",
      providerId: String(service.providerId),
      providerServiceId: String(service.providerServiceId || ""),
      createdAt: FieldValue.serverTimestamp(),
    });

    const notifId = randomId("ntf_", 14);
    tx.set(userRef.collection("notifications").doc(notifId), {
      title: `Order Created (${orderId})`,
      message: `${service.name} x${quantity} atomically charged $${charge.toFixed(2)}.`,
      type: "order",
      read: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return {
      duplicate: false,
      orderId,
      charge,
      newBalance,
      status: "pending",
      providerId: String(service.providerId),
      providerServiceId: String(service.providerServiceId || ""),
    };
  });

  if (txResult.duplicate) {
    return {
      orderId: txResult.orderId,
      charge: txResult.charge,
      status: txResult.status,
      duplicate: true,
    };
  }

  // Optional post-transaction server-side provider dispatch if server Secret Manager key is configured
  let finalStatus = txResult.status;
  try {
    const providerSnap = await db.collection("providers").doc(txResult.providerId).get();
    const provider = providerSnap.exists ? providerSnap.data() : null;
    const providerApiKey = resolveKey(txResult.providerId);

    if (
      provider &&
      provider.active &&
      provider.baseUrl &&
      /^https?:\/\//i.test(provider.baseUrl) &&
      providerApiKey &&
      txResult.providerServiceId &&
      typeof fetchImpl === "function"
    ) {
      const formBody = new URLSearchParams({
        key: providerApiKey,
        action: "add",
        service: txResult.providerServiceId,
        link: targetLink,
        quantity: String(quantity),
      });
      const resp = await fetchImpl(provider.baseUrl, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody.toString(),
      });
      const json = await resp.json().catch(() => ({}));
      if (resp.ok && json && json.order) {
        finalStatus = "processing";
        await db.collection("orders").doc(txResult.orderId).update({
          status: "processing",
          providerOrderId: String(json.order).slice(0, 128),
          integrationNote: `Dispatched to ${provider.name} via server Secret Manager key.`,
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        const errMsg = json?.error || `HTTP ${resp.status}`;
        await db.collection("orders").doc(txResult.orderId).update({
          integrationNote: `Provider dispatch deferred (${String(errMsg).slice(0, 180)}).`,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    } else {
      await db.collection("orders").doc(txResult.orderId).update({
        integrationNote: `Atomic debit completed. Configure server secret for provider '${txResult.providerId}' to auto-dispatch.`,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  } catch (dispatchErr) {
    await db
      .collection("orders")
      .doc(txResult.orderId)
      .update({
        integrationNote: `Provider dispatch error: ${String(dispatchErr.message || dispatchErr).slice(0, 180)}`,
        updatedAt: FieldValue.serverTimestamp(),
      })
      .catch(() => {});
  }

  return {
    orderId: txResult.orderId,
    charge: txResult.charge,
    newBalance: txResult.newBalance,
    status: finalStatus,
    duplicate: false,
  };
}

/**
 * Atomic user-initiated order cancellation (marks non-terminal order cancelled & updates counter;
 * refunds require owner-admin review via adminCancelAndRefundOrder).
 */
async function cancelSmmOrderHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const caller = assertSignedIn(request.auth);
  const orderId = assertValidId(String(request.data?.orderId || "").trim(), "orderId");

  const userRef = db.collection("users").doc(caller.uid);
  const orderRef = db.collection("orders").doc(orderId);

  await db.runTransaction(async (tx) => {
    const [userSnap, orderSnap] = await Promise.all([tx.get(userRef), tx.get(orderRef)]);
    if (!userSnap.exists || userSnap.data()?.accountStatus === "suspended") {
      throw new HttpsError("permission-denied", "Suspended or unregistered account cannot cancel orders.");
    }
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Order not found.");
    }
    const order = orderSnap.data() || {};
    if (order.userId !== caller.uid) {
      throw new HttpsError("permission-denied", "You can only cancel your own orders.");
    }
    if (order.status === "completed" || order.status === "cancelled") {
      throw new HttpsError("failed-precondition", `Order is already ${order.status}.`);
    }

    const currentPending = Number(userSnap.data()?.pendingOrders || 0);
    const wasPending = ["pending", "processing", "awaiting_provider_config"].includes(order.status);

    tx.update(orderRef, {
      status: "cancelled",
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (wasPending) {
      tx.update(userRef, {
        pendingOrders: Math.max(0, currentPending - 1),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    const notifId = randomId("ntf_", 14);
    tx.set(userRef.collection("notifications").doc(notifId), {
      title: "Order Cancelled",
      message: `Order ${orderId} has been cancelled.`,
      type: "order",
      read: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { orderId, status: "cancelled" };
}

module.exports = {
  createSmmOrderHandler,
  cancelSmmOrderHandler,
};
