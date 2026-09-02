package com.example.util

import java.util.Locale

data class NonLabMedicalInfo(
    val query: String,
    val categoryAr: String,
    val categoryEn: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val confidence: Confidence
) {
    enum class Confidence { CONFIRMED, LIKELY, UNKNOWN }
}

/**
 * V87: safe fallback for terms that do not exist in the laboratory catalogue.
 *
 * The app never invents a lab price for an unmatched term. Known non-laboratory
 * investigations are classified locally/offline. Unknown terms are clearly marked
 * as unclassified and can be searched externally only after an explicit warning.
 */
object NonLabMedicalLookup {

    fun classify(rawQuery: String): NonLabMedicalInfo {
        val query = rawQuery.trim().replace(Regex("\\s+"), " ")
        val key = normalize(query)

        // The exact type that motivated V87.
        if ((key.contains("pxr") || key.contains("p x r") || key.contains("plain x ray") || key.contains("plain xray")) &&
            (key.contains("urinary") || key.contains("kub") || key.contains("kidney") || key.contains("مسالك"))
        ) {
            return info(
                query,
                "أشعة", "Imaging",
                "أشعة عادية على المسالك البولية (KUB / Plain X-ray)",
                "Plain X-ray of the urinary tract (KUB)",
                "أشعة عادية على منطقة الكلى والحالبين والمثانة. قد يطلبها الطبيب لتقييم بعض الحصوات أو التكلسات ومتابعة المسالك البولية، وهي ليست تحليلا معمليا.",
                "A plain X-ray covering the kidneys, ureters and bladder. It may be requested to assess some stones or calcifications and is not a laboratory test.",
                NonLabMedicalInfo.Confidence.CONFIRMED
            )
        }

        if (containsAny(key, "x ray", "xray", "radiograph", "pxr", "p x r", "اشعه عاديه", "اشعة عادية", "اشعه")) {
            return info(
                query, "أشعة", "Imaging",
                "فحص بالأشعة السينية", "X-ray examination",
                "هذا المصطلح يبدو فحص أشعة وليس تحليلا معمليا. الأشعة السينية تستخدم لإظهار تراكيب داخل الجسم حسب المنطقة المطلوبة.",
                "This appears to be an X-ray examination rather than a laboratory test. X-rays are used to visualize internal structures depending on the requested body area.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "ct scan", "computed tomography", "اشعه مقطعيه", "اشعة مقطعية", "مقطعيه") || containsWord(key, "ct")) {
            return info(
                query, "أشعة", "Imaging",
                "أشعة مقطعية CT", "CT scan",
                "يبدو أنه فحص أشعة مقطعية وليس تحليلا معمليا. ينتج صورا مقطعية تفصيلية للمنطقة التي يحددها الطبيب.",
                "This appears to be a CT scan rather than a laboratory test. CT creates detailed cross-sectional images of the requested body region.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "mri", "magnetic resonance", "رنين", "رنين مغناطيسي")) {
            return info(
                query, "أشعة", "Imaging",
                "رنين مغناطيسي MRI", "MRI",
                "يبدو أنه فحص رنين مغناطيسي وليس تحليلا معمليا. يستخدم المجال المغناطيسي لإنتاج صور تفصيلية للأنسجة والأعضاء.",
                "This appears to be an MRI rather than a laboratory test. MRI uses magnetic fields to create detailed images of tissues and organs.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "ultrasound", "sonar", "سونار", "ultrasonography")) {
            return info(
                query, "أشعة", "Imaging",
                "سونار / موجات فوق صوتية", "Ultrasound",
                "يبدو أنه فحص بالموجات فوق الصوتية وليس تحليلا معمليا. يستخدم موجات صوتية لتصوير أعضاء وأنسجة داخل الجسم.",
                "This appears to be an ultrasound rather than a laboratory test. Ultrasound uses sound waves to image organs and tissues.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "doppler", "دوبلر")) {
            return info(
                query, "أشعة", "Imaging",
                "دوبلر", "Doppler ultrasound",
                "يبدو أنه فحص دوبلر وليس تحليلا معمليا. يستخدم عادة لتقييم تدفق الدم في الأوعية الدموية.",
                "This appears to be a Doppler ultrasound rather than a laboratory test. It is commonly used to assess blood flow in vessels.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "ecg", "ekg", "electrocardiogram", "رسم قلب", "تخطيط قلب")) {
            return info(
                query, "فحص تشخيصي", "Diagnostic test",
                "رسم قلب ECG", "ECG / EKG",
                "يبدو أنه رسم كهربائي للقلب وليس تحليلا معمليا. يسجل النشاط الكهربائي للقلب لفترة قصيرة.",
                "This appears to be an electrocardiogram rather than a laboratory test. It records the heart's electrical activity.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "echocardiography", "echocardiogram", "echo heart", "echo", "ايكو", "إيكو")) {
            return info(
                query, "فحص تشخيصي", "Diagnostic test",
                "إيكو على القلب", "Echocardiography",
                "يبدو أنه فحص إيكو للقلب وليس تحليلا معمليا. يستخدم الموجات فوق الصوتية لتقييم بنية القلب وحركته.",
                "This appears to be an echocardiogram rather than a laboratory test. It uses ultrasound to assess heart structure and motion.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "endoscopy", "gastroscopy", "colonoscopy", "منظار")) {
            return info(
                query, "إجراء تشخيصي", "Diagnostic procedure",
                "منظار", "Endoscopy",
                "يبدو أنه إجراء منظار وليس تحليلا معمليا. يستخدم الطبيب منظارا لرؤية جزء من الجسم أو الجهاز الهضمي حسب النوع المكتوب.",
                "This appears to be an endoscopic procedure rather than a laboratory test. An endoscope is used to directly view the requested organ or tract.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (containsAny(key, "biopsy", "خزعه", "خزعة")) {
            return info(
                query, "إجراء / باثولوجي", "Procedure / Pathology",
                "خزعة", "Biopsy",
                "الخزعة إجراء لأخذ عينة نسيج لفحصها. إذا كان المقصود فحص العينة داخل المعمل فله اسم باثولوجي منفصل، لكن كلمة خزعة نفسها ليست تحليل دم أو بول.",
                "A biopsy is a procedure to obtain tissue for examination. The specimen may later undergo a pathology test, but the biopsy itself is not a blood or urine laboratory test.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        if (looksLikeMedication(key)) {
            return info(
                query, "دواء / وصفة", "Medication / prescription",
                "يبدو أنه اسم دواء أو وصفة", "Possible medication or prescription item",
                "هذا المصطلح لا يطابق تحليلا معمليا، وشكله أقرب لاسم دواء أو جرعة. راجع الاسم المكتوب قبل البحث الخارجي.",
                "This does not match a laboratory test and looks more like a medication or dose. Verify the written name before searching externally.",
                NonLabMedicalInfo.Confidence.LIKELY
            )
        }

        return info(
            query, "غير محدد", "Unclassified",
            "غير موجود ضمن قائمة التحاليل", "Not found in the laboratory catalogue",
            "لم يتم العثور على هذا المصطلح كتحليل معملي، ولا يمكن تحديد نوعه بأمان من الاسم وحده. قد يكون أشعة أو فحصا أو إجراء أو دواء أو كتابة غير واضحة. يمكنك البحث عنه خارج التطبيق بعد التأكيد.",
            "This term was not found as a laboratory test and cannot be safely classified from the name alone. It may be imaging, another examination, a procedure, medication, or unclear writing. You can search externally after confirming.",
            NonLabMedicalInfo.Confidence.UNKNOWN
        )
    }

    /**
     * Pull only recognizable non-laboratory investigation lines from OCR/AI text.
     * This intentionally ignores unknown lines to avoid treating patient names,
     * diagnoses, addresses, etc. as medical requests.
     */
    fun extractKnownNonLabQueries(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText.lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.length in 2..120 }
            .map { line -> line to classify(line) }
            .filter { (_, result) -> result.confidence != NonLabMedicalInfo.Confidence.UNKNOWN }
            .map { (line, _) -> line }
            .distinctBy { normalize(it) }
            .take(20)
            .toList()
    }

    private fun info(
        query: String,
        categoryAr: String,
        categoryEn: String,
        titleAr: String,
        titleEn: String,
        descriptionAr: String,
        descriptionEn: String,
        confidence: NonLabMedicalInfo.Confidence
    ) = NonLabMedicalInfo(
        query = query,
        categoryAr = categoryAr,
        categoryEn = categoryEn,
        titleAr = titleAr,
        titleEn = titleEn,
        descriptionAr = descriptionAr,
        descriptionEn = descriptionEn,
        confidence = confidence
    )

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, vararg tokens: String): Boolean =
        tokens.any { token -> value.contains(normalize(token)) }

    private fun containsWord(value: String, token: String): Boolean =
        (" $value ").contains(" ${normalize(token)} ")

    private fun looksLikeMedication(value: String): Boolean {
        val hasDose = Regex("(^| )\\d+(\\.\\d+)? *(mg|mcg|g|ml|iu|unit|units)( |$)").containsMatchIn(value)
        val dosageWords = containsAny(value, "tablet", "tab ", "capsule", "cap ", "syrup", "ampoule", "amp ", "قرص", "كبسول", "شراب", "حقنه", "حقنة")
        return hasDose || dosageWords
    }
}
