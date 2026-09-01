package com.example.data.repository

import android.content.Context
import com.example.data.model.LabTest
import com.example.data.model.normalizeText
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import kotlin.math.abs

class LabTestRepository(private val context: Context) {

    private val catalogPrefs = context.getSharedPreferences("editable_lab_catalog_v96", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_TESTS = "custom_tests"
        private const val KEY_EDITED_TESTS = "edited_tests"
        private const val KEY_DELETED_IDS = "deleted_ids"
        private const val KEY_NEXT_ID = "next_custom_id"
        private const val CUSTOM_ID_START = 100000
    }

    @Volatile
    private var cachedTests: List<LabTest>? = null

    fun getLabTests(): List<LabTest> {
        cachedTests?.let { return it }

        return synchronized(this) {
            cachedTests?.let { return@synchronized it }

            val testsList = mutableListOf<LabTest>()
            try {
                val inputStream = context.assets.open("lab_tests_android.json")
                val jsonString = InputStreamReader(inputStream, Charsets.UTF_8).use { it.readText() }
                val jsonArray = JSONArray(jsonString)

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optInt("id")
                    val englishName = obj.optString("english_name", "")
                    val arabicName = obj.optString("arabic_name", "")
                    val marketName = obj.optString("market_name", "")
                    val searchText = obj.optString("search_text", "")

                    val customerPriceRaw = if (obj.isNull("customer_price")) null else obj.get("customer_price").toString()
                    val customerPrice = formatPrice(customerPriceRaw)

                    testsList.add(
                        LabTest(
                            id = id,
                            englishName = englishName,
                            arabicName = arabicName,
                            marketName = marketName,
                            customerPrice = customerPrice,
                            searchText = searchText
                        )
                    )
                }
            } catch (_: Exception) {
                // Do not emit dataset details or stack traces in release logs.
            }

            val merged = applyEditableCatalog(testsList)
            cachedTests = merged
            merged
        }
    }

    @Synchronized
    fun addLabTest(
        englishName: String,
        arabicName: String,
        marketName: String,
        searchText: String,
        customerPrice: String?
    ): LabTest {
        val id = nextCustomId()
        val test = buildTest(id, englishName, arabicName, marketName, searchText, customerPrice)
        val custom = readObjectArray(KEY_CUSTOM_TESTS)
        custom.put(testToJson(test))
        catalogPrefs.edit()
            .putString(KEY_CUSTOM_TESTS, custom.toString())
            .putInt(KEY_NEXT_ID, id + 1)
            .apply()
        cachedTests = null
        return test
    }

    @Synchronized
    fun updateLabTest(test: LabTest): LabTest {
        val normalized = buildTest(
            test.id, test.englishName, test.arabicName, test.marketName, test.searchText, test.customerPrice
        )
        if (test.id >= CUSTOM_ID_START) {
            val custom = readObjectArray(KEY_CUSTOM_TESTS)
            val replaced = JSONArray()
            var found = false
            for (i in 0 until custom.length()) {
                val obj = custom.optJSONObject(i) ?: continue
                if (obj.optInt("id") == test.id) {
                    replaced.put(testToJson(normalized)); found = true
                } else replaced.put(obj)
            }
            if (!found) replaced.put(testToJson(normalized))
            catalogPrefs.edit().putString(KEY_CUSTOM_TESTS, replaced.toString()).apply()
        } else {
            val edits = readObjectArray(KEY_EDITED_TESTS)
            val replaced = JSONArray()
            var found = false
            for (i in 0 until edits.length()) {
                val obj = edits.optJSONObject(i) ?: continue
                if (obj.optInt("id") == test.id) {
                    replaced.put(testToJson(normalized)); found = true
                } else replaced.put(obj)
            }
            if (!found) replaced.put(testToJson(normalized))
            catalogPrefs.edit().putString(KEY_EDITED_TESTS, replaced.toString()).apply()
        }
        cachedTests = null
        return normalized
    }

    @Synchronized
    fun applyRemoteCatalogOverride(test: LabTest, deleted: Boolean) {
        val id = test.id
        if (deleted) {
            deleteLabTest(id)
            return
        }
        val normalized = buildTest(id, test.englishName, test.arabicName, test.marketName, test.searchText, test.customerPrice)
        val deletedIds = readDeletedIds().toMutableSet().apply { remove(id) }
        val editor = catalogPrefs.edit().putString(KEY_DELETED_IDS, JSONArray(deletedIds.toList()).toString())
        if (id >= CUSTOM_ID_START) {
            val custom = readObjectArray(KEY_CUSTOM_TESTS)
            val replaced = JSONArray()
            var found = false
            for (i in 0 until custom.length()) {
                val obj = custom.optJSONObject(i) ?: continue
                if (obj.optInt("id") == id) { replaced.put(testToJson(normalized)); found = true } else replaced.put(obj)
            }
            if (!found) replaced.put(testToJson(normalized))
            editor.putString(KEY_CUSTOM_TESTS, replaced.toString())
            editor.putInt(KEY_NEXT_ID, maxOf(catalogPrefs.getInt(KEY_NEXT_ID, CUSTOM_ID_START), id + 1))
        } else {
            val edits = readObjectArray(KEY_EDITED_TESTS)
            val replaced = JSONArray()
            var found = false
            for (i in 0 until edits.length()) {
                val obj = edits.optJSONObject(i) ?: continue
                if (obj.optInt("id") == id) { replaced.put(testToJson(normalized)); found = true } else replaced.put(obj)
            }
            if (!found) replaced.put(testToJson(normalized))
            editor.putString(KEY_EDITED_TESTS, replaced.toString())
        }
        editor.apply()
        cachedTests = null
    }

    @Synchronized
    fun bulkUpdateLabTests(updates: Map<Int, LabTest>): Int {
        if (updates.isEmpty()) return 0
        updates.values.forEach { updateLabTest(it) }
        cachedTests = null
        return updates.size
    }

    @Synchronized
    fun deleteLabTest(id: Int) {
        if (id >= CUSTOM_ID_START) {
            val custom = readObjectArray(KEY_CUSTOM_TESTS)
            val kept = JSONArray()
            for (i in 0 until custom.length()) {
                val obj = custom.optJSONObject(i) ?: continue
                if (obj.optInt("id") != id) kept.put(obj)
            }
            catalogPrefs.edit().putString(KEY_CUSTOM_TESTS, kept.toString()).apply()
        } else {
            val deleted = readDeletedIds().toMutableSet().apply { add(id) }
            catalogPrefs.edit().putString(KEY_DELETED_IDS, JSONArray(deleted.toList()).toString()).apply()
        }
        cachedTests = null
    }

    private fun applyEditableCatalog(base: List<LabTest>): List<LabTest> {
        val deleted = readDeletedIds()
        val edits = mutableMapOf<Int, LabTest>()
        val editArray = readObjectArray(KEY_EDITED_TESTS)
        for (i in 0 until editArray.length()) {
            editArray.optJSONObject(i)?.let(::jsonToTest)?.let { edits[it.id] = it }
        }
        val result = base.asSequence()
            .filterNot { it.id in deleted }
            .map { edits[it.id] ?: it }
            .toMutableList()
        val custom = readObjectArray(KEY_CUSTOM_TESTS)
        for (i in 0 until custom.length()) {
            custom.optJSONObject(i)?.let(::jsonToTest)?.let { result.add(it) }
        }
        return result.distinctBy { it.id }.sortedBy { it.id }
    }

    private fun readDeletedIds(): Set<Int> {
        val raw = catalogPrefs.getString(KEY_DELETED_IDS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet { for (i in 0 until array.length()) add(array.optInt(i)) }
        }.getOrDefault(emptySet())
    }

    private fun readObjectArray(key: String): JSONArray = runCatching {
        JSONArray(catalogPrefs.getString(key, "[]") ?: "[]")
    }.getOrElse { JSONArray() }

    private fun nextCustomId(): Int {
        val saved = catalogPrefs.getInt(KEY_NEXT_ID, CUSTOM_ID_START)
        val maxExisting = getLabTests().maxOfOrNull { it.id } ?: 0
        return maxOf(saved, maxExisting + 1, CUSTOM_ID_START)
    }

    private fun buildTest(
        id: Int, englishName: String, arabicName: String, marketName: String, searchText: String, customerPrice: String?
    ): LabTest {
        val en = englishName.trim()
        val ar = arabicName.trim()
        val market = marketName.trim()
        val search = searchText.trim().ifBlank { listOf(en, ar, market).filter { it.isNotBlank() }.joinToString(" ") }
        return LabTest(id, en, ar, market, formatPrice(customerPrice), search)
    }

    private fun testToJson(test: LabTest): JSONObject = JSONObject().apply {
        put("id", test.id)
        put("english_name", test.englishName)
        put("arabic_name", test.arabicName)
        put("market_name", test.marketName)
        put("search_text", test.searchText)
        if (test.customerPrice == null) put("customer_price", JSONObject.NULL) else put("customer_price", test.customerPrice)
    }

    private fun jsonToTest(obj: JSONObject): LabTest? {
        val id = obj.optInt("id", -1)
        if (id < 0) return null
        return buildTest(
            id = id,
            englishName = obj.optString("english_name", ""),
            arabicName = obj.optString("arabic_name", ""),
            marketName = obj.optString("market_name", ""),
            searchText = obj.optString("search_text", ""),
            customerPrice = if (obj.isNull("customer_price")) null else obj.optString("customer_price", "")
        )
    }

    private fun formatPrice(priceStr: String?): String? {
        if (priceStr.isNullOrBlank() || priceStr.equals("null", ignoreCase = true)) return null
        return priceStr.trim()
    }

    private data class AliasRule(
        val keys: List<String>,
        val targets: List<String>
    )

    /**
     * Existing clinic vocabulary from V66, now also typo-tolerant in V68.
     * Short medical abbreviations (ALT/AST/HCV/HBV...) remain exact-only so
     * one abbreviation cannot accidentally resolve to a different one.
     */
    private val aliasRules = listOf(
        AliasRule(listOf("rbg", "rbs", "random blood glucose", "random blood sugar", "random glucose", "random sugar", "سكر عشوائي"), listOf("Glucose Random")),
        AliasRule(listOf("fbs", "fasting blood sugar", "fasting sugar", "سكر صائم", "سكر صايم"), listOf("Blood Glucose Fasting")),
        AliasRule(listOf("hba1c", "hb a1c", "a1c", "سكر تراكمي"), listOf("Glycated Haemoglobin (HbA1c)")),
        AliasRule(listOf("sgpt", "alt"), listOf("ALT (SGPT)")),
        AliasRule(listOf("sgot", "ast"), listOf("AST (SGOT)")),
        AliasRule(listOf("inr"), listOf("Prothrombin Time & Conc")),
        AliasRule(listOf("blood group", "blood grouping", "فصيله دم"), listOf("ABO", "Rh")),
        AliasRule(
            listOf("وظائف كبد", "انزيم كبد", "انزيمات كبد", "lft", "liver function", "liver functions"),
            listOf("ALT (SGPT)", "AST (SGOT)")
        ),
        AliasRule(
            listOf("وظائف كلي", "وظائف كليه", "kft", "kidney function", "kidney functions", "renal function", "renal functions"),
            listOf("Creatinine", "Blood Urea")
        ),
        AliasRule(listOf("hbv", "hepatitis b"), listOf("HBs Ag")),
        AliasRule(listOf("hcv", "hepatitis c"), listOf("HCV Ab")),
        AliasRule(listOf("hiv", "aids"), listOf("HIV Ab")),
        AliasRule(listOf("vit d", "vitamin d", "فيتامين د"), listOf("Vit D3 (25 Hydroxycholecalciferol)")),
        AliasRule(listOf("b12", "vit b12", "vitamin b12", "فيتامين ب12"), listOf("VIT B12 (Cyanocobalamine)")),
        AliasRule(listOf("مخزون حديد", "ferritin"), listOf("Ferritin")),
        AliasRule(listOf("lipid profile", "lipids profile", "تحليل دهون", "دهون كامله"), listOf("Lipids Profile")),
        AliasRule(listOf("وظائف غده درقيه", "thyroid function", "thyroid functions", "tft"), listOf("TSH", "Free T4")),
        // V142 common clinic shorthand / market wording.
        AliasRule(listOf("cbc", "fbc", "complete blood count", "صورة دم", "صوره دم", "صورة دم كاملة"), listOf("CBC")),
        AliasRule(listOf("crp", "c reactive protein", "سي ار بي"), listOf("CRP")),
        AliasRule(listOf("esr", "sed rate", "سرعة ترسيب", "سرعه ترسيب"), listOf("ESR")),
        AliasRule(listOf("tsh", "thyroid stimulating hormone"), listOf("TSH")),
        AliasRule(listOf("ft4", "free t4", "free thyroxine"), listOf("Free T4")),
        AliasRule(listOf("ft3", "free t3"), listOf("Free T3")),
        AliasRule(listOf("ptt", "aptt", "a ptt", "partial thromboplastin"), listOf("PTT")),
        AliasRule(listOf("d dimer", "d-dimer", "ddimer"), listOf("D-Dimer")),
        AliasRule(listOf("creat", "cr", "creatinine", "كرياتينين"), listOf("Creatinine")),
        AliasRule(listOf("urea", "bun", "blood urea", "يوريا"), listOf("Blood Urea")),
        AliasRule(listOf("uric", "uric acid", "يوريك اسيد", "حمض اليوريك"), listOf("Uric Acid")),
        AliasRule(listOf("ua"), listOf("Uric Acid", "Urine Analysis")),
        AliasRule(listOf("alp", "alk phos", "alkaline phosphatase"), listOf("Alkaline Phosphatase")),
        AliasRule(listOf("ggt", "gamma gt", "gamma glutamyl transferase"), listOf("GGT")),
        AliasRule(listOf("t bil", "tbil", "total bilirubin", "bilirubin total"), listOf("Bilirubin (Total)")),
        AliasRule(listOf("d bil", "dbil", "direct bilirubin", "bilirubin direct"), listOf("Bilirubin (Direct)")),
        AliasRule(listOf("chol", "total cholesterol", "كوليسترول"), listOf("Cholesterol")),
        AliasRule(listOf("hdl", "hdl c", "good cholesterol"), listOf("HDL")),
        AliasRule(listOf("ldl", "ldl c", "bad cholesterol"), listOf("LDL Cholesterol")),
        AliasRule(listOf("tg", "triglycerides", "triglyceride"), listOf("Triglyceride")),
        AliasRule(listOf("na", "sodium", "صوديوم"), listOf("Sodium (Na)")),
        AliasRule(listOf("k", "potassium", "بوتاسيوم"), listOf("Potassium (K)")),
        AliasRule(listOf("mg", "magnesium", "ماغنسيوم", "مغنيسيوم"), listOf("Magnesium")),
        AliasRule(listOf("ca", "total calcium", "calcium total", "كالسيوم كلي"), listOf("Calcium (Total)")),
        AliasRule(listOf("ca2", "ca2+", "ionized calcium", "calcium ionized", "كالسيوم متأين"), listOf("Calcium (Ionized)")),
        AliasRule(listOf("amylase", "amy"), listOf("Amylase")),
        AliasRule(listOf("lipase", "lip"), listOf("Lipase")),
        AliasRule(listOf("psa", "total psa", "psa total"), listOf("PSA (Total)")),
        AliasRule(listOf("free psa", "fpsa", "psa free"), listOf("PSA (Free)")),
        AliasRule(listOf("bhcg", "b hcg", "beta hcg", "pregnancy hormone", "هرمون الحمل"), listOf("Beta HCG Quantitative")),
        AliasRule(listOf("urine", "urinalysis", "urine analysis", "تحليل بول", "بول كامل"), listOf("Urine Analysis")),

        // V142 semantic organ/system search: typing an organ or a natural phrase
        // such as "عايز اطمن على القلب" returns tests medically associated
        // with that organ even when the organ word is absent from the test name.
        AliasRule(
            listOf("القلب", "قلب", "عضلة القلب", "عضله القلب", "انزيمات القلب", "إنزيمات القلب", "تحاليل القلب", "فحص القلب", "اطمن على القلب", "اطمئن على القلب", "عايز اطمن على القلب", "عاوز اطمن على القلب", "heart", "cardiac", "cardiology"),
            listOf("CK-MB", "Troponin I (Qualitative)", "Troponin I (Quantitative)", "Homocysteine", "CRP", "LDH (Lactate Dehydrogenase)", "Cholesterol", "HDL", "LDL Cholesterol", "Triglyceride")
        ),
        AliasRule(
            listOf("الكبد", "كبد", "وظائف الكبد", "انزيمات الكبد", "إنزيمات الكبد", "تحاليل الكبد", "اطمن على الكبد", "اطمئن على الكبد", "liver", "hepatic"),
            listOf("ALT (SGPT)", "AST (SGOT)", "GGT", "Alkaline Phosphatase", "Bilirubin (Total)", "Bilirubin (Direct)", "Bilirubin (Indirect)", "Albumin", "Total Protein", "Prothrombin Time & Conc", "HBs Ag", "HCV Ab", "AFP")
        ),
        AliasRule(
            listOf("الكلى", "الكليتين", "كلى", "كلية", "وظائف الكلى", "تحاليل الكلى", "اطمن على الكلى", "اطمئن على الكلى", "kidney", "kidneys", "renal"),
            listOf("Creatinine", "Blood Urea", "BUN", "eGFR", "Creatinine Clearance", "Albumin/Creatinine Ratio", "Micro Albumin Random", "Micro Albumin in 24 hrs", "Protein in Urine Random", "Sodium (Na)", "Potassium (K)", "Phosphorous", "Uric Acid")
        ),
        AliasRule(
            listOf("الغدة الدرقية", "الغده الدرقيه", "الدرقية", "درقية", "وظائف الغدة الدرقية", "اطمن على الغدة الدرقية", "thyroid"),
            listOf("TSH", "T3", "T4", "Free T3", "Free T4", "Anti Thyroid Ab", "Thyroid Ab (Anti-Microsomal / Peroxidase Ab)")
        ),
        AliasRule(
            listOf("البنكرياس", "بنكرياس", "وظائف البنكرياس", "تحاليل البنكرياس", "اطمن على البنكرياس", "pancreas", "pancreatic"),
            listOf("Amylase", "Amylase in urine", "Lipase", "Blood Glucose Fasting", "Glucose Random", "Blood Glucose 2 hrs P.P", "Glycated Haemoglobin (HbA1c)", "Insulin Level (Fasting)", "Insulin (Random)", "C-Peptide (Fasting)", "C-Peptide (Random)", "Homa-IR", "Anti Islet Cells Antibody")
        ),
        AliasRule(
            listOf("البروستاتا", "بروستاتا", "تحاليل البروستاتا", "اطمن على البروستاتا", "prostate"),
            listOf("PSA (Total)", "PSA (Free)")
        ),
        AliasRule(
            listOf("المبيض", "المبايض", "مبيض", "مبايض", "مخزون المبيض", "تحاليل المبايض", "اطمن على المبايض", "ovary", "ovaries", "ovarian"),
            listOf("AMH", "CA 125", "FSH", "LH", "Estradiol (E2)", "Progesterone")
        ),
        AliasRule(
            listOf("الرحم", "رحم", "عنق الرحم", "تحاليل الرحم", "اطمن على الرحم", "uterus", "uterine", "cervix", "cervical"),
            listOf("Beta HCG Quantitative", "Progesterone", "Estradiol (E2)", "FSH", "LH", "CA 125", "Cervical Swab C/S")
        ),
        AliasRule(
            listOf("الغدة النخامية", "الغده النخاميه", "النخامية", "النخاميه", "نخامية", "pituitary"),
            listOf("ACTH AM", "ACTH PM", "FSH", "LH", "PRL", "Growth Hormone (Basal)", "Insulin Like Growth Factor-1 (IGF-1)", "TSH")
        ),
        AliasRule(
            listOf("الغدة الكظرية", "الغده الكظريه", "الكظرية", "الكظريه", "كظرية", "adrenal"),
            listOf("Cortisol AM", "Cortisol PM", "Cortisol Random", "ACTH AM", "ACTH PM", "Aldosterone", "Aldosterone / Renin Ratio", "DHEA", "DHEA-S")
        ),
        AliasRule(
            listOf("العظام", "عظام", "صحة العظام", "تحاليل العظام", "اطمن على العظام", "bone", "bones"),
            listOf("Vit D3 (25 Hydroxycholecalciferol)", "Calcium (Total)", "Calcium (Ionized)", "Phosphorous", "PTH", "Alkaline Phosphatase")
        ),
        AliasRule(
            listOf("العضلات", "عضلات", "عضلة", "عضله", "تحاليل العضلات", "muscle", "muscles"),
            listOf("CK Total", "LDH (Lactate Dehydrogenase)", "AST (SGOT)")
        ),
        AliasRule(
            listOf("المعدة", "المعده", "معدة", "معده", "تحاليل المعدة", "جرثومة المعدة", "اطمن على المعدة", "stomach", "gastric"),
            listOf("H. Pylori IgG", "H. Pylori IgM", "H. Pylori Ag (in stool)", "Gastrin Level")
        ),
        AliasRule(
            listOf("القولون", "قولون", "الأمعاء", "الامعاء", "أمعاء", "امعاء", "تحاليل القولون", "اطمن على القولون", "colon", "bowel", "intestine", "intestinal"),
            listOf("Calprotectin in Stool", "Occult Blood in Stool", "CEA", "Stool Analysis", "Stool C/S")
        ),
        AliasRule(
            listOf("الرئة", "الرئه", "الرئتين", "رئة", "رئه", "التنفس", "الجهاز التنفسي", "تحاليل الرئة", "اطمن على الرئة", "lung", "lungs", "pulmonary", "respiratory"),
            listOf("D-Dimer", "Alpha 1 Antitrypsin (Serum)", "Sputum C/S", "Acid Fast Bacilli (Sputum)", "T.B Gold (QuantiFERON)", "T.B DNA by PCR", "IgE Specific (Inhalant Allergens) RAST")
        ),
        AliasRule(
            listOf("الثدي", "ثدي", "تحاليل الثدي", "اطمن على الثدي", "breast"),
            listOf("CA 15.3", "PRL")
        ),
        AliasRule(
            listOf("الخصية", "الخصيه", "الخصيتين", "خصية", "خصيه", "تحاليل الخصية", "اطمن على الخصية", "testis", "testicle", "testicular", "male fertility"),
            listOf("Testosterone Total", "Testosterone Free", "FSH", "LH", "Semen Analysis", "Semen Analysis with CASA", "Semen C/S", "Fructose In Semen", "Anti Sperm Ab (Semen)")
        ),
        AliasRule(listOf("stool", "stool analysis", "تحليل براز"), listOf("Stool Analysis"))
    )

    private fun aliasTargets(normQuery: String): List<String>? {
        val q = removeArabicArticles(normQuery)
        if (q.isBlank()) return null

        // Exact aliases always win.
        aliasRules.firstOrNull { rule ->
            rule.keys.any { alias -> removeArabicArticles(normalizeText(alias)) == q }
        }?.let { return it.targets }

        // V68: tolerate small spelling mistakes in long aliases as well.
        // Example: "وظايف كلي" still resolves to kidney-function tests.
        val best = aliasRules.mapNotNull { rule ->
            val score = rule.keys.mapNotNull { alias ->
                fuzzyPhraseDistance(q, removeArabicArticles(normalizeText(alias)))
            }.minOrNull()
            score?.let { rule to it }
        }.minByOrNull { it.second }

        return best?.first?.targets
    }

    private fun removeArabicArticles(value: String): String {
        if (value.isBlank()) return value
        return value.split(" ")
            .joinToString(" ") { token ->
                if (token.startsWith("ال") && token.length > 3) token.removePrefix("ال") else token
            }
            .trim()
    }

    /**
     * V69: compact Latin medical names for exact comparison only.
     * This makes spacing/punctuation irrelevant for abbreviations such as:
     * HBsAg == HBs Ag == HBs-Ag, without enabling broad fuzzy matching
     * for short medical abbreviations.
     */
    private fun compactLatinKey(normalizedValue: String): String? {
        if (normalizedValue.isBlank()) return null
        val isLatinMedical = normalizedValue.all { ch ->
            ch == ' ' || ch in 'a'..'z' || ch.isDigit()
        }
        if (!isLatinMedical) return null
        return normalizedValue.filter { it in 'a'..'z' || it.isDigit() }
            .takeIf { it.length >= 3 }
    }

    /**
     * V94: exact token matching independent of word order.
     * This is deliberately not fuzzy: every query token must exist as a whole token
     * in the candidate field. It supports writing variants such as
     * "Random Glucose" vs "Glucose Random" without creating false short-abbreviation matches.
     */
    private fun containsAllQueryTokens(query: String, candidate: String): Boolean {
        val qTokens = query.split(" ").filter { it.isNotBlank() }
        if (qTokens.size < 2) return false
        val cTokens = candidate.split(" ").filter { it.isNotBlank() }.toSet()
        return qTokens.all { it in cTokens }
    }

    private fun resolveExactCatalogueNames(names: List<String>, allTests: List<LabTest>): List<LabTest> {
        val wanted = names.map(::normalizeText)
        return wanted.mapNotNull { target ->
            allTests.firstOrNull { test ->
                test.normEnglish == target || test.normMarket == target || test.normArabic == target
            }
        }.distinctBy { it.id }
    }

    private fun isArabicToken(token: String): Boolean =
        token.any { it in '\u0600'..'\u06FF' }

    /**
     * Conservative typo tolerance:
     * - Arabic 3-6 letters: one edit.
     * - Arabic 7-10 letters: two edits.
     * - Very short Latin medical abbreviations stay exact-only.
     */
    private fun maxFuzzyDistance(token: String): Int {
        if (token.isBlank()) return 0
        return if (isArabicToken(token)) {
            when {
                token.length <= 2 -> 0
                token.length <= 6 -> 1
                token.length <= 10 -> 2
                else -> 3
            }
        } else {
            when {
                token.length <= 4 -> 0
                token.length <= 7 -> 1
                token.length <= 11 -> 2
                else -> 3
            }
        }
    }

    /**
     * Arabic token variants used only for search comparison. We do not change
     * the stored catalogue text. Common regular plural suffixes are removed so
     * "فيروسات" and "فيرسات" can still reach "فيروس".
     *
     * Candidate tokens may also lose a common attached preposition such as the
     * leading ل in "لفيروس". Query tokens do not get this aggressive prefix
     * stripping, which keeps false positives down.
     */
    private fun tokenVariants(token: String, allowAttachedPrefix: Boolean): List<String> {
        if (token.isBlank()) return emptyList()

        val variants = linkedSetOf(token)
        var changed = true
        while (changed && variants.size < 12) {
            changed = false
            val snapshot = variants.toList()
            for (value in snapshot) {
                if (value.startsWith("ال") && value.length > 4) {
                    changed = variants.add(value.removePrefix("ال")) || changed
                }

                if (allowAttachedPrefix && value.length >= 6 && value.first() in listOf('ل', 'ب', 'و', 'ك')) {
                    changed = variants.add(value.drop(1)) || changed
                }

                for (suffix in listOf("ات", "ون", "ين")) {
                    if (value.endsWith(suffix) && value.length - suffix.length >= 3) {
                        changed = variants.add(value.dropLast(suffix.length)) || changed
                    }
                }
            }
        }
        return variants.toList()
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun fuzzyTokenDistance(queryToken: String, candidateToken: String): Int? {
        var best: Int? = null

        for (queryVariant in tokenVariants(queryToken, allowAttachedPrefix = false)) {
            val maxDistance = maxFuzzyDistance(queryVariant)
            for (candidateVariant in tokenVariants(candidateToken, allowAttachedPrefix = true)) {
                if (queryVariant == candidateVariant) return 0
                if (maxDistance == 0) continue
                if (abs(queryVariant.length - candidateVariant.length) > maxDistance) continue

                val distance = editDistance(queryVariant, candidateVariant)
                if (distance <= maxDistance && (best == null || distance < best)) {
                    best = distance
                }
            }
        }
        return best
    }

    private fun fuzzyPhraseDistance(queryPhrase: String, candidatePhrase: String): Int? {
        val queryTokens = queryPhrase.split(" ").filter { it.isNotBlank() }
        val candidateTokens = candidatePhrase.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty() || queryTokens.size != candidateTokens.size) return null

        var total = 0
        for (index in queryTokens.indices) {
            val distance = fuzzyTokenDistance(queryTokens[index], candidateTokens[index]) ?: return null
            total += distance
        }
        return total
    }

    private fun fuzzyCatalogueDistance(queryPhrase: String, candidateFields: List<String>): Int? {
        val queryTokens = queryPhrase.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return null

        val candidateTokens = linkedSetOf<String>()
        candidateFields.forEach { field ->
            removeArabicArticles(field)
                .split(" ")
                .filterTo(candidateTokens) { it.isNotBlank() }
        }
        if (candidateTokens.isEmpty()) return null

        var totalDistance = 0
        for (queryToken in queryTokens) {
            val bestDistance = candidateTokens.mapNotNull { candidateToken ->
                fuzzyTokenDistance(queryToken, candidateToken)
            }.minOrNull() ?: return null
            totalDistance += bestDistance
        }
        return totalDistance
    }


    /** V116: remove conversational filler while keeping medical words/abbreviations. */
    private val naturalSearchStopWords = setOf(
        "انا", "اني", "احنا", "عايز", "عاوز", "عايزه", "عاوزه", "محتاج", "محتاجه",
        "ممكن", "اعمل", "اعمللي", "نعمل", "تحليل", "تحاليل", "فحص", "فحوصات",
        "اطمن", "اطمئن", "اطمان", "عايزاطمن", "على", "علي", "عن", "من", "في", "الى", "الي",
        "لو", "مثلا", "مثال", "ايه", "اي", "ايهالتحاليل", "اللي", "المطلوبه", "المطلوبة",
        "please", "want", "check", "test", "tests", "for", "the", "a", "an", "my"
    )

    private fun meaningfulNaturalQuery(normalized: String): String {
        if (normalized.isBlank()) return normalized
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val kept = tokens.filterNot { it in naturalSearchStopWords }
        return kept.joinToString(" ").ifBlank { normalized }
    }

    /**
     * V116 unified wide smart picker search.
     * - Blank/one-character input keeps the full catalogue visible.
     * - Filtering starts from two characters and never auto-selects a test.
     * - Arabic/English/market/search-text, abbreviations and known aliases are searched together.
     * - Conversational filler is ignored, so "انا عايز اطمن على الكبد" still resolves to liver-related tests.
     * - Natural-language health intents are suggestions only; the user always chooses manually.
     */
    fun smartSearchTests(query: String): List<LabTest> {
        val allTests = getLabTests()
        val trimmed = query.trim()
        val normalized = normalizeText(trimmed)

        // Keep the dropdown useful as a catalogue browser until at least two characters are typed.
        if (normalized.length < 2) return allTests

        val meaningfulNormalized = meaningfulNaturalQuery(normalized)
        val normalizedCandidates = linkedSetOf(normalized, meaningfulNormalized)
            .map(::removeArabicArticles)
            .filter { it.isNotBlank() }
            .distinct()

        fun exactByEnglish(vararg names: String): List<LabTest> {
            val targets = names.map(::normalizeText).toSet()
            return allTests.filter { test ->
                test.normEnglish in targets || test.normMarket in targets || test.normArabic in targets
            }
        }

        fun hasAny(vararg keys: String): Boolean {
            return normalizedCandidates.any { q ->
                keys.any { key ->
                    val k = removeArabicArticles(normalizeText(key))
                    k.isNotBlank() && (q.contains(k) || k.contains(q))
                }
            }
        }

        val intentSuggestions = when {
            hasAny("اطمن على الكبد", "اطمئن على الكبد", "الكبد", "كبد", "liver") -> exactByEnglish(
                "ALT (SGPT)", "AST (SGOT)", "Bilirubin (Total)", "Bilirubin (Direct)", "Albumin", "Alkaline Phosphatase", "GGT"
            )
            hasAny("اطمن على الكلى", "اطمئن على الكلى", "الكلى", "الكلي", "كلى", "كلي", "kidney", "renal") -> exactByEnglish(
                "Creatinine", "Blood Urea", "Uric Acid"
            )
            hasAny("اطمن على السكر", "اطمئن على السكر", "السكر", "سكر", "diabetes", "glucose") -> exactByEnglish(
                "Blood Glucose Fasting", "Glucose Random", "Glycated Haemoglobin (HbA1c)"
            )
            hasAny("الغدة الدرقية", "الغده الدرقيه", "غدة درقية", "غده درقيه", "thyroid") -> exactByEnglish(
                "TSH", "Free T4", "Free T3"
            )
            hasAny("انيميا", "أنيميا", "فقر دم", "anemia", "anaemia") -> exactByEnglish(
                "CBC", "Ferritin", "Iron", "TIBC (Total Iron Binding Capacity)", "VIT B12 (Cyanocobalamine)"
            )
            hasAny("مخزون الحديد", "الحديد", "حديد", "iron") -> exactByEnglish(
                "Ferritin", "Iron", "TIBC (Total Iron Binding Capacity)"
            )
            hasAny("تساقط شعر", "سقوط شعر", "الشعر", "hair loss", "hairfall") -> exactByEnglish(
                "CBC", "Ferritin", "TSH", "Vit D3 (25 Hydroxycholecalciferol)", "VIT B12 (Cyanocobalamine)"
            )
            hasAny("فيتامين", "فيتامينات", "vitamin", "vitamins") -> exactByEnglish(
                "Vit D3 (25 Hydroxycholecalciferol)", "VIT B12 (Cyanocobalamine)"
            )
            hasAny("التهاب", "التهابات", "inflammation", "inflammatory") -> exactByEnglish(
                "CBC", "CRP", "ESR"
            )
            hasAny("املاح", "أملاح", "معادن", "electrolytes", "minerals") -> exactByEnglish(
                "Sodium (Na)", "Potassium (K)", "Magnesium", "Calcium (Total)", "Calcium (Ionized)"
            )
            hasAny("البنكرياس", "بنكرياس", "pancreas", "pancreatic") -> exactByEnglish(
                "Amylase", "Lipase"
            )
            hasAny("بول", "urine", "urinalysis") -> exactByEnglish(
                "Urine Analysis"
            )
            hasAny("براز", "stool") -> exactByEnglish(
                "Stool Analysis"
            )
            hasAny("الدهون", "دهون", "كوليسترول", "cholesterol", "lipid") -> exactByEnglish(
                "Lipids Profile", "Cholesterol", "HDL", "LDL Cholesterol", "Triglyceride"
            )
            hasAny("سيولة", "تجلط", "coagulation", "clotting") -> exactByEnglish(
                "Prothrombin Time & Conc", "PTT", "D-Dimer"
            )
            hasAny("البروستاتا", "بروستاتا", "prostate") -> exactByEnglish(
                "PSA (Total)", "PSA (Free)"
            )
            hasAny("الحمل", "حمل", "pregnancy") -> exactByEnglish(
                "Beta HCG Quantitative"
            )
            else -> emptyList()
        }

        data class SmartScore(val test: LabTest, val score: Int)

        // Established aliases/fuzzy rules are evaluated against both the literal sentence and
        // the medically meaningful remainder. This keeps CBC/ALT/TSH shortcuts strong even in a sentence.
        val establishedFallbackIds = buildSet {
            addAll(searchTests(trimmed).map { it.id })
            if (meaningfulNormalized != normalized) {
                addAll(searchTests(meaningfulNormalized).map { it.id })
            }
        }

        fun lexicalScore(test: LabTest, q: String): Int? {
            val fields = listOf(test.normEnglish, test.normArabic, test.normMarket, test.normSearch)
                .map(::removeArabicArticles)
            val fieldTokens = fields.flatMap { it.split(" ") }.filter { it.isNotBlank() }
            val qTokens = q.split(" ").filter { it.isNotBlank() }

            return when {
                fields.any { it == q } -> 0
                fields.any { it.startsWith(q) } -> 1
                fieldTokens.any { it == q || it.startsWith(q) } -> 2
                fields.any { it.contains(q) } -> 3
                qTokens.size > 1 && qTokens.all { qt ->
                    fieldTokens.any { token -> token == qt || token.startsWith(qt) || token.contains(qt) }
                } -> 4
                q.length >= 3 && fieldTokens.any { token -> token.contains(q) } -> 5
                else -> null
            }
        }

        val lexical = allTests.mapNotNull { test ->
            val best = normalizedCandidates.mapNotNull { q -> lexicalScore(test, q) }.minOrNull()
            val score = best ?: if (test.id in establishedFallbackIds) 8 else return@mapNotNull null
            SmartScore(test, score)
        }.sortedWith(
            compareBy<SmartScore> { it.score }
                .thenBy { it.test.englishName.lowercase() }
                .thenBy { it.test.id }
        ).map { it.test }

        // Intent suggestions appear first, but no result is ever auto-selected.
        return (intentSuggestions + lexical).distinctBy { it.id }
    }

    fun searchTests(query: String): List<LabTest> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val normQuery = normalizeText(trimmedQuery)
        if (normQuery.isEmpty()) return emptyList()
        val compactQuery = compactLatinKey(normQuery)

        val allTests = getLabTests()

        // Exact and typo-tolerant clinic aliases win before catalogue matching.
        aliasTargets(normQuery)?.let { targetNames ->
            val resolved = resolveExactCatalogueNames(targetNames, allTests)
            if (resolved.isNotEmpty()) return resolved
        }

        val queryWithoutArticles = removeArabicArticles(normQuery)

        data class ScoredTest(val test: LabTest, val rankScore: Int)
        val matchedTests = mutableListOf<ScoredTest>()

        for (test in allTests) {
            val normEnglish = test.normEnglish
            val normArabic = test.normArabic
            val normMarket = test.normMarket
            val normSearch = test.normSearch

            val articleFreeArabic = removeArabicArticles(normArabic)
            val articleFreeMarket = removeArabicArticles(normMarket)
            val articleFreeSearch = removeArabicArticles(normSearch)

            val compactExactMatch = compactQuery != null && (
                compactLatinKey(normEnglish) == compactQuery ||
                    compactLatinKey(normMarket) == compactQuery
                )

            val tokenOrderMatch = containsAllQueryTokens(normQuery, normEnglish) ||
                containsAllQueryTokens(normQuery, normArabic) ||
                containsAllQueryTokens(normQuery, normMarket) ||
                containsAllQueryTokens(normQuery, normSearch)

            // V94: short Latin medical abbreviations must match a whole token or
            // an exact compact catalogue name. This prevents ICT from matching
            // the letters inside "picture", while RBG/RBS/CRP/ESR still work.
            val shortLatinExactOnly = normQuery.length <= 5 &&
                normQuery.none { it == ' ' } &&
                normQuery.all { it in 'a'..'z' || it.isDigit() }
            val wholeTokenMatch = listOf(normEnglish, normArabic, normMarket, normSearch).any { field ->
                field.split(" ").any { token -> token == normQuery }
            }

            val directMatch = compactExactMatch ||
                if (shortLatinExactOnly) {
                    wholeTokenMatch
                } else {
                    normEnglish.contains(normQuery) ||
                        normArabic.contains(normQuery) ||
                        normMarket.contains(normQuery) ||
                        normSearch.contains(normQuery) ||
                        tokenOrderMatch
                }

            val articleTolerantMatch = !shortLatinExactOnly && queryWithoutArticles.isNotBlank() && (
                articleFreeArabic.contains(queryWithoutArticles) ||
                    articleFreeMarket.contains(queryWithoutArticles) ||
                    articleFreeSearch.contains(queryWithoutArticles)
                )

            val rankScore: Int = if (directMatch || articleTolerantMatch) {
                when {
                    normEnglish == normQuery -> 1
                    normArabic == normQuery -> 2
                    normMarket == normQuery -> 3
                    compactExactMatch -> 4
                    articleFreeArabic == queryWithoutArticles || articleFreeMarket == queryWithoutArticles -> 5
                    normEnglish.split(" ").any { it == normQuery } ||
                        normArabic.split(" ").any { it == normQuery } ||
                        normMarket.split(" ").any { it == normQuery } -> 6
                    normEnglish.startsWith(normQuery) || normArabic.startsWith(normQuery) -> 7
                    normMarket.startsWith(normQuery) -> 8
                    normEnglish.contains(normQuery) || normArabic.contains(normQuery) -> 9
                    articleTolerantMatch -> 10
                    tokenOrderMatch -> 11
                    else -> 12
                }
            } else {
                val fuzzyDistance = fuzzyCatalogueDistance(
                    queryWithoutArticles,
                    listOf(normEnglish, normArabic, normMarket, normSearch)
                ) ?: continue

                // Keep fuzzy matches below all exact/substring matches.
                20 + fuzzyDistance
            }

            matchedTests.add(ScoredTest(test, rankScore))
        }

        if (matchedTests.isEmpty()) return emptyList()

        // If any exact/substring result exists, do not pollute it with fuzzy guesses.
        val exactMatches = matchedTests.filter { it.rankScore < 20 }
        val finalMatches = if (exactMatches.isNotEmpty()) {
            exactMatches
        } else {
            // For typo-only searches keep only the nearest score band. This prevents
            // a visually similar but unrelated word from appearing far below the
            // intended results (for example فيروس/فيروسات vs unrelated catalogue words).
            val bestFuzzyScore = matchedTests.minOf { it.rankScore }
            matchedTests.filter { it.rankScore <= bestFuzzyScore + 1 }
        }

        return finalMatches
            .sortedWith(compareBy<ScoredTest> { it.rankScore }.thenBy { it.test.id })
            .map { it.test }
    }
}
