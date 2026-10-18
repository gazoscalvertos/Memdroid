package com.memdroid.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Buy me a beer" tips through Google Play Billing. Tips are consumable one-off products,
 * so people can give more than once, and nothing in the app is unlocked by them.
 * The product IDs must match the in-app products created in Play Console.
 */
class Tips(context: Context) : PurchasesUpdatedListener {

    enum class State { CONNECTING, READY, UNAVAILABLE }

    data class Product(val id: String, val emoji: String, val title: String)

    data class Tip(val product: Product, val price: String, val details: ProductDetails)

    /** A tip that went through, waiting for the supporter to choose how to appear on the heroes wall. */
    data class Thanks(val productId: String, val orderId: String?)

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _state = MutableStateFlow(State.CONNECTING)
    val state: StateFlow<State> = _state

    private val _tips = MutableStateFlow<List<Tip>>(emptyList())
    val tips: StateFlow<List<Tip>> = _tips

    private val _pending = MutableStateFlow(false)
    /** True while Play is waiting for a slow payment method such as cash or bank transfer. */
    val pending: StateFlow<Boolean> = _pending

    private val _thanks = MutableStateFlow<Thanks?>(null)
    val thanks: StateFlow<Thanks?> = _thanks

    fun connect() {
        if (client.isReady) return
        _state.value = State.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    loadProducts()
                    recoverPurchases()
                } else {
                    _state.value = State.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = State.UNAVAILABLE
            }
        })
    }

    fun disconnect() {
        client.endConnection()
    }

    fun buy(activity: Activity, tip: Tip) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(tip.details).build())
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun clearThanks() {
        _thanks.value = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingResponseCode.OK) purchases?.forEach(::handle)
    }

    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(PRODUCTS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it.id)
                    .setProductType(ProductType.INAPP)
                    .build()
            })
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) {
                _state.value = State.UNAVAILABLE
                return@queryProductDetailsAsync
            }
            val byId = details.productDetailsList.associateBy { it.productId }
            _tips.value = PRODUCTS.mapNotNull { product ->
                val d = byId[product.id] ?: return@mapNotNull null
                val price = d.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
                Tip(product, price, d)
            }
            // Sideloaded copies, and builds before the products exist in Play Console, get no products.
            _state.value = if (_tips.value.isEmpty()) State.UNAVAILABLE else State.READY
        }
    }

    /** Finish tips that were paid while the app was closed, or that were pending. */
    private fun recoverPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) purchases.forEach(::handle)
        }
    }

    private fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> _pending.value = true
            Purchase.PurchaseState.PURCHASED -> {
                _pending.value = false
                // Consuming the tip lets the same supporter tip again later.
                val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.consumeAsync(params) { result, _ ->
                    if (result.responseCode == BillingResponseCode.OK) {
                        _thanks.value = Thanks(purchase.products.firstOrNull().orEmpty(), purchase.orderId)
                    }
                }
            }
            else -> Unit
        }
    }

    companion object {
        val PRODUCTS = listOf(
            Product("beer_half", "🍺", "Half pint"),
            Product("beer_pint", "🍻", "A pint"),
            Product("beer_round", "🎉", "A round for the team"),
        )
    }
}
