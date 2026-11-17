const { HttpsError } = require("firebase-functions/v2/https");
const {
  FieldValue,
  getDb,
  resolveProviderApiKey,
  assertOwnerAdmin,
  assertValidId,
  roundCurrency,
  randomId,
  maskSecret,
} = require("./config");

function appendAuditLogInTx(tx, db, admin, { action, targetType, targetId, summary }) {
  const logId = randomId("log_");
  tx.set(db.collection("admin_audit_logs").doc(logId), {
    adminUid: admin.uid,
    adminEmail: admin.email.slice(0, 200),
    action: String(action).slice(0, 80),
    targetType,
    targetId: String(targetId || "global").slice(0, 128),
    summary: String(summary || action).slice(0, 500),
    createdAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Owner-only atomic deposit approval/rejection & wallet credit.
 */
async function adminProcessDepositHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const admin = await assertOwnerAdmin(request.auth, db);
  const data = request.data || {};

  const txId = assertValidId(String(data.transactionId || "").trim(), "transactionId");
  const approve = Boolean(data.approve);
  const reasonNote = String(data.reasonNote || "").trim();

  const txRef = db.collection("transactions").doc(txId);

  const result = await db.runTransaction(async (tx) => {
    const txSnap = await tx.get(txRef);
    if (!txSnap.exists) {
      throw new HttpsError("not-found", `Transaction ${txId} no longer exists.`);
    }
    const txData = txSnap.data() || {};
    if (txData.type !== "deposit_request") {
      throw new HttpsError("failed-precondition", "Only deposit requests can be approved or rejected.");
    }
    if (txData.status !== "pending_verification") {
      throw new HttpsError("failed-precondition", `Transaction ${txId} was already processed (${txData.status}).`);
    }

    const userId = assertValidId(String(txData.userId || ""), "userId");
    const userRef = db.collection("users").doc(userId);
    const userSnap = await tx.get(userRef);

    const amount = roundCurrency(txData.amount || 0);
    if (amount <= 0) {
      throw new HttpsError("failed-precondition", "Deposit amount must be positive.");
    }

    const nextStatus = approve ? "completed" : "rejected";
    const updatedNote =
      !approve && reasonNote
        ? `${txData.referenceNote || ""} | Rejected: ${reasonNote}`.slice(0, 300)
        : String(txData.referenceNote || "");

    tx.update(txRef, {
      status: nextStatus,
      referenceNote: updatedNote,
      updatedAt: FieldValue.serverTimestamp(),
    });

    let newBalance = null;
    if (approve && userSnap.exists) {
      const currentBal = roundCurrency(userSnap.data()?.balance || 0);
      newBalance = roundCurrency(currentBal + amount);
      tx.update(userRef, {
        balance: newBalance,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    if (userSnap.exists) {
      const notifId = randomId("ntf_", 14);
      tx.set(userRef.collection("notifications").doc(notifId), {
        title: approve ? "Deposit Approved" : "Deposit Rejected",
        message: approve
          ? `Your $${amount.toFixed(2)} deposit request (${txId}) was approved and credited to your wallet.`
          : `Your $${amount.toFixed(2)} deposit request (${txId}) was rejected. ${reasonNote}`.trim(),
        type: "wallet",
        read: false,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    appendAuditLogInTx(tx, db, admin, {
      action: approve ? "PAYMENT_APPROVED" : "PAYMENT_REJECTED",
      targetType: "transaction",
      targetId: txId,
      summary: approve
        ? `Approved $${amount.toFixed(2)} deposit for ${txData.userEmail || userId}.`
        : `Rejected $${amount.toFixed(2)} deposit for ${txData.userEmail || userId} (${reasonNote || "No reason"}).`,
    });

    return { transactionId: txId, status: nextStatus, amount, newBalance };
  });

  return result;
}

/**
 * Owner-only atomic user account status & balance adjustment.
 */
async function adminAdjustUserAccountHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const admin = await assertOwnerAdmin(request.auth, db);
  const data = request.data || {};

  const targetUserId = assertValidId(String(data.targetUserId || "").trim(), "targetUserId");
  const newStatus = String(data.newStatus || "").trim();
  const newBalance = roundCurrency(data.newBalance);
  const cleanNote = String(data.adjustmentNote || "").trim() || "Admin account update";

  if (!["active", "suspended"].includes(newStatus)) {
    throw new HttpsError("invalid-argument", "Account status must be 'active' or 'suspended'.");
  }
  if (!Number.isFinite(newBalance) || newBalance < 0 || newBalance > 1000000) {
    throw new HttpsError("invalid-argument", "Balance must be between 0 and 1,000,000.");
  }

  const userRef = db.collection("users").doc(targetUserId);

  return db.runTransaction(async (tx) => {
    const userSnap = await tx.get(userRef);
    if (!userSnap.exists) {
      throw new HttpsError("not-found", "Target user profile not found.");
    }
    const user = userSnap.data() || {};
    const oldBalance = roundCurrency(user.balance || 0);
    const oldStatus = String(user.accountStatus || "active");
    const delta = roundCurrency(newBalance - oldBalance);

    tx.update(userRef, {
      accountStatus: newStatus,
      balance: newBalance,
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (Math.abs(delta) > 0.001) {
      const adjTxId = randomId("adj_", 14);
      tx.set(db.collection("transactions").doc(adjTxId), {
        userId: targetUserId,
        userEmail: String(user.email || "").slice(0, 200),
        type: "admin_adjustment",
        amount: Math.abs(delta),
        gateway: "admin_panel",
        referenceNote: cleanNote.slice(0, 300),
        status: "completed",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    if (oldStatus !== newStatus) {
      const notifId = randomId("ntf_", 14);
      tx.set(userRef.collection("notifications").doc(notifId), {
        title: newStatus === "suspended" ? "Account Suspended" : "Account Activated",
        message:
          newStatus === "suspended"
            ? `Your BINSMM account has been suspended by an administrator. Note: ${cleanNote}`
            : "Your BINSMM account is now active.",
        type: "system",
        read: false,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    let actionCode = "USER_UPDATED";
    if (oldStatus !== newStatus && newStatus === "suspended") actionCode = "USER_SUSPENDED";
    else if (oldStatus !== newStatus && newStatus === "active") actionCode = "USER_ACTIVATED";
    else if (Math.abs(delta) > 0.001) actionCode = "USER_BALANCE_ADJUSTED";

    appendAuditLogInTx(tx, db, admin, {
      action: actionCode,
      targetType: "user",
      targetId: targetUserId,
      summary: `Updated ${user.email || targetUserId}: status=${newStatus}, balance=$${newBalance.toFixed(2)} (${cleanNote}).`,
    });

    return { userId: targetUserId, accountStatus: newStatus, balance: newBalance, delta };
  });
}

/**
 * Owner-only atomic order cancellation and optional wallet refund.
 */
async function adminCancelAndRefundOrderHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const admin = await assertOwnerAdmin(request.auth, db);
  const data = request.data || {};

  const orderId = assertValidId(String(data.orderId || "").trim(), "orderId");
  const refundToWallet = Boolean(data.refundToWallet);
  const cleanReason = String(data.reason || "").trim() || "Cancelled by Admin";

  const orderRef = db.collection("orders").doc(orderId);

  return db.runTransaction(async (tx) => {
    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", `Order ${orderId} no longer exists.`);
    }
    const order = orderSnap.data() || {};
    const currentStatus = String(order.status || "");
    if (currentStatus === "completed" || currentStatus === "cancelled") {
      throw new HttpsError(
        "failed-precondition",
        `Order ${orderId} is already ${currentStatus} and cannot be cancelled/refunded.`
      );
    }

    const userId = assertValidId(String(order.userId || ""), "userId");
    const userRef = db.collection("users").doc(userId);
    const userSnap = await tx.get(userRef);

    const orderCharge = roundCurrency(order.charge || 0);
    const refundAmount = refundToWallet && orderCharge > 0 ? orderCharge : 0;
    const noteText =
      refundAmount > 0
        ? `Cancelled & refunded $${refundAmount.toFixed(2)} (${cleanReason})`.slice(0, 300)
        : `Cancelled by Admin (${cleanReason})`.slice(0, 300);

    tx.update(orderRef, {
      status: "cancelled",
      integrationNote: noteText,
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (userSnap.exists) {
      const user = userSnap.data() || {};
      const existingBal = roundCurrency(user.balance || 0);
      const existingPending = Number(user.pendingOrders || 0);
      const wasPending = [
        "pending",
        "processing",
        "in_progress",
        "awaiting_provider_config",
        "not_started",
      ].includes(currentStatus);

      const userUpdates = {
        pendingOrders: wasPending ? Math.max(0, existingPending - 1) : existingPending,
        updatedAt: FieldValue.serverTimestamp(),
      };
      if (refundAmount > 0) {
        userUpdates.balance = roundCurrency(existingBal + refundAmount);
      }
      tx.update(userRef, userUpdates);

      const notifId = randomId("ntf_", 14);
      tx.set(userRef.collection("notifications").doc(notifId), {
        title: refundAmount > 0 ? "Order Cancelled & Refunded" : "Order Cancelled",
        message:
          refundAmount > 0
            ? `Order ${orderId} was cancelled and $${refundAmount.toFixed(2)} was refunded to your wallet.`
            : `Order ${orderId} was cancelled by Admin (${cleanReason}).`,
        type: "order",
        read: false,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    if (refundAmount > 0) {
      const refundTxId = randomId("rfd_", 14);
      tx.set(db.collection("transactions").doc(refundTxId), {
        userId,
        userEmail: String(order.userEmail || "").slice(0, 200),
        type: "refund",
        amount: refundAmount,
        gateway: "wallet",
        referenceNote: `Refund for Order #${orderId}: ${cleanReason}`.slice(0, 300),
        status: "completed",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    appendAuditLogInTx(tx, db, admin, {
      action: refundAmount > 0 ? "ORDER_CANCELLED_REFUNDED" : "ORDER_CANCELLED",
      targetType: "order",
      targetId: orderId,
      summary:
        refundAmount > 0
          ? `Cancelled order ${orderId} (${order.userEmail}) and refunded $${refundAmount.toFixed(2)} (${cleanReason}).`
          : `Cancelled order ${orderId} (${order.userEmail}) without wallet refund (${cleanReason}).`,
    });

    return { orderId, status: "cancelled", refundedAmount: refundAmount };
  });
}

/**
 * Owner-only atomic order status update + user order counter reconciliation.
 */
async function adminUpdateOrderStatusHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const admin = await assertOwnerAdmin(request.auth, db);
  const data = request.data || {};

  const orderId = assertValidId(String(data.orderId || "").trim(), "orderId");
  const newStatus = String(data.newStatus || "").trim();
  const providerOrderId = String(data.providerOrderId || "").trim().slice(0, 128);

  const allowedStatuses = [
    "awaiting_provider_config",
    "pending",
    "processing",
    "in_progress",
    "completed",
    "partial",
    "cancelled",
    "failed",
    "not_started",
    "not_received",
  ];
  if (!allowedStatuses.includes(newStatus)) {
    throw new HttpsError("invalid-argument", "Invalid order status.");
  }

  const orderRef = db.collection("orders").doc(orderId);

  return db.runTransaction(async (tx) => {
    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", `Order ${orderId} not found.`);
    }
    const order = orderSnap.data() || {};
    const oldStatus = String(order.status || "");
    const userId = assertValidId(String(order.userId || ""), "userId");
    const userRef = db.collection("users").doc(userId);
    const userSnap = await tx.get(userRef);

    tx.update(orderRef, {
      status: newStatus,
      providerOrderId,
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (userSnap.exists) {
      const user = userSnap.data() || {};
      const pending = Number(user.pendingOrders || 0);
      const completed = Number(user.completedOrders || 0);
      const pendingStates = [
        "pending",
        "processing",
        "in_progress",
        "awaiting_provider_config",
        "not_started",
      ];
      const wasPending = pendingStates.includes(oldStatus);
      const isNowPending = pendingStates.includes(newStatus);

      let nextPending = pending;
      if (wasPending && !isNowPending) nextPending = Math.max(0, pending - 1);
      else if (!wasPending && isNowPending) nextPending = pending + 1;

      let nextCompleted = completed;
      if (oldStatus !== "completed" && newStatus === "completed") nextCompleted = completed + 1;
      else if (oldStatus === "completed" && newStatus !== "completed") nextCompleted = Math.max(0, completed - 1);

      tx.update(userRef, {
        pendingOrders: nextPending,
        completedOrders: nextCompleted,
        updatedAt: FieldValue.serverTimestamp(),
      });

      if (oldStatus !== newStatus) {
        const notifId = randomId("ntf_", 14);
        tx.set(userRef.collection("notifications").doc(notifId), {
          title: "Order Status Updated",
          message: `Order ${orderId} (${order.serviceName}) is now ${newStatus.replace(/_/g, " ")}.`,
          type: "order",
          read: false,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    }

    appendAuditLogInTx(tx, db, admin, {
      action: "ORDER_STATUS_UPDATED",
      targetType: "order",
      targetId: orderId,
      summary: `Updated order ${orderId} (${order.userEmail}) from ${oldStatus} to ${newStatus}.`,
    });

    return { orderId, status: newStatus };
  });
}

/**
 * Owner-only server-side SMM provider connection save & balance check.
 * Raw API keys are resolved from server Secret Manager (or transiently tested in memory) and NEVER saved to Firestore.
 */
async function adminTestProviderConnectionHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const fetchImpl = deps.fetchImpl || globalThis.fetch;
  const resolveKey = deps.resolveProviderApiKey || resolveProviderApiKey;
  const admin = await assertOwnerAdmin(request.auth, db);
  const data = request.data || {};

  const providerId = data.providerId ? assertValidId(String(data.providerId).trim(), "providerId") : randomId("prv_", 10);
  const name = String(data.name || "").trim().slice(0, 120);
  const baseUrl = String(data.baseUrl || "").trim().slice(0, 300);
  const transientKey = String(data.transientApiKey || "").trim();
  const active = Boolean(data.active);
  const runLiveTest = Boolean(data.runLiveTest);

  if (name.length < 1) {
    throw new HttpsError("invalid-argument", "Provider name is required.");
  }

  const docRef = db.collection("providers").doc(providerId);
  const existingSnap = await docRef.get();
  const existing = existingSnap.exists ? existingSnap.data() : null;

  const serverSecretKey = resolveKey(providerId);
  const keyToUse = transientKey || serverSecretKey || "";
  const apiKeyConfigured = Boolean(serverSecretKey || transientKey || existing?.apiKeyConfigured);
  const apiKeyMasked = transientKey
    ? maskSecret(transientKey)
    : serverSecretKey
    ? maskSecret(serverSecretKey)
    : String(existing?.apiKeyMasked || "");

  let lastBalance = String(existing?.lastBalance || "Not tested");
  let lastSyncStatus = "Saved (configure SMM_PROVIDER_API_KEYS_JSON in Secret Manager for live server dispatch)";

  if (runLiveTest) {
    if (!/^https?:\/\//i.test(baseUrl)) {
      throw new HttpsError("invalid-argument", "Provider URL must start with https:// or http://");
    }
    if (!keyToUse) {
      lastBalance = "Secret missing";
      lastSyncStatus = "No server secret (SMM_PROVIDER_API_KEYS_JSON) or transient test key supplied";
    } else if (typeof fetchImpl === "function") {
      try {
        const formBody = new URLSearchParams({ key: keyToUse, action: "balance" });
        const resp = await fetchImpl(baseUrl, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: formBody.toString(),
        });
        const json = await resp.json().catch(() => ({}));
        if (resp.ok && json && !json.error && json.balance !== undefined) {
          lastBalance = `${json.balance} ${json.currency || "USD"}`.slice(0, 64);
          lastSyncStatus = serverSecretKey
            ? "Connected via server Secret Manager & SMM v2 API"
            : "Connected via SMM v2 API (transient key tested; store key in Secret Manager for automated dispatch)";
        } else {
          lastBalance = "Connection failed";
          lastSyncStatus = String(json?.error || `HTTP ${resp.status}`).slice(0, 200);
        }
      } catch (err) {
        lastBalance = "Connection failed";
        lastSyncStatus = String(err.message || err).slice(0, 200);
      }
    }
  } else if (serverSecretKey && /^https?:\/\//i.test(baseUrl)) {
    lastSyncStatus = existing?.lastSyncStatus?.startsWith("Connected")
      ? existing.lastSyncStatus
      : "Server Secret Manager key present (ready for test)";
  } else if (!baseUrl || !apiKeyConfigured) {
    lastSyncStatus = "Not configured";
  }

  const isConnected = lastSyncStatus.startsWith("Connected");
  const batch = db.batch();
  const payload = {
    name,
    baseUrl,
    apiKeyMasked,
    apiKeyConfigured,
    active: active && isConnected,
    lastBalance,
    lastSyncStatus,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (!existing) {
    payload.createdAt = FieldValue.serverTimestamp();
  }
  batch.set(docRef, payload, { merge: true });

  const logId = randomId("log_");
  batch.set(db.collection("admin_audit_logs").doc(logId), {
    adminUid: admin.uid,
    adminEmail: admin.email.slice(0, 200),
    action: runLiveTest ? "PROVIDER_TESTED" : "PROVIDER_SAVED",
    targetType: "provider",
    targetId: providerId,
    summary: `Provider '${name}': ${lastSyncStatus} (balance: ${lastBalance}).`.slice(0, 500),
    createdAt: FieldValue.serverTimestamp(),
  });

  await batch.commit();

  return {
    providerId,
    name,
    apiKeyMasked,
    apiKeyConfigured,
    active: active && isConnected,
    lastBalance,
    lastSyncStatus,
  };
}

/**
 * Owner-only pin of verified owner UID in /admins/{uid} so Admin privileges survive
 * verified Admin email changes without ever letting ordinary users elevate privileges.
 */
async function adminSyncOwnerUidHandler(request, deps = {}) {
  const db = deps.db || getDb();
  const admin = await assertOwnerAdmin(request.auth, db);

  const adminRef = db.collection("admins").doc(admin.uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(adminRef);
    const payload = {
      uid: admin.uid,
      email: admin.email.slice(0, 200),
      isVerifiedOwnerPin: true,
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (!snap.exists) {
      payload.createdAt = FieldValue.serverTimestamp();
    }
    tx.set(adminRef, payload, { merge: true });

    appendAuditLogInTx(tx, db, admin, {
      action: "OWNER_UID_PINNED",
      targetType: "admin_security",
      targetId: admin.uid,
      summary: `Pinned verified owner UID for continuous Admin authorization (${admin.email}).`,
    });
  });

  return { uid: admin.uid, email: admin.email, pinned: true };
}

module.exports = {
  adminProcessDepositHandler,
  adminAdjustUserAccountHandler,
  adminCancelAndRefundOrderHandler,
  adminUpdateOrderStatusHandler,
  adminTestProviderConnectionHandler,
  adminSyncOwnerUidHandler,
};
