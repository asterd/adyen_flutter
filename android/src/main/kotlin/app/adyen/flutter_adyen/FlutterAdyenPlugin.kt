package app.adyen.flutter_adyen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.NonNull
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.base.model.PaymentMethodsApiResponse
import com.adyen.checkout.base.model.payments.Amount
import com.adyen.checkout.base.model.payments.request.*
import com.adyen.checkout.base.model.payments.response.Action
import com.adyen.checkout.bcmc.BcmcConfiguration
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.util.LocaleUtil
import com.adyen.checkout.dropin.DropIn
import com.adyen.checkout.dropin.DropInConfiguration
import com.adyen.checkout.dropin.service.CallResult
import com.adyen.checkout.dropin.service.DropInService
import com.adyen.checkout.redirect.RedirectComponent
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.UUID
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.Serializable
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.google.gson.reflect.TypeToken
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import java.util.*

class FlutterAdyenPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private var flutterResult: Result? = null
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_adyen")
        channel.setMethodCallHandler(this)
    }
    
    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {}
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "openDropIn" -> {
                this.activity?.let { a ->
                    val additionalData = call.argument<Map<String, String>>("additionalData")
                    val paymentMethods = call.argument<String>("paymentMethods")
                    val baseUrl = call.argument<String>("baseUrl")
                    val clientKey = call.argument<String>("clientKey")
                    val publicKey = call.argument<String>("publicKey")
                    val amount = call.argument<String>("amount")
                    val currency = call.argument<String>("currency")
                    val env = call.argument<String>("environment")
                    val lineItem = call.argument<Map<String, String>>("lineItem")
                    val reference = call.argument<String>("reference")
                    val shopperReference = call.argument<String>("shopperReference")
                    val authToken = call.argument<String>("authToken")
                    val merchantAccount = call.argument<String>("merchantAccount")

                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    val lineItemString = JSONObject(lineItem).toString()
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    val additionalDataString = JSONObject(additionalData).toString()
                    val localeString = call.argument<String>("locale") ?: "it_IT"
                    val countryCode = localeString.split("_").last()

                    var environment = Environment.TEST
                    when (env) {
                        "LIVE_US" -> {
                            environment = Environment.UNITED_STATES
                        }
                        "LIVE_AUSTRALIA" -> {
                            environment = Environment.AUSTRALIA
                        }
                        "LIVE_EUROPE" -> {
                            environment = Environment.EUROPE
                        }
                    }

                    try {
                        val jsonObject = JSONObject(paymentMethods ?: "")
                        val paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject)
                        val shopperLocale = LocaleUtil.fromLanguageTag(localeString)
                        val googlePayConfig = GooglePayConfiguration.Builder(a, merchantAccount
                                ?: "").build()
                        val bcmcConfig = BcmcConfiguration.Builder(a).setPublicKey(publicKey ?: "").build()
                        val cardConfiguration = CardConfiguration.Builder(a)
                                .setHolderNameRequire(false)
                                .setPublicKey(publicKey ?: "")
                                .setShopperLocale(shopperLocale)
                                .setEnvironment(environment)
                                .build()

                        val resultIntent = Intent(a, a::class.java)
                        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        resultIntent.putExtra("baseUrl", baseUrl)
                        resultIntent.putExtra("Authorization", authToken)

                        val sharedPref = a.getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            remove("AdyenResultCode")
                            putString("baseUrl", baseUrl)
                            putString("amount", "$amount")
                            putString("countryCode", countryCode)
                            putString("currency", currency)
                            putString("lineItem", lineItemString)
                            putString("additionalData", additionalDataString)
                            putString("shopperReference", shopperReference)
                            putString("Authorization", authToken)
                            putString("merchantAccount", merchantAccount)
                            putString("reference", reference)
                            commit()
                        }

                        val dropInConfiguration = DropInConfiguration.Builder(a, resultIntent, AdyenDropinService::class.java)
                                .setClientKey(clientKey ?: "")
                                .addCardConfiguration(cardConfiguration)
                                .addGooglePayConfiguration(googlePayConfig)
                                .addBcmcConfiguration(bcmcConfig)
                                .build()
                        DropIn.startPayment(a, paymentMethodsApiResponse, dropInConfiguration)
                        flutterResult = result
                    } catch (e: Throwable) {
                        result.error("PAYMENT_ERROR", "${e.printStackTrace()}", "")
                    }
                } ?: result.error("PAYMENT_ERROR", "NO ACTIVITY FOUND", "")
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        this.activity?.let { a ->
            val sharedPref = a.getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
            val storedResultCode = sharedPref.getString("AdyenResultCode", "PAYMENT_CANCELLED")
            flutterResult?.success(storedResultCode)
            flutterResult = null
        }
        return true
    }
}

/*
class FlutterAdyenPlugin(private val activity: Activity) : MethodCallHandler, PluginRegistry.ActivityResultListener {
    private var flutterResult: Result? = null

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_adyen")
            val plugin = FlutterAdyenPlugin(registrar.activity())
            channel.setMethodCallHandler(plugin)
            registrar.addActivityResultListener(plugin)
        }
    }

    override fun onMethodCall(call: MethodCall, res: Result) {
        when (call.method) {
            "openDropIn" -> {

                val additionalData = call.argument<Map<String, String>>("additionalData")
                val paymentMethods = call.argument<String>("paymentMethods")
                val baseUrl = call.argument<String>("baseUrl")
                val clientKey = call.argument<String>("clientKey")
                val publicKey = call.argument<String>("publicKey")
                val amount = call.argument<String>("amount")
                val currency = call.argument<String>("currency")
                val env = call.argument<String>("environment")
                val lineItem = call.argument<Map<String, String>>("lineItem")
                val reference = call.argument<String>("reference")
                val shopperReference = call.argument<String>("shopperReference")
                val authToken = call.argument<String>("authToken")
                val merchantAccount = call.argument<String>("merchantAccount")

                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                val lineItemString = JSONObject(lineItem).toString()
                val additionalDataString = JSONObject(additionalData).toString()
                val localeString = call.argument<String>("locale") ?: "it_IT"
                val countryCode = localeString.split("_").last()

                var environment = Environment.TEST
                when (env) {
                    "LIVE_US" -> {
                        environment = Environment.UNITED_STATES
                    }
                    "LIVE_AUSTRALIA" -> {
                        environment = Environment.AUSTRALIA
                    }
                    "LIVE_EUROPE" -> {
                        environment = Environment.EUROPE
                    }
                }

                try {
                    val jsonObject = JSONObject(paymentMethods ?: "")
                    val paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject)
                    val shopperLocale = LocaleUtil.fromLanguageTag(localeString)
                    val googlePayConfig = GooglePayConfiguration.Builder(activity, merchantAccount ?: "").build()
                    val bcmcConfig = BcmcConfiguration.Builder(activity).build()
                    val cardConfiguration = CardConfiguration.Builder(activity)
                            .setHolderNameRequire(false)
                            .setPublicKey(publicKey ?: "")
                            .setShopperLocale(shopperLocale)
                            .setEnvironment(environment)
                            .build()

                    val resultIntent = Intent(activity, activity::class.java)
                    resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    resultIntent.putExtra("baseUrl", baseUrl)
                    resultIntent.putExtra("Authorization", authToken)

                    val sharedPref = activity.getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        remove("AdyenResultCode")
                        putString("baseUrl", baseUrl)
                        putString("amount", "$amount")
                        putString("countryCode", countryCode)
                        putString("currency", currency)
                        putString("lineItem", lineItemString)
                        putString("additionalData", additionalDataString)
                        putString("shopperReference", shopperReference)
                        putString("Authorization", authToken)
                        putString("merchantAccount", merchantAccount)
                        putString("reference", reference)
                        commit()
                    }

                    val dropInConfiguration = DropInConfiguration.Builder(activity, resultIntent, AdyenDropinService::class.java)
                            .setClientKey(clientKey ?: "")
                            .addCardConfiguration(cardConfiguration)
                            .addGooglePayConfiguration(googlePayConfig)
                            .addBcmcConfiguration(bcmcConfig)
                            .build()
                    DropIn.startPayment(activity, paymentMethodsApiResponse, dropInConfiguration)
                    flutterResult = res
                } catch (e: Throwable) {
                    res.error("PAYMENT_ERROR", "${e.printStackTrace()}", "")
                }


            }
            else -> {
                res.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val sharedPref = activity.getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
        val storedResultCode = sharedPref.getString("AdyenResultCode", "PAYMENT_CANCELLED")
        flutterResult?.success(storedResultCode)
        flutterResult = null
        return true
    }

}
*/

/**
 * This is just an example on how to make network calls on the [DropInService].
 * You should make the calls to your own servers and have additional data or processing if necessary.
 */
inline fun <reified T> Gson.fromJson(json: String): T = fromJson<T>(json, object: TypeToken<T>() {}.type)


class AdyenDropinService : DropInService() {

//    companion object {
//        private val TAG = LogUtil.getTag()
//    }

    override fun makePaymentsCall(paymentComponentData: JSONObject): CallResult {
        log("start payment request")
        val sharedPref = getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
        val baseUrl = sharedPref.getString("baseUrl", "UNDEFINED_STR")
        val authorization = sharedPref.getString("Authorization", "UNDEFINED_STR")
        val amount = sharedPref.getString("amount", "UNDEFINED_STR")
        val currency = sharedPref.getString("currency", "UNDEFINED_STR")
        val countryCode = sharedPref.getString("countryCode", "DE")
        val lineItemString = sharedPref.getString("lineItem", "UNDEFINED_STR")
        val additionalDataString = sharedPref.getString("additionalData", "UNDEFINED_STR")
        val merchantAccount = sharedPref.getString("merchantAccount", "UNDEFINED_STR")
        val shopperReference = sharedPref.getString("shopperReference", null)
        val uuid: UUID = UUID.randomUUID()
        val reference = sharedPref.getString("reference", uuid.toString())

        var lineItem : LineItem? = null
        // log("lineItem: ${lineItemString}")
        if (lineItemString?: "{}" != "{}") { // check is not an empty map
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(LineItem::class.java)
            lineItem = jsonAdapter.fromJson(lineItemString ?: "")
        }

        val gson = Gson()
        val additionalData = gson.fromJson<Map<String, String>>(additionalDataString ?: "")
        val serializedPaymentComponentData = PaymentComponentData.SERIALIZER.deserialize(paymentComponentData)

        if (serializedPaymentComponentData.paymentMethod == null)
            return CallResult(CallResult.ResultType.ERROR, "Empty payment data")

        val paymentsRequest = createPaymentsRequest(this@AdyenDropinService,
                lineItem,
                serializedPaymentComponentData,
                amount?: "",
                currency ?: "",
                reference ?: "",
                merchantAccount ?: "",
                shopperReference = shopperReference,
                countryCode = countryCode ?: "IT",
                additionalData = additionalData
        )
        val paymentsRequestJson = dataObjectToJsonString(paymentsRequest)

        val requestBody = RequestBody.create(MediaType.parse("application/json"), paymentsRequestJson) // .toString())
        log("payment request body: $paymentsRequestJson $authorization")
        val headers: HashMap<String, String> = HashMap()
        headers["Authorization"] = authorization ?: ""
        val call = getService(headers, baseUrl ?: "").payments(requestBody)
        call.request().headers()
        return try {
            val response = call.execute()
            val paymentsResponse = response.body()
            log("Payment response $paymentsResponse")

            if (response.isSuccessful && paymentsResponse != null) {
                if (paymentsResponse.action != null) {
                    with(sharedPref.edit()) {
                        putString("AdyenResultCode", paymentsResponse.action.toString())
                        commit()
                    }
                    CallResult(CallResult.ResultType.ACTION, Action.SERIALIZER.serialize(paymentsResponse.action).toString())
                } else {
                    if (paymentsResponse.resultCode != null &&
                            (paymentsResponse.resultCode == "Authorised" || paymentsResponse.resultCode == "Received" || paymentsResponse.resultCode == "Pending")) {
                        with(sharedPref.edit()) {
                            putString("AdyenResultCode", paymentsResponse.resultCode)
                            commit()
                        }
                        CallResult(CallResult.ResultType.FINISHED, paymentsResponse.resultCode)
                    } else {
                        with(sharedPref.edit()) {
                            putString("AdyenResultCode", paymentsResponse.resultCode ?: "EMPTY")
                            commit()
                        }
                        CallResult(CallResult.ResultType.FINISHED, paymentsResponse.resultCode
                                ?: "EMPTY")
                    }
                }
            } else {
                with(sharedPref.edit()) {
                    putString("AdyenResultCode", "ERROR")
                    commit()
                }
                CallResult(CallResult.ResultType.ERROR, "IOException")
            }
        } catch (e: IOException) {
            log("Errore $e")
            with(sharedPref.edit()) {
                putString("AdyenResultCode", "ERROR")
                commit()
            }
            CallResult(CallResult.ResultType.ERROR, "IOException")
        }
    }

    override fun makeDetailsCall(actionComponentData: JSONObject): CallResult {
        val sharedPref = getSharedPreferences("ADYEN", Context.MODE_PRIVATE)
        val baseUrl = sharedPref.getString("baseUrl", "UNDEFINED_STR")
        val authorization = sharedPref.getString("Authorization", "UNDEFINED_STR")
        val requestBody = RequestBody.create(MediaType.parse("application/json"), actionComponentData.toString())
        val headers: HashMap<String, String> = HashMap()
        headers["Authorization"] = authorization ?: ""
        log("payment request details body: $actionComponentData $authorization")

        val call = getService(headers, baseUrl ?: "").details(requestBody)
        return try {
            val response = call.execute()
            val detailsResponse = response.body()
            if (response.isSuccessful && detailsResponse != null) {
                if (detailsResponse.action != null) {
                    with(sharedPref.edit()) {
                        putString("AdyenResultCode", detailsResponse.action.toString())
                        commit()
                    }
                    CallResult(CallResult.ResultType.ACTION, Action.SERIALIZER.serialize(detailsResponse.action).toString())
                }
                else if (detailsResponse.resultCode != null &&
                        (detailsResponse.resultCode == "Authorised" || detailsResponse.resultCode == "Received" || detailsResponse.resultCode == "Pending")) {
                    with(sharedPref.edit()) {
                        putString("AdyenResultCode", detailsResponse.resultCode)
                        commit()
                    }
                    CallResult(CallResult.ResultType.FINISHED, detailsResponse.resultCode)
                } else {
                    with(sharedPref.edit()) {
                        putString("AdyenResultCode", detailsResponse.resultCode ?: "EMPTY")
                        commit()
                    }
                    CallResult(CallResult.ResultType.FINISHED, detailsResponse.resultCode
                            ?: "EMPTY")
                }
            } else {
                with(sharedPref.edit()) {
                    putString("AdyenResultCode", "ERROR")
                    commit()
                }
                CallResult(CallResult.ResultType.ERROR, "IOException")
            }
        } catch (e: IOException) {
            with(sharedPref.edit()) {
                putString("AdyenResultCode", "ERROR")
                commit()
            }
            CallResult(CallResult.ResultType.ERROR, "IOException")
        }
    }
}


fun createPaymentsRequest(
        context: Context, lineItem: LineItem?, paymentComponentData: PaymentComponentData<out PaymentMethodDetails>,
        amount: String, currency: String, reference: String, merchantAccount: String,
        shopperReference: String?, countryCode: String, additionalData: Map<String, String>
): PaymentsRequest {
    @Suppress("UsePropertyAccessSyntax")
    return PaymentsRequest(
            payment = Payment(paymentComponentData.getPaymentMethod() as PaymentMethodDetails,
                    countryCode,
                    paymentComponentData.isStorePaymentMethodEnable,
                    getAmount(amount, currency),
                    reference,
                    merchantAccount,
                    RedirectComponent.getReturnUrl(context),
                    lineItems = listOf(lineItem),
                    shopperReference = shopperReference),
            additionalData = additionalData

    )
}

private fun getAmount(amount: String, currency: String) = createAmount(amount.toInt(), currency)

fun createAmount(value: Int, currency: String): Amount {
    val amount = Amount()
    amount.currency = currency
    amount.value = value
    return amount
}

data class Payment(
        val paymentMethod: PaymentMethodDetails,
        val countryCode: String = "DE",
        val storePaymentMethod: Boolean,
        val amount: Amount,
        val reference: String,
        val merchantAccount: String,
        val returnUrl: String,
        val channel: String = "Android",
        val lineItems: List<LineItem?>,
        val additionalData: AdditionalData = AdditionalData(allow3DS2 = "true", executeTreeD = "true"),
        val shopperReference: String?
): Serializable

data class PaymentsRequest(
        val payment: Payment,
        val additionalData: Map<String, String>
): Serializable
data class LineItem(
        val id: String,
        val description: String
): Serializable

data class AdditionalData(val allow3DS2: String = "true", val executeTreeD: String = "true")

private fun dataObjectToJsonString(paymentsRequest: PaymentsRequest): String {
    val gson = Gson()
    return gson.toJson(paymentsRequest)
}

//private fun serializePaymentsRequest(paymentsRequest: PaymentsRequest): JSONObject {
//    val gson = Gson()
//    val jsonString = gson.toJson(paymentsRequest)
//    // print(jsonString)
//    val request = JSONObject(jsonString)
//    // print(request)
//    return request
//}

private fun log(toLog: String) {
    Log.d("ADYEN", "ADYEN (native) : $toLog")
}