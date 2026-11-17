package com.example.data.repository

import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SupportCategory

object SupportDefaults {
    const val ADMIN_WHATSAPP_NUMBER = "+91 6372505724"
    const val ADMIN_WHATSAPP_DIGITS = "916372505724"

    fun defaultKnowledgeBaseEntries(): List<KnowledgeBaseEntry> = listOf(
        // A. Account Support
        KnowledgeBaseEntry(
            id = "kb_acc_register_login",
            title = "Account Registration & Google Sign-In",
            category = SupportCategory.ACCOUNT.key,
            question = "How do I register or log in to BINSMM? What if I have a login problem?",
            answer = "BINSMM uses secure Google Sign-In via Android Credential Manager. Tap 'Continue with Google' on the sign-in screen and select a Google account added to your device. If sign-in fails, check your internet connection, ensure Google Play Services is updated, and confirm a Google account is logged into your Android device settings.",
            keywords = "register signup login account google signin error device account",
            active = true
        ),
        KnowledgeBaseEntry(
            id = "kb_acc_password_profile",
            title = "Password Reset & Profile Information",
            category = SupportCategory.ACCOUNT.key,
            question = "How do I reset my password or update my profile and WhatsApp contact?",
            answer = "Because regular BINSMM accounts sign in through Google, password resets are managed in your Google Account security settings (myaccount.google.com). To update your Display Name or WhatsApp Contact Handle inside BINSMM, go to the Profile tab and tap the Edit (pencil) icon on your profile card.",
            keywords = "password reset forgot profile display name whatsapp contact update",
            active = true
        ),

        // B. Order Support
        KnowledgeBaseEntry(
            id = "kb_ord_place_and_id",
            title = "How to Place a New Order & Find Order ID",
            category = SupportCategory.ORDER.key,
            question = "How do I place a new order and where can I find my Order ID?",
            answer = "1) Open the 'New Order' tab, pick a platform (Instagram, YouTube, Facebook, or Telegram), select an active live service, enter your public target link/username and quantity, and submit. Note: Services labeled DEMO cannot process real orders until Admin connects a live SMM provider API.\n2) To find your Order ID (format: ord_...), open the 'Orders' tab; the ID is displayed on every order card and inside Order Details.",
            keywords = "new order place order how to find order id ord_ target link",
            active = true
        ),
        KnowledgeBaseEntry(
            id = "kb_ord_statuses",
            title = "Order Status Meanings (Pending, Processing, Completed, Partial, Failed, Cancelled)",
            category = SupportCategory.ORDER.key,
            question = "What do Pending, Processing, Completed, Partial, Failed, Cancelled, and Not Started mean?",
            answer = "• Pending / Awaiting API: Order is queued or waiting for provider dispatch.\n• Processing / In Progress: Actively delivering on the provider network.\n• Completed: Full requested quantity has been delivered.\n• Partial: Delivery stopped early; unfulfilled remainder should be reviewed by Admin for refund.\n• Cancelled: Order was stopped; contact Admin if a wallet refund review is needed.\n• Failed / Not Started / Not Received: Delivery could not start or complete (e.g., private link or provider error). Share your Order ID with Admin on WhatsApp (+91 6372505724) or open a Support Ticket.",
            keywords = "order status pending processing in progress completed partial failed cancelled not started not received",
            active = true
        ),
        KnowledgeBaseEntry(
            id = "kb_ord_refill",
            title = "Order Refill & Drop Protection",
            category = SupportCategory.ORDER.key,
            question = "How do refills work if followers or views drop?",
            answer = "Refills apply only to services marked 'Refill Supported' during their active refill window, provided your account/link remains public and unchanged. AI cannot automatically approve or trigger refills—if you experience a drop on a refill-eligible order, share your Order ID in a Support Ticket or chat with Admin on WhatsApp (+91 6372505724).",
            keywords = "refill drop followers lost warranty support guarantee",
            active = true
        ),

        // C. Wallet & Payment Support
        KnowledgeBaseEntry(
            id = "kb_wal_add_funds",
            title = "How to Add Funds, Deposit Limits & Payment Screenshots",
            category = SupportCategory.WALLET.key,
            question = "How do I add funds to my BINSMM wallet, what are min/max limits, and how do I share payment details?",
            answer = "Open Profile or Home -> 'Add Funds'. Supported manual deposit requests accept between $1.00 (minimum) and $100,000.00 (maximum) per request. Enter your exact transaction reference / UTR / payment note clearly (or send your payment screenshot directly to Admin WhatsApp +91 6372505724 along with your registered email) so Admin can verify and credit your wallet.",
            keywords = "add funds deposit wallet money minimum maximum screenshot utr reference payment",
            active = true
        ),
        KnowledgeBaseEntry(
            id = "kb_wal_pending_not_credited",
            title = "Payment Pending or Balance Not Credited",
            category = SupportCategory.WALLET.key,
            question = "My payment is pending or my balance was not credited yet. What should I do?",
            answer = "Manual deposit requests stay in 'Pending Verification' until verified by the BINSMM Admin, while automated gateways credit only after server-side webhook verification is configured. Check 'Wallet & Transactions' for your transaction ID. If your payment is still not credited, click 'Chat with Admin on WhatsApp' (+91 6372505724) with your transaction reference and payment screenshot.",
            keywords = "payment pending balance not credited wallet verification deposit delay",
            active = true
        ),

        // D. Service Support
        KnowledgeBaseEntry(
            id = "kb_svc_platforms_demo",
            title = "Instagram, YouTube, Facebook & Telegram Services (DEMO vs Live)",
            category = SupportCategory.SERVICE.key,
            question = "Which social platforms are supported and what does the DEMO label mean?",
            answer = "BINSMM supports Instagram (followers, likes, Reels views), YouTube (views, subscribers, likes), Facebook (page likes, post reactions, views), and Telegram (channel members, post views, reactions). Services tagged DEMO are template placeholders without a live SMM provider API connected—they never charge or place real orders until Admin links an active provider API.",
            keywords = "instagram youtube facebook telegram service demo template live provider",
            active = true
        ),

        // E. API & Reseller Support
        KnowledgeBaseEntry(
            id = "kb_api_reseller",
            title = "API & Reseller Integration Support",
            category = SupportCategory.API_RESELLER.key,
            question = "How does BINSMM support resellers and SMM Provider API connections?",
            answer = "BINSMM connects backend SMM v2 Provider APIs securely via Google Cloud Functions and Cloud Secret Manager—API keys are never exposed inside the Android app. For reseller bulk pricing, API partnership questions, or custom provider setup help, open a Support Ticket under 'API & Reseller' or message Admin on WhatsApp (+91 6372505724).",
            keywords = "api reseller bulk panel key integration webhook provider",
            active = true
        ),

        // F. General Support
        KnowledgeBaseEntry(
            id = "kb_gen_refunds_delays",
            title = "Refunds, Delays & Direct Admin Escalation",
            category = SupportCategory.GENERAL.key,
            question = "What is the policy for order delays, service drops, and refunds?",
            answer = "Estimated delivery times vary by service and network load. Automated refunds are not granted inside AI chat—all eligible cancellations, partial refunds, or payment disputes are reviewed atomically by Admin. Use the 'Create Support Ticket' tab or tap 'Chat with Admin on WhatsApp' (+91 6372505724) with your Order ID and issue summary for fast help.",
            keywords = "refund delay dispute complaint help admin whatsapp ticket",
            active = true
        )
    )
}
