package com.example.util

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.BuildConfig
import com.example.settings.AppSettings
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * V83 commercial backup layer.
 * - Exports core operational Firestore data and portable lab branding/settings.
 * - Encrypts every backup with AES-256-GCM using a password-derived key.
 * - Restore is merge/upsert only: it never mass-deletes server data.
 */
object CommercialBackupManager {
    private const val HEADER = "TAHALIL_BACKUP_V1"
    private const val SCHEMA_VERSION = 3
    private const val PBKDF2_ROUNDS = 180_000
    private const val MAX_ENCRYPTED_BYTES = 160 * 1024 * 1024
    private const val WRITE_BATCH_SIZE = 350

    data class ExportResult(
        val encrypted: ByteArray,
        val customers: Int,
        val orders: Int,
        val payments: Int,
        val resultDocuments: Int
    )

    data class RestoreResult(
        val settings: AppSettings?,
        val brandLogoBytes: ByteArray?,
        val documentsWritten: Int,
        val customers: Int,
        val orders: Int,
        val payments: Int,
        val resultDocuments: Int
    )

    fun createEncryptedBackup(
        db: FirebaseFirestore,
        settings: AppSettings,
        password: String,
        onResult: (Result<ExportResult>) -> Unit
    ) {
        if (!validBackupPassword(password)) {
            onResult(Result.failure(IllegalArgumentException("كلمة مرور النسخة الاحتياطية لازم تكون 10 أحرف على الأقل")))
            return
        }

        db.collection("customers").get()
            .addOnSuccessListener { customersSnapshot ->
                val customerDocs = customersSnapshot.documents.map { doc ->
                    backupDoc(doc.id, doc.data.orEmpty())
                }

                fetchOrders(db, customersSnapshot) { orderResult ->
                    orderResult.fold(
                        onSuccess = { orders ->
                            fetchPayments(db, orders) { paymentResult ->
                                paymentResult.fold(
                                    onSuccess = { payments ->
                                        fetchResultFileActivity(db, customersSnapshot) { resultFilesResult ->
                                            resultFilesResult.fold(
                                                onSuccess = { resultFiles ->
                                        fetchSimpleCollection(db, "customer_price_overrides") { customerPricesResult ->
                                            customerPricesResult.fold(
                                                onSuccess = { customerPrices ->
                                                    fetchSimpleCollection(db, "lab2lab_prices") { labPricesResult ->
                                                        labPricesResult.fold(
                                                            onSuccess = { labPrices ->
                                                                fetchSimpleCollection(db, "phone_registry") { phoneRegistryResult ->
                                                                    phoneRegistryResult.fold(
                                                                        onSuccess = { phoneRegistry ->
                                                                            fetchSimpleCollection(db, "lab_orders") { labOrdersResult ->
                                                                                labOrdersResult.fold(
                                                                                    onSuccess = { labOrders ->
                                                                            runCatching {
                                                                                val root = JSONObject().apply {
                                                                                    put("schema_version", SCHEMA_VERSION)
                                                                                    put("application_id", BuildConfig.APPLICATION_ID)
                                                                                    put("app_version_code", BuildConfig.VERSION_CODE)
                                                                                    put("app_version_name", BuildConfig.VERSION_NAME)
                                                                                    put("created_at_ms", System.currentTimeMillis())
                                                                                    put("settings", settingsToJson(settings))
                                                                                    put("customers", JSONArray(customerDocs))
                                                                                    put("orders", JSONArray(orders.map { it.json }))
                                                                                    put("payments", JSONArray(payments.map { it.json }))
                                                                                    put("result_file_activity", JSONArray(resultFiles.map { it.json }))
                                                                                    put("lab_orders", JSONArray(labOrders))
                                                                                    put("customer_price_overrides", JSONArray(customerPrices))
                                                                                    put("lab2lab_prices", JSONArray(labPrices))
                                                                                    put("phone_registry", JSONArray(phoneRegistry))
                                                                                }
                                                                                val encrypted = encrypt(root.toString().toByteArray(StandardCharsets.UTF_8), password)
                                                                                ExportResult(
                                                                                    encrypted = encrypted,
                                                                                    customers = customerDocs.size,
                                                                                    orders = orders.size,
                                                                                    payments = payments.size,
                                                                                    resultDocuments = resultFiles.size
                                                                                )
                                                                            }.fold(
                                                                                onSuccess = { onResult(Result.success(it)) },
                                                                                onFailure = { onResult(Result.failure(it)) }
                                                                            )
                                                                                    },
                                                                                    onFailure = { onResult(Result.failure(it)) }
                                                                                )
                                                                            }
                                                                        },
                                                                        onFailure = { onResult(Result.failure(it)) }
                                                                    )
                                                                }
                                                            },
                                                            onFailure = { onResult(Result.failure(it)) }
                                                        )
                                                    }
                                                },
                                                onFailure = { onResult(Result.failure(it)) }
                                            )
                                        }
                                                },
                                                onFailure = { onResult(Result.failure(it)) }
                                            )
                                        }
                                    },
                                    onFailure = { onResult(Result.failure(it)) }
                                )
                            }
                        },
                        onFailure = { onResult(Result.failure(it)) }
                    )
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /**
     * V134 safety export.
     * Adds a recovery archive for user profiles, approved/pending device records and immutable audit logs.
     * Firebase Auth accounts are intentionally NOT recreated from the app; those records are preserved
     * for controlled disaster recovery only. Core operational restore behavior remains unchanged.
     */
    fun createEncryptedBackupV134(
        db: FirebaseFirestore,
        settings: AppSettings,
        password: String,
        onResult: (Result<ExportResult>) -> Unit
    ) {
        if (!validBackupPassword(password)) {
            onResult(Result.failure(IllegalArgumentException("كلمة مرور النسخة الاحتياطية لازم تكون 10 أحرف على الأقل")))
            return
        }
        val main = Handler(Looper.getMainLooper())
        Thread({
            val result = runCatching {
                val customersSnapshot = Tasks.await(db.collection("customers").get(), 45, TimeUnit.SECONDS)
                val customerDocs = customersSnapshot.documents.map { backupDoc(it.id, it.data.orEmpty()) }

                val orders = mutableListOf<OrderBackup>()
                customersSnapshot.documents.forEach { customerDoc ->
                    val orderSnapshot = Tasks.await(customerDoc.reference.collection("orders").get(), 45, TimeUnit.SECONDS)
                    orderSnapshot.documents.forEach { orderDoc ->
                        orders += OrderBackup(
                            customerId = customerDoc.id,
                            orderId = orderDoc.id,
                            json = JSONObject().apply {
                                put("customer_id", customerDoc.id)
                                put("id", orderDoc.id)
                                put("data", jsonObjectFromMap(orderDoc.data.orEmpty()))
                            }
                        )
                    }
                }

                val payments = mutableListOf<PaymentBackup>()
                orders.forEach { order ->
                    val paymentSnapshot = Tasks.await(
                        db.collection("customers").document(order.customerId)
                            .collection("orders").document(order.orderId)
                            .collection("payments").get(),
                        45, TimeUnit.SECONDS
                    )
                    paymentSnapshot.documents.forEach { paymentDoc ->
                        payments += PaymentBackup(
                            customerId = order.customerId,
                            orderId = order.orderId,
                            json = JSONObject().apply {
                                put("customer_id", order.customerId)
                                put("order_id", order.orderId)
                                put("id", paymentDoc.id)
                                put("data", jsonObjectFromMap(paymentDoc.data.orEmpty()))
                            }
                        )
                    }
                }

                val resultFiles = mutableListOf<ResultActivityBackup>()
                customersSnapshot.documents.forEach { customerDoc ->
                    listOf("result_file_meta", "result_file_chunk").forEach { type ->
                        val activitySnapshot = Tasks.await(
                            customerDoc.reference.collection("activity").whereEqualTo("type", type).get(),
                            45, TimeUnit.SECONDS
                        )
                        activitySnapshot.documents.forEach { doc ->
                            resultFiles += ResultActivityBackup(
                                customerId = customerDoc.id,
                                id = doc.id,
                                json = JSONObject().apply {
                                    put("customer_id", customerDoc.id)
                                    put("id", doc.id)
                                    put("data", jsonObjectFromMap(doc.data.orEmpty()))
                                }
                            )
                        }
                    }
                }

                fun simple(name: String): List<JSONObject> {
                    val snap = Tasks.await(db.collection(name).get(), 45, TimeUnit.SECONDS)
                    return snap.documents.map { backupDoc(it.id, it.data.orEmpty()) }
                }

                val labOrders = simple("lab_orders")
                val customerPrices = simple("customer_price_overrides")
                val labPrices = simple("lab2lab_prices")
                val phoneRegistry = simple("phone_registry")
                val catalogOverrides = simple("lab_catalog_overrides")
                val users = simple("users")
                val auditLogs = simple("audit_logs")

                val userDevices = mutableListOf<JSONObject>()
                users.forEach { user ->
                    val uid = safeDocId(user.getString("id"))
                    val snap = Tasks.await(
                        db.collection("users").document(uid).collection("devices").get(),
                        45, TimeUnit.SECONDS
                    )
                    snap.documents.forEach { deviceDoc ->
                        userDevices += JSONObject().apply {
                            put("user_id", uid)
                            put("id", deviceDoc.id)
                            put("data", jsonObjectFromMap(deviceDoc.data.orEmpty()))
                        }
                    }
                }

                val root = JSONObject().apply {
                    put("schema_version", SCHEMA_VERSION)
                    put("application_id", BuildConfig.APPLICATION_ID)
                    put("app_version_code", BuildConfig.VERSION_CODE)
                    put("app_version_name", BuildConfig.VERSION_NAME)
                    put("created_at_ms", System.currentTimeMillis())
                    put("settings", settingsToJson(settings))
                    put("customers", JSONArray(customerDocs))
                    put("orders", JSONArray(orders.map { it.json }))
                    put("payments", JSONArray(payments.map { it.json }))
                    put("result_file_activity", JSONArray(resultFiles.map { it.json }))
                    put("lab_orders", JSONArray(labOrders))
                    put("customer_price_overrides", JSONArray(customerPrices))
                    put("lab2lab_prices", JSONArray(labPrices))
                    put("phone_registry", JSONArray(phoneRegistry))
                    put("lab_catalog_overrides", JSONArray(catalogOverrides))
                    put("users", JSONArray(users))
                    put("user_devices", JSONArray(userDevices))
                    put("audit_logs", JSONArray(auditLogs))
                    put("security_archive_restore_mode", "manual_only")
                }
                val encrypted = encrypt(root.toString().toByteArray(StandardCharsets.UTF_8), password)
                ExportResult(
                    encrypted = encrypted,
                    customers = customerDocs.size,
                    orders = orders.size,
                    payments = payments.size,
                    resultDocuments = resultFiles.size
                )
            }
            main.post { onResult(result) }
        }, "tahalil-v134-safety-backup").apply { isDaemon = true }.start()
    }

    /**
     * V135 permission-safe restore.
     * Existing Firestore documents are NEVER overwritten. The restore only creates documents
     * that are currently missing, in dependency order (customers -> orders -> payments -> rest).
     * This avoids immutable-payment/update-rule failures and preserves newer live data.
     */
    fun restoreEncryptedBackup(
        db: FirebaseFirestore,
        encryptedBytes: ByteArray,
        password: String,
        onResult: (Result<RestoreResult>) -> Unit
    ) {
        if (encryptedBytes.isEmpty() || encryptedBytes.size > MAX_ENCRYPTED_BYTES) {
            onResult(Result.failure(IllegalArgumentException("ملف النسخة الاحتياطية غير صالح أو حجمه غير مدعوم")))
            return
        }
        if (!validBackupPassword(password)) {
            onResult(Result.failure(IllegalArgumentException("أدخل كلمة مرور النسخة الاحتياطية الصحيحة")))
            return
        }

        val parsed = runCatching {
            val plain = decrypt(encryptedBytes, password)
            JSONObject(String(plain, StandardCharsets.UTF_8))
        }.getOrElse {
            onResult(Result.failure(IllegalArgumentException("تعذر فتح النسخة الاحتياطية. راجع كلمة المرور والملف")))
            return
        }

        if (parsed.optInt("schema_version", -1) !in 1..SCHEMA_VERSION ||
            parsed.optString("application_id") != BuildConfig.APPLICATION_ID
        ) {
            onResult(Result.failure(IllegalArgumentException("النسخة الاحتياطية لا تخص هذا النظام أو إصدارها غير مدعوم")))
            return
        }

        val authUser = FirebaseAuth.getInstance().currentUser
        val restoreUid = authUser?.uid.orEmpty()
        val restoreEmail = authUser?.email.orEmpty()
        if (restoreUid.isBlank()) {
            onResult(Result.failure(IllegalStateException("يجب تسجيل الدخول بحساب المدير قبل الاسترجاع")))
            return
        }

        data class RestoreWrite(
            val ref: DocumentReference,
            val data: Map<String, Any?>,
            val kind: String
        )

        fun createSafeData(kind: String, original: Map<String, Any?>): Map<String, Any?> {
            if (kind !in setOf("customer", "order", "payment", "lab_order")) return original
            return original.toMutableMap().apply {
                // Firestore create rules bind newly restored operational records to the manager
                // performing the recovery. Historical immutable data is otherwise preserved.
                put("created_by_uid", restoreUid)
                if (restoreEmail.isNotBlank()) put("created_by_email", restoreEmail)
            }
        }

        val customerWrites = mutableListOf<RestoreWrite>()
        val orderWrites = mutableListOf<RestoreWrite>()
        val paymentWrites = mutableListOf<RestoreWrite>()
        val resultWrites = mutableListOf<RestoreWrite>()
        val otherWrites = mutableListOf<RestoreWrite>()

        runCatching {
            val customers = parsed.optJSONArray("customers") ?: JSONArray()
            for (i in 0 until customers.length()) {
                val obj = customers.getJSONObject(i)
                val id = safeDocId(obj.getString("id"))
                val data = jsonObjectToFirestoreMap(obj.getJSONObject("data"))
                customerWrites += RestoreWrite(db.collection("customers").document(id), createSafeData("customer", data), "customer")
            }

            val orders = parsed.optJSONArray("orders") ?: JSONArray()
            for (i in 0 until orders.length()) {
                val obj = orders.getJSONObject(i)
                val customerId = safeDocId(obj.getString("customer_id"))
                val id = safeDocId(obj.getString("id"))
                val data = jsonObjectToFirestoreMap(obj.getJSONObject("data"))
                orderWrites += RestoreWrite(
                    db.collection("customers").document(customerId).collection("orders").document(id),
                    createSafeData("order", data), "order"
                )
            }

            val payments = parsed.optJSONArray("payments") ?: JSONArray()
            for (i in 0 until payments.length()) {
                val obj = payments.getJSONObject(i)
                val customerId = safeDocId(obj.getString("customer_id"))
                val orderId = safeDocId(obj.getString("order_id"))
                val id = safeDocId(obj.getString("id"))
                val data = jsonObjectToFirestoreMap(obj.getJSONObject("data"))
                paymentWrites += RestoreWrite(
                    db.collection("customers").document(customerId)
                        .collection("orders").document(orderId)
                        .collection("payments").document(id),
                    createSafeData("payment", data), "payment"
                )
            }

            val resultDocs = parsed.optJSONArray("result_file_activity") ?: JSONArray()
            for (i in 0 until resultDocs.length()) {
                val obj = resultDocs.getJSONObject(i)
                val customerId = safeDocId(obj.getString("customer_id"))
                val id = safeDocId(obj.getString("id"))
                resultWrites += RestoreWrite(
                    db.collection("customers").document(customerId).collection("activity").document(id),
                    jsonObjectToFirestoreMap(obj.getJSONObject("data")), "result"
                )
            }

            fun addSimple(key: String, collection: String, kind: String = "simple") {
                val array = parsed.optJSONArray(key) ?: return
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = safeDocId(obj.getString("id"))
                    val data = jsonObjectToFirestoreMap(obj.getJSONObject("data"))
                    otherWrites += RestoreWrite(
                        db.collection(collection).document(id),
                        createSafeData(kind, data), kind
                    )
                }
            }
            addSimple("lab_orders", "lab_orders", "lab_order")
            addSimple("customer_price_overrides", "customer_price_overrides")
            addSimple("lab2lab_prices", "lab2lab_prices")
            addSimple("phone_registry", "phone_registry")
            addSimple("lab_catalog_overrides", "lab_catalog_overrides")
        }.onFailure {
            onResult(Result.failure(IllegalArgumentException("محتوى النسخة الاحتياطية تالف أو غير مكتمل")))
            return
        }

        val settings = parsed.optJSONObject("settings")?.let(::settingsFromJson)
        val main = Handler(Looper.getMainLooper())

        Thread({
            val result = runCatching {
                var documentsWritten = 0
                var customersWritten = 0
                var ordersWritten = 0
                var paymentsWritten = 0
                var resultDocumentsWritten = 0

                fun restoreMissingPhase(writes: List<RestoreWrite>): Int {
                    if (writes.isEmpty()) return 0
                    val missing = ArrayList<RestoreWrite>()
                    writes.forEach { write ->
                        val snapshot = Tasks.await(write.ref.get(Source.SERVER), 30, TimeUnit.SECONDS)
                        if (!snapshot.exists()) missing += write
                    }
                    if (missing.isEmpty()) return 0
                    var start = 0
                    while (start < missing.size) {
                        val end = (start + WRITE_BATCH_SIZE).coerceAtMost(missing.size)
                        val batch = db.batch()
                        for (i in start until end) {
                            val write = missing[i]
                            batch.set(write.ref, write.data)
                        }
                        Tasks.await(batch.commit(), 45, TimeUnit.SECONDS)
                        start = end
                    }
                    return missing.size
                }

                // Dependency order matters for Firestore create rules.
                customersWritten = restoreMissingPhase(customerWrites)
                documentsWritten += customersWritten
                ordersWritten = restoreMissingPhase(orderWrites)
                documentsWritten += ordersWritten
                paymentsWritten = restoreMissingPhase(paymentWrites)
                documentsWritten += paymentsWritten
                resultDocumentsWritten = restoreMissingPhase(resultWrites)
                documentsWritten += resultDocumentsWritten
                documentsWritten += restoreMissingPhase(otherWrites)

                RestoreResult(
                    settings = settings,
                    brandLogoBytes = extractBrandLogo(parsed.optJSONObject("settings")),
                    documentsWritten = documentsWritten,
                    customers = customersWritten,
                    orders = ordersWritten,
                    payments = paymentsWritten,
                    resultDocuments = resultDocumentsWritten
                )
            }
            main.post { onResult(result) }
        }, "tahalil-v135-safe-missing-only-restore").apply { isDaemon = true }.start()
    }

    private data class OrderBackup(val customerId: String, val orderId: String, val json: JSONObject)
    private data class PaymentBackup(val customerId: String, val orderId: String, val json: JSONObject)
    private data class ResultActivityBackup(val customerId: String, val id: String, val json: JSONObject)

    private fun fetchOrders(
        db: FirebaseFirestore,
        customersSnapshot: QuerySnapshot,
        callback: (Result<List<OrderBackup>>) -> Unit
    ) {
        if (customersSnapshot.isEmpty) {
            callback(Result.success(emptyList()))
            return
        }
        val remaining = AtomicInteger(customersSnapshot.size())
        val output = java.util.Collections.synchronizedList(mutableListOf<OrderBackup>())
        var failed = false
        customersSnapshot.documents.forEach { customerDoc ->
            customerDoc.reference.collection("orders").get()
                .addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { orderDoc ->
                        output += OrderBackup(
                            customerId = customerDoc.id,
                            orderId = orderDoc.id,
                            json = JSONObject().apply {
                                put("customer_id", customerDoc.id)
                                put("id", orderDoc.id)
                                put("data", jsonObjectFromMap(orderDoc.data.orEmpty()))
                            }
                        )
                    }
                    if (remaining.decrementAndGet() == 0 && !failed) callback(Result.success(output.toList()))
                }
                .addOnFailureListener { error ->
                    if (!failed) {
                        failed = true
                        callback(Result.failure(error))
                    }
                }
        }
    }

    private fun fetchPayments(
        db: FirebaseFirestore,
        orders: List<OrderBackup>,
        callback: (Result<List<PaymentBackup>>) -> Unit
    ) {
        if (orders.isEmpty()) {
            callback(Result.success(emptyList()))
            return
        }
        val remaining = AtomicInteger(orders.size)
        val output = java.util.Collections.synchronizedList(mutableListOf<PaymentBackup>())
        var failed = false
        orders.forEach { order ->
            db.collection("customers").document(order.customerId)
                .collection("orders").document(order.orderId)
                .collection("payments").get()
                .addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { paymentDoc ->
                        output += PaymentBackup(
                            customerId = order.customerId,
                            orderId = order.orderId,
                            json = JSONObject().apply {
                                put("customer_id", order.customerId)
                                put("order_id", order.orderId)
                                put("id", paymentDoc.id)
                                put("data", jsonObjectFromMap(paymentDoc.data.orEmpty()))
                            }
                        )
                    }
                    if (remaining.decrementAndGet() == 0 && !failed) callback(Result.success(output.toList()))
                }
                .addOnFailureListener { error ->
                    if (!failed) {
                        failed = true
                        callback(Result.failure(error))
                    }
                }
        }
    }

    private fun fetchResultFileActivity(
        db: FirebaseFirestore,
        customersSnapshot: QuerySnapshot,
        callback: (Result<List<ResultActivityBackup>>) -> Unit
    ) {
        if (customersSnapshot.isEmpty) {
            callback(Result.success(emptyList()))
            return
        }
        val remaining = AtomicInteger(customersSnapshot.size() * 2)
        val output = java.util.Collections.synchronizedList(mutableListOf<ResultActivityBackup>())
        var failed = false
        customersSnapshot.documents.forEach { customerDoc ->
            listOf("result_file_meta", "result_file_chunk").forEach { type ->
                customerDoc.reference.collection("activity").whereEqualTo("type", type).get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { doc ->
                            output += ResultActivityBackup(
                                customerId = customerDoc.id,
                                id = doc.id,
                                json = JSONObject().apply {
                                    put("customer_id", customerDoc.id)
                                    put("id", doc.id)
                                    put("data", jsonObjectFromMap(doc.data.orEmpty()))
                                }
                            )
                        }
                        if (remaining.decrementAndGet() == 0 && !failed) callback(Result.success(output.toList()))
                    }
                    .addOnFailureListener { error ->
                        if (!failed) {
                            failed = true
                            callback(Result.failure(error))
                        }
                    }
            }
        }
    }

    private fun fetchSimpleCollection(
        db: FirebaseFirestore,
        collection: String,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        db.collection(collection).get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(snapshot.documents.map { backupDoc(it.id, it.data.orEmpty()) }))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun backupDoc(id: String, data: Map<String, Any?>): JSONObject = JSONObject().apply {
        put("id", id)
        put("data", jsonObjectFromMap(data))
    }

    private fun addSimpleRestoreWrites(
        root: JSONObject,
        key: String,
        collection: com.google.firebase.firestore.CollectionReference,
        writes: MutableList<Pair<DocumentReference, Map<String, Any?>>>
    ) {
        val array = root.optJSONArray(key) ?: return
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = safeDocId(obj.getString("id"))
            writes += collection.document(id) to jsonObjectToFirestoreMap(obj.getJSONObject("data"))
        }
    }

    private fun commitInChunks(
        db: FirebaseFirestore,
        writes: List<Pair<DocumentReference, Map<String, Any?>>>,
        start: Int,
        callback: (Result<Unit>) -> Unit
    ) {
        if (start >= writes.size) {
            callback(Result.success(Unit))
            return
        }
        val end = (start + WRITE_BATCH_SIZE).coerceAtMost(writes.size)
        val batch = db.batch()
        for (i in start until end) {
            val (ref, data) = writes[i]
            batch.set(ref, data)
        }
        batch.commit()
            .addOnSuccessListener { commitInChunks(db, writes, end, callback) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun validBackupPassword(password: String): Boolean = password.length >= 10

    private fun encrypt(plain: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(HEADER.toByteArray(StandardCharsets.UTF_8))
        val cipherText = cipher.doFinal(plain)
        val envelope = listOf(
            HEADER,
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
        ).joinToString("\n")
        return envelope.toByteArray(StandardCharsets.UTF_8)
    }

    private fun decrypt(input: ByteArray, password: String): ByteArray {
        val parts = String(input, StandardCharsets.UTF_8).split('\n', limit = 4)
        require(parts.size == 4 && parts[0] == HEADER)
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[3], Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(HEADER.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(cipherText)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ROUNDS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun safeDocId(value: String): String {
        val clean = value.trim()
        require(clean.isNotBlank() && clean.length <= 512 && !clean.contains('/'))
        return clean
    }

    private fun settingsToJson(settings: AppSettings): JSONObject = JSONObject().apply {
        put("pdf_lab_name", settings.pdfLabName)
        put("brand_tagline", settings.brandTagline)
        put("brand_whatsapp", settings.brandWhatsApp)
        put("brand_phone", settings.brandPhone)
        put("brand_address", settings.brandAddress)
        put("pdf_show_contact", settings.pdfShowContactInfo)
        put("pdf_contact_info", settings.pdfContactInfo)
        put("show_customer_price", settings.showCustomerPrice)
        put("pdf_include_customer_price", settings.pdfIncludeCustomerPrice)
        put("pdf_show_totals", settings.pdfShowTotals)
        val logoFile = settings.brandLogoPath.trim().takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        if (logoFile != null && logoFile.isFile && logoFile.length() in 1..3_000_000) {
            put("brand_logo_base64", Base64.encodeToString(logoFile.readBytes(), Base64.NO_WRAP))
        }
    }

    private fun extractBrandLogo(settingsObject: JSONObject?): ByteArray? = runCatching {
        val encoded = settingsObject?.optString("brand_logo_base64").orEmpty()
        if (encoded.isBlank()) null else Base64.decode(encoded, Base64.NO_WRAP)
    }.getOrNull()

    private fun settingsFromJson(obj: JSONObject): AppSettings = AppSettings(
        pdfLabName = obj.optString("pdf_lab_name", "تحاليل العقاد").ifBlank { "تحاليل العقاد" },
        brandTagline = obj.optString("brand_tagline", "دليل التحاليل والأسعار").ifBlank { "دليل التحاليل والأسعار" },
        brandWhatsApp = obj.optString("brand_whatsapp", ""),
        brandPhone = obj.optString("brand_phone", ""),
        brandAddress = obj.optString("brand_address", ""),
        pdfShowContactInfo = obj.optBoolean("pdf_show_contact", false),
        pdfContactInfo = obj.optString("pdf_contact_info", ""),
        showCustomerPrice = obj.optBoolean("show_customer_price", true),
        pdfIncludeCustomerPrice = obj.optBoolean("pdf_include_customer_price", true),
        pdfShowTotals = obj.optBoolean("pdf_show_totals", true)
    )

    private fun jsonObjectFromMap(map: Map<String, Any?>): JSONObject = JSONObject().apply {
        map.forEach { (key, value) -> put(key, jsonSafeValue(value)) }
    }

    private fun jsonSafeValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is String, is Number, is Boolean -> value
        is Timestamp -> JSONObject().apply { put("__type", "timestamp"); put("millis", value.toDate().time) }
        is GeoPoint -> JSONObject().apply { put("__type", "geopoint"); put("lat", value.latitude); put("lng", value.longitude) }
        is Blob -> JSONObject().apply { put("__type", "blob"); put("base64", Base64.encodeToString(value.toBytes(), Base64.NO_WRAP)) }
        is DocumentReference -> JSONObject().apply { put("__type", "reference"); put("path", value.path) }
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (k, v) -> if (k != null) put(k.toString(), jsonSafeValue(v)) }
        }
        is Iterable<*> -> JSONArray().apply { value.forEach { put(jsonSafeValue(it)) } }
        is Array<*> -> JSONArray().apply { value.forEach { put(jsonSafeValue(it)) } }
        else -> value.toString()
    }

    private fun jsonObjectToFirestoreMap(obj: JSONObject): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = jsonToFirestoreValue(obj.get(key))
        }
        return out
    }

    private fun jsonToFirestoreValue(value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return null
        return when (value) {
            is JSONObject -> {
                when (value.optString("__type")) {
                    "timestamp" -> Timestamp(java.util.Date(value.optLong("millis", 0L)))
                    "geopoint" -> GeoPoint(value.getDouble("lat"), value.getDouble("lng"))
                    "blob" -> Blob.fromBytes(Base64.decode(value.getString("base64"), Base64.NO_WRAP))
                    // Restoring arbitrary cross-document references is intentionally omitted;
                    // this app stores operational links as string IDs, not DocumentReference fields.
                    "reference" -> value.optString("path")
                    else -> jsonObjectToFirestoreMap(value)
                }
            }
            is JSONArray -> (0 until value.length()).map { jsonToFirestoreValue(value.get(it)) }
            is Number, is String, is Boolean -> value
            else -> value.toString()
        }
    }
}
