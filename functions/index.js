const { onCall, onRequest } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const {
  STRIPE_SECRET_KEY,
  STRIPE_WEBHOOK_SECRET,
  RAZORPAY_KEY_ID,
  RAZORPAY_KEY_SECRET,
  RAZORPAY_WEBHOOK_SECRET,
  SMM_PROVIDER_API_KEYS_JSON,
} = require("./src/config");
const { createSmmOrderHandler, cancelSmmOrderHandler } = require("./src/orders");
const {
  adminProcessDepositHandler,
  adminAdjustUserAccountHandler,
  adminCancelAndRefundOrderHandler,
  adminUpdateOrderStatusHandler,
  adminTestProviderConnectionHandler,
  adminSyncOwnerUidHandler,
} = require("./src/admin");
const {
  createPaymentCheckoutHandler,
  stripeWebhookHandler,
  razorpayWebhookHandler,
} = require("./src/payments");

setGlobalOptions({
  region: "us-central1",
  maxInstances: 10,
});

exports.createSmmOrder = onCall(
  { secrets: [SMM_PROVIDER_API_KEYS_JSON] },
  (request) => createSmmOrderHandler(request)
);

exports.cancelSmmOrder = onCall(
  {},
  (request) => cancelSmmOrderHandler(request)
);

exports.adminProcessDeposit = onCall(
  {},
  (request) => adminProcessDepositHandler(request)
);

exports.adminAdjustUserAccount = onCall(
  {},
  (request) => adminAdjustUserAccountHandler(request)
);

exports.adminCancelAndRefundOrder = onCall(
  {},
  (request) => adminCancelAndRefundOrderHandler(request)
);

exports.adminUpdateOrderStatus = onCall(
  {},
  (request) => adminUpdateOrderStatusHandler(request)
);

exports.adminTestProviderConnection = onCall(
  { secrets: [SMM_PROVIDER_API_KEYS_JSON] },
  (request) => adminTestProviderConnectionHandler(request)
);

exports.createPaymentCheckout = onCall(
  { secrets: [STRIPE_SECRET_KEY, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET] },
  (request) => createPaymentCheckoutHandler(request)
);

exports.stripeWebhook = onRequest(
  { secrets: [STRIPE_WEBHOOK_SECRET] },
  (req, res) => stripeWebhookHandler(req, res)
);

exports.razorpayWebhook = onRequest(
  { secrets: [RAZORPAY_WEBHOOK_SECRET] },
  (req, res) => razorpayWebhookHandler(req, res)
);

exports.adminSyncOwnerUid = onCall(
  {},
  (request) => adminSyncOwnerUidHandler(request)
);
