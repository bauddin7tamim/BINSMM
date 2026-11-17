package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SmmProviderBalanceResponse(
    @Json(name = "balance") val balance: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class SmmProviderServiceDto(
    @Json(name = "service") val service: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "type") val type: String = "Default",
    @Json(name = "category") val category: String = "",
    @Json(name = "rate") val rate: String = "0",
    @Json(name = "min") val min: String = "10",
    @Json(name = "max") val max: String = "10000",
    @Json(name = "refill") val refill: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SmmProviderOrderResponse(
    @Json(name = "order") val order: Long? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class SmmProviderStatusResponse(
    @Json(name = "charge") val charge: String? = null,
    @Json(name = "start_count") val startCount: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "remains") val remains: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "error") val error: String? = null
)

interface SmmProviderApiService {
    @FormUrlEncoded
    @POST
    suspend fun getBalance(
        @Url endpointUrl: String,
        @Field("key") apiKey: String,
        @Field("action") action: String = "balance"
    ): SmmProviderBalanceResponse

    @FormUrlEncoded
    @POST
    suspend fun getServices(
        @Url endpointUrl: String,
        @Field("key") apiKey: String,
        @Field("action") action: String = "services"
    ): List<SmmProviderServiceDto>

    @FormUrlEncoded
    @POST
    suspend fun createOrder(
        @Url endpointUrl: String,
        @Field("key") apiKey: String,
        @Field("action") action: String = "add",
        @Field("service") serviceId: String,
        @Field("link") link: String,
        @Field("quantity") quantity: Long
    ): SmmProviderOrderResponse

    @FormUrlEncoded
    @POST
    suspend fun getOrderStatus(
        @Url endpointUrl: String,
        @Field("key") apiKey: String,
        @Field("action") action: String = "status",
        @Field("order") orderId: String
    ): SmmProviderStatusResponse
}

class SmmProviderClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val apiService: SmmProviderApiService = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SmmProviderApiService::class.java)

    suspend fun testConnection(baseUrl: String, apiKey: String): Result<SmmProviderBalanceResponse> {
        return runCatching {
            require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) {
                "Provider URL must start with https:// or http://"
            }
            require(apiKey.isNotBlank()) { "API key is required to test connection" }
            val response = apiService.getBalance(endpointUrl = baseUrl.trim(), apiKey = apiKey.trim())
            if (!response.error.isNullOrBlank()) {
                error("Provider API error: ${response.error}")
            }
            response
        }
    }

    suspend fun fetchRemoteServices(baseUrl: String, apiKey: String): Result<List<SmmProviderServiceDto>> {
        return runCatching {
            require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) {
                "Provider URL must start with https:// or http://"
            }
            require(apiKey.isNotBlank()) { "API key is required to sync services" }
            apiService.getServices(endpointUrl = baseUrl.trim(), apiKey = apiKey.trim())
        }
    }
}

/**
 * Clean abstraction for external Payment Gateways (Stripe, Razorpay, PayPal, Crypto).
 * No gateway is faked as live until its real backend SDK/webhook URL is provisioned.
 */
data class PaymentGatewayDescriptor(
    val id: String,
    val name: String,
    val subtitle: String,
    val isConfigured: Boolean = false
)

object PaymentGatewayRegistry {
    val availableGateways = listOf(
        PaymentGatewayDescriptor(
            id = "stripe_unconfigured",
            name = "Cards & Apple/Google Pay (Stripe)",
            subtitle = "Gateway integration not configured — requires server PaymentIntent endpoint",
            isConfigured = false
        ),
        PaymentGatewayDescriptor(
            id = "razorpay_unconfigured",
            name = "UPI / NetBanking / Cards (Razorpay)",
            subtitle = "Gateway integration not configured — requires Merchant Key & webhook",
            isConfigured = false
        ),
        PaymentGatewayDescriptor(
            id = "manual_request",
            name = "Manual Deposit Verification Request",
            subtitle = "Submit a payment reference for manual Admin approval & balance credit",
            isConfigured = true
        )
    )
}
