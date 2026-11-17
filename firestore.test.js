const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const OWNER_UID = "owner_uid_789";
const OWNER_EMAIL = "bauddinsk0@gmail.com";
const OTHER_ADMIN_ATTEMPT_UID = "other_admin_attempt";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
}

function validUserPayload(uid, email, accountStatus = "active") {
  return {
    userId: uid,
    displayName: "Alice Creator",
    email: email,
    photoUrl: null,
    whatsappHandle: "+15550100",
    role: "user",
    accountStatus,
    balance: 0,
    totalOrders: 0,
    pendingOrders: 0,
    completedOrders: 0,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validServicePayload(integrationStatus = "template_unconnected") {
  return {
    name: "Instagram High-Retention Followers",
    category: "instagram",
    description: "Template service awaiting provider API connection.",
    ratePer1000: 1.95,
    providerCostPer1000: 1.10,
    minQuantity: 100,
    maxQuantity: 50000,
    averageTime: "1-3 hours",
    refillSupported: true,
    active: true,
    providerId: integrationStatus === "provider_linked" ? "prv_live_1" : "unconfigured",
    providerServiceId: "101",
    integrationStatus,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

test("Unauthenticated user cannot read users, services, orders, or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("services").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("transactions").get());
});

test("User can create own profile with zero balance and user role, but cannot self-assign admin or initial balance", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice@example.com"))
  );

  await assertFails(
    aliceDb.collection("users").doc("alice_admin_attempt").set({
      ...validUserPayload("alice_admin_attempt", "alice@example.com"),
      role: "admin",
    })
  );

  await assertFails(
    aliceDb.collection("users").doc("alice_money_attempt").set({
      ...validUserPayload("alice_money_attempt", "alice@example.com"),
      balance: 999.0,
    })
  );
});

test("PII Isolation & Shadow Update: Bob cannot read Alice profile or inject ghost fields", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice@example.com"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isVerified: true,
      updatedAt: nowTs(),
    })
  );

  // Client cannot directly update balance, order counters, or ghost fields (backend Cloud Functions only)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      balance: 500,
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      balance: -10,
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalOrders: 1,
      updatedAt: nowTs(),
    })
  );

  // Even admin client SDK cannot directly mutate user balance outside Cloud Functions
  const adminDb = ownerDb();
  await assertFails(
    adminDb.collection("users").doc(ALICE_UID).update({
      balance: 100,
      updatedAt: nowTs(),
    })
  );

  // Active user CAN update allowed profile contact fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      whatsappHandle: "+15550999",
      updatedAt: nowTs(),
    })
  );
});

test("Owner-Only Admin Enforcement: unverified owner email and non-owner accounts with /admins docs are strictly denied", async () => {
  // Even if an attacker or legacy doc exists in /admins or users.role='admin', rules MUST deny admin rights to non-owners
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(OTHER_ADMIN_ATTEMPT_UID).set({
      uid: OTHER_ADMIN_ATTEMPT_UID,
      email: "intruder@example.com",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await ctx.firestore().collection("users").doc(OTHER_ADMIN_ATTEMPT_UID).set({
      ...validUserPayload(OTHER_ADMIN_ATTEMPT_UID, "intruder@example.com"),
      role: "admin",
    });
  });

  const spoofOwnerDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofOwnerDb.collection("services").doc("svc_spoof").set(validServicePayload()));
  await assertFails(spoofOwnerDb.collection("users").get());

  const otherDb = testEnv.authenticatedContext(OTHER_ADMIN_ATTEMPT_UID, {
    email: "intruder@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(otherDb.collection("services").doc("svc_other").set(validServicePayload()));
  await assertFails(otherDb.collection("users").get());
  await assertFails(otherDb.collection("providers").get());
  await assertFails(otherDb.collection("admin_audit_logs").get());

  // Even the verified owner cannot create/delegate additional /admins documents
  const verifiedOwnerDb = ownerDb();
  await assertFails(
    verifiedOwnerDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertFails(verifiedOwnerDb.collection("admins").doc(OTHER_ADMIN_ATTEMPT_UID).delete());

  // Verified owner succeeds on admin actions
  await assertSucceeds(verifiedOwnerDb.collection("services").doc("svc_ig_1").set(validServicePayload()));
});

test("Admin can create/update services and providers; regular user cannot", async () => {
  const adminDb = ownerDb();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  await assertSucceeds(adminDb.collection("services").doc("svc_ig_1").set(validServicePayload()));
  await assertFails(aliceDb.collection("services").doc("svc_ig_2").set(validServicePayload()));
  await assertSucceeds(aliceDb.collection("services").get());
  await assertFails(aliceDb.collection("providers").get());
});

test("Orders: client SDK creation, updates, and deletes are strictly denied (Cloud Functions Admin SDK only)", async () => {
  const baseOrder = {
    userId: ALICE_UID,
    userEmail: "alice@example.com",
    serviceId: "svc_ig_live",
    serviceName: "Instagram High-Retention Followers",
    category: "instagram",
    targetLink: "https://instagram.com/binsmm",
    quantity: 1000,
    charge: 1.95,
    status: "pending",
    providerOrderId: "",
    integrationNote: "Queued for provider",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice@example.com", "active"));
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob@example.com", "suspended"));
    await ctx.firestore().collection("services").doc("svc_ig_live").set(validServicePayload("provider_linked"));
    // Seed order as backend Admin SDK would
    await ctx.firestore().collection("orders").doc("order_1").set(baseOrder);
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const adminDb = ownerDb();

  // Neither regular users nor admin client SDK can create, update, or delete orders directly
  await assertFails(aliceDb.collection("orders").doc("order_client").set(baseOrder));
  await assertFails(adminDb.collection("orders").doc("order_admin_client").set(baseOrder));
  await assertFails(aliceDb.collection("orders").doc("order_1").update({ status: "cancelled", updatedAt: nowTs() }));
  await assertFails(adminDb.collection("orders").doc("order_1").update({ status: "completed", updatedAt: nowTs() }));
  await assertFails(adminDb.collection("orders").doc("order_1").delete());

  // Query & read isolation remain intact
  await assertSucceeds(aliceDb.collection("orders").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(adminDb.collection("orders").get());
  await assertFails(aliceDb.collection("orders").get());
  await assertFails(bobDb.collection("orders").doc("order_1").get());
});

test("Transactions: active user can submit pending deposit_request; direct order_charge, client approvals, idempotency, and webhook_events are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice@example.com", "active"));
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob@example.com", "suspended"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const adminDb = ownerDb();

  // Cannot fake completed deposit or client order_charge
  await assertFails(
    aliceDb.collection("transactions").doc("tx_fake").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      type: "deposit_request",
      amount: 50.0,
      gateway: "manual_request",
      referenceNote: "Fake credit",
      status: "completed",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    aliceDb.collection("transactions").doc("tx_charge_client").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      type: "order_charge",
      amount: 1.95,
      gateway: "wallet",
      referenceNote: "Client debit",
      status: "completed",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Suspended user cannot create deposit request
  await assertFails(
    bobDb.collection("transactions").doc("tx_suspended").set({
      userId: BOB_UID,
      userEmail: "bob@example.com",
      type: "deposit_request",
      amount: 25.0,
      gateway: "manual_request",
      referenceNote: "UTR-SUSP",
      status: "pending_verification",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Valid pending deposit request succeeds
  await assertSucceeds(
    aliceDb.collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      type: "deposit_request",
      amount: 25.0,
      gateway: "manual_request",
      referenceNote: "UTR-889911",
      status: "pending_verification",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Clients (including admin client SDK) cannot approve/update transactions directly; must use adminProcessDeposit Cloud Function
  await assertFails(
    aliceDb.collection("transactions").doc("tx_1").update({
      status: "completed",
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    adminDb.collection("transactions").doc("tx_1").update({
      status: "completed",
      updatedAt: nowTs(),
    })
  );

  // Backend-only idempotency & webhook replay collections deny all client access
  await assertFails(aliceDb.collection("order_idempotency").doc("k1").set({ orderId: "o1" }));
  await assertFails(adminDb.collection("order_idempotency").get());
  await assertFails(aliceDb.collection("webhook_events").doc("e1").set({ eventKey: "e1" }));
  await assertFails(adminDb.collection("webhook_events").get());
});

test("Admin Audit Logs: only verified owner admin can create/read, updates & deletes strictly forbidden, regular user denied", async () => {
  const adminDb = ownerDb();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  const validLog = {
    adminUid: OWNER_UID,
    adminEmail: OWNER_EMAIL,
    action: "PAYMENT_APPROVED",
    targetType: "transaction",
    targetId: "tx_1",
    summary: "Approved $25.00 manual deposit",
    createdAt: nowTs(),
  };

  // Regular user cannot create or read audit logs
  await assertFails(
    aliceDb.collection("admin_audit_logs").doc("log_alice").set({
      ...validLog,
      adminUid: ALICE_UID,
      adminEmail: "alice@example.com",
    })
  );
  await assertFails(aliceDb.collection("admin_audit_logs").get());

  // Regular user cannot elevate themselves via /admins
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Admin cannot forge another admin's UID in audit log
  await assertFails(
    adminDb.collection("admin_audit_logs").doc("log_spoof").set({
      ...validLog,
      adminUid: "other_admin_uid",
    })
  );

  // Owner admin can create and read audit log
  await assertSucceeds(adminDb.collection("admin_audit_logs").doc("log_1").set(validLog));
  await assertSucceeds(adminDb.collection("admin_audit_logs").get());

  // Audit logs are immutable: even owner admin cannot update or delete
  await assertFails(
    adminDb.collection("admin_audit_logs").doc("log_1").update({
      summary: "Tampered summary",
    })
  );
  await assertFails(adminDb.collection("admin_audit_logs").doc("log_1").delete());
});

test("Knowledge Base: customers have read-only access, unauthenticated denied, only verified Admin can CRUD", async () => {
  const adminDb = ownerDb();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const kbEntry = {
    title: "How to place an order",
    category: "order",
    question: "How do I place a new order?",
    answer: "Open New Order, select platform and service, paste your link, enter quantity, and confirm.",
    keywords: "order,place,new order",
    active: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };

  await assertFails(aliceDb.collection("knowledge_base").doc("kb_1").set(kbEntry));
  await assertFails(unauthDb.collection("knowledge_base").get());

  await assertSucceeds(adminDb.collection("knowledge_base").doc("kb_1").set(kbEntry));
  await assertSucceeds(aliceDb.collection("knowledge_base").get());
  await assertFails(aliceDb.collection("knowledge_base").doc("kb_1").update({ title: "Hacked", updatedAt: nowTs() }));
  await assertFails(aliceDb.collection("knowledge_base").doc("kb_1").delete());

  await assertSucceeds(adminDb.collection("knowledge_base").doc("kb_1").update({ active: false, updatedAt: nowTs() }));
  await assertSucceeds(adminDb.collection("knowledge_base").doc("kb_1").delete());
});

test("Support Tickets & Chats: strict per-user isolation, closed tickets block customer replies, Admin manages tickets", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice@example.com", "active"));
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob@example.com", "suspended"));
  });

  const adminDb = ownerDb();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  // Per-user AI chat history isolation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("support_chats").doc("msg_1").set({
      role: "user",
      text: "Mera order pending kyu hai?",
      detectedOrderId: "",
      suggestWhatsApp: false,
      escalatedProblem: "",
      createdAt: nowTs(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("support_chats").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("support_chats").doc("msg_spoof").set({
      role: "user",
      text: "Spoof",
      createdAt: nowTs(),
    })
  );

  // Active customer creates Support Ticket & first message
  const ticketDoc = {
    ticketNumber: "TKT-100001",
    userId: ALICE_UID,
    userEmail: "alice@example.com",
    userName: "Alice Creator",
    category: "order",
    orderId: "ord_123456",
    subject: "Order stuck in pending",
    description: "My Instagram order has been pending for 2 hours.",
    status: "open",
    lastMessagePreview: "My Instagram order has been pending for 2 hours.",
    lastReplyBy: "customer",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
  await assertSucceeds(aliceDb.collection("support_tickets").doc("tkt_1").set(ticketDoc));
  await assertSucceeds(
    aliceDb.collection("support_tickets").doc("tkt_1").collection("messages").doc("tmsg_1").set({
      senderUid: ALICE_UID,
      senderRole: "customer",
      senderName: "Alice Creator",
      message: "Please check this order.",
      createdAt: nowTs(),
    })
  );

  // Other user (Bob) cannot read Alice's ticket or list all tickets; suspended Bob cannot create tickets
  await assertFails(bobDb.collection("support_tickets").doc("tkt_1").get());
  await assertFails(bobDb.collection("support_tickets").get());
  await assertFails(
    bobDb.collection("support_tickets").doc("tkt_bob").set({
      ...ticketDoc,
      userId: BOB_UID,
      userEmail: "bob@example.com",
    })
  );

  // Admin can read all tickets, reply, and close the ticket
  await assertSucceeds(adminDb.collection("support_tickets").get());
  await assertSucceeds(
    adminDb.collection("support_tickets").doc("tkt_1").collection("messages").doc("tmsg_2").set({
      senderUid: OWNER_UID,
      senderRole: "admin",
      senderName: "BINSMM Admin",
      message: "Checking with provider now.",
      createdAt: nowTs(),
    })
  );
  await assertSucceeds(
    adminDb.collection("support_tickets").doc("tkt_1").update({
      status: "closed",
      lastReplyBy: "admin",
      updatedAt: nowTs(),
    })
  );

  // Once closed, customer cannot add further replies to the closed ticket
  await assertFails(
    aliceDb.collection("support_tickets").doc("tkt_1").collection("messages").doc("tmsg_3").set({
      senderUid: ALICE_UID,
      senderRole: "customer",
      senderName: "Alice Creator",
      message: "Replying after close",
      createdAt: nowTs(),
    })
  );
});

test("Verified Owner UID Pin preserves Admin rules access when verified Admin email changes, while rejecting forged docs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc("pinned_owner_uid").set({
      uid: "pinned_owner_uid",
      email: "newadmin@binsmm.app",
      isVerifiedOwnerPin: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await ctx.firestore().collection("admins").doc(OTHER_ADMIN_ATTEMPT_UID).set({
      uid: OTHER_ADMIN_ATTEMPT_UID,
      email: "intruder@example.com",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const pinnedOwnerNewEmailDb = testEnv.authenticatedContext("pinned_owner_uid", {
    email: "newadmin@binsmm.app",
    email_verified: true,
  }).firestore();
  await assertSucceeds(pinnedOwnerNewEmailDb.collection("knowledge_base").doc("kb_pin").set({
    title: "Pin check",
    category: "general",
    question: "Pin active?",
    answer: "Yes.",
    keywords: "pin",
    active: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  const forgedDb = testEnv.authenticatedContext(OTHER_ADMIN_ATTEMPT_UID, {
    email: "intruder@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(forgedDb.collection("knowledge_base").doc("kb_forged").set({
    title: "Forged",
    category: "general",
    question: "Q",
    answer: "A",
    keywords: "",
    active: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));
});

