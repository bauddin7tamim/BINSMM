const crypto = require("crypto");
const { getApps, initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const OWNER_ADMIN_EMAIL = "bauddinsk0@gmail.com";
const DATABASE_ID = process.env.FIRESTORE_DATABASE_ID || "ai-studio-android-binsmm-c1f92791-a2e3-4d6c-8bca-49b938dea4fa";
const VALID_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;

// Server-side secrets (stored exclusively in Google Cloud Secret Manager / server env, never in Android APK or Firestore)
const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
const STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");
const RAZORPAY_KEY_ID = defineSecret("RAZORPAY_KEY_ID");
const RAZORPAY_KEY_SECRET = defineSecret("RAZORPAY_KEY_SECRET");
const RAZORPAY_WEBHOOK_SECRET = defineSecret("RAZORPAY_WEBHOOK_SECRET");
const SMM_PROVIDER_API_KEYS_JSON = defineSecret("SMM_PROVIDER_API_KEYS_JSON");

function getDb() {
  const app = getApps().length ? getApps()[0] : initializeApp();
  return getFirestore(app, DATABASE_ID);
}

function readSecretValue(secretParam, envName) {
  try {
    const val = secretParam && typeof secretParam.value === "function" ? secretParam.value() : "";
    if (val && String(val).trim().length > 0) {
      return String(val).trim();
    }
  } catch (_) {
    // Fallback to process.env for local emulator / unit test contexts where Cloud Secret Manager param isn't bound
  }
  const envVal = process.env[envName];
  return envVal && String(envVal).trim().length > 0 ? String(envVal).trim() : null;
}

function resolveProviderApiKey(providerId) {
  if (!providerId || providerId === "unconfigured") return null;
  const rawJson = readSecretValue(SMM_PROVIDER_API_KEYS_JSON, "SMM_PROVIDER_API_KEYS_JSON");
  if (rawJson) {
    try {
      const parsed = JSON.parse(rawJson);
      if (parsed && typeof parsed[providerId] === "string" && parsed[providerId].trim().length > 0) {
        return parsed[providerId].trim();
      }
    } catch (_) {
      // Ignore malformed JSON secret
    }
  }
  const directEnv = process.env[`SMM_PROVIDER_KEY_${providerId}`];
  return directEnv && directEnv.trim().length > 0 ? directEnv.trim() : null;
}

function assertSignedIn(auth) {
  if (!auth || !auth.uid) {
    throw new HttpsError("unauthenticated", "Google Sign-In authentication required.");
  }
  return {
    uid: auth.uid,
    email: String(auth.token?.email || `${auth.uid}@binsmm.app`).slice(0, 200),
    emailVerified: Boolean(auth.token?.email_verified),
  };
}

async function assertOwnerAdmin(auth, db = null) {
  const caller = assertSignedIn(auth);
  const emailMatch =
    caller.email.toLowerCase() === OWNER_ADMIN_EMAIL && caller.emailVerified === true;
  if (emailMatch) {
    return caller;
  }
  if (db && caller.emailVerified === true) {
    try {
      const pinSnap = await db.collection("admins").doc(caller.uid).get();
      if (pinSnap.exists && pinSnap.data()?.isVerifiedOwnerPin === true) {
        return caller;
      }
    } catch (_) {
      // Deny by default if lookup fails
    }
  }
  throw new HttpsError(
    "permission-denied",
    "Owner administrator verification is required."
  );
}

function assertValidId(value, label = "ID") {
  if (typeof value !== "string" || !VALID_ID_REGEX.test(value)) {
    throw new HttpsError("invalid-argument", `Invalid ${label}.`);
  }
  return value;
}

function roundCurrency(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return 0;
  return Math.round((num + Number.EPSILON) * 10000) / 10000;
}

function randomId(prefix, len = 16) {
  return `${prefix}${crypto.randomUUID().replace(/-/g, "").slice(0, len)}`;
}

function maskSecret(raw) {
  const s = String(raw || "").trim();
  if (!s) return "";
  if (s.length > 6) return `${s.slice(0, 3)}***${s.slice(-3)}`;
  return "***configured***";
}

module.exports = {
  OWNER_ADMIN_EMAIL,
  DATABASE_ID,
  VALID_ID_REGEX,
  STRIPE_SECRET_KEY,
  STRIPE_WEBHOOK_SECRET,
  RAZORPAY_KEY_ID,
  RAZORPAY_KEY_SECRET,
  RAZORPAY_WEBHOOK_SECRET,
  SMM_PROVIDER_API_KEYS_JSON,
  FieldValue,
  getDb,
  readSecretValue,
  resolveProviderApiKey,
  assertSignedIn,
  assertOwnerAdmin,
  assertValidId,
  roundCurrency,
  randomId,
  maskSecret,
};
