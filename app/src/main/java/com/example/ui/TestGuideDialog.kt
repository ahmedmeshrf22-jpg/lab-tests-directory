package com.example.ui

import androidx.compose.material.icons.filled.*
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LabTest
import com.example.settings.tr
import com.example.util.PdfGenerator
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * V72 educational laboratory guide.
 *
 * Medical overview: MedlinePlus / CDC where a dedicated source exists.
 * Collection/tube reference: WHO phlebotomy guidance + BD Vacutainer product specifications.
 * Tube colors are displayed as a common collection reference, never as a replacement for the
 * laboratory's own SOP / analyser instructions because specimen requirements can vary by method.
 */
data class LabTestGuide(
    val overview: String,
    val whyWhen: String,
    val specimen: String,
    val tubeName: String,
    val tubeColorName: String,
    val tubeVisualKey: String,
    val preparation: String,
    val turnaround: String,
    val medicalSourceName: String,
    val medicalSourceUrl: String,
    val tubeConfidence: String = "شائع",
    val specialNote: String = ""
)

private object LabTestGuideRepository {
    private const val MEDLINE_BASE = "https://medlineplus.gov/lab-tests/"
    private const val MEDLINE_HEPATITIS = "https://medlineplus.gov/lab-tests/hepatitis-testing/"
    private const val CDC_HBV = "https://www.cdc.gov/hepatitis-b/hcp/diagnosis-testing/index.html"

    private data class TubeSpec(
        val specimen: String,
        val name: String,
        val color: String,
        val key: String,
        val confidence: String = "شائع",
        val note: String = ""
    )

    fun forTest(test: LabTest): LabTestGuide {
        val n = normalize(test.englishName)
        val a = test.arabicName.ifBlank { test.englishName }
        val tube = tubeFor(n)
        val exact = exactContent(n)
        val source = sourceFor(n)

        return LabTestGuide(
            overview = exact?.first ?: genericOverview(n, a),
            whyWhen = exact?.second ?: genericWhyWhen(n, a),
            specimen = tube.specimen,
            tubeName = tube.name,
            tubeColorName = tube.color,
            tubeVisualKey = tube.key,
            preparation = preparationFor(n),
            turnaround = turnaroundFor(n),
            medicalSourceName = source.first,
            medicalSourceUrl = source.second,
            tubeConfidence = tube.confidence,
            specialNote = tube.note
        )
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace("-", " ")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun exactContent(n: String): Pair<String, String>? = when {
        n == "tsh" -> Pair(
            "يقيس هرمون TSH الذي تفرزه الغدة النخامية لتنظيم عمل الغدة الدرقية.",
            "يطلب لتقييم نشاط الغدة الدرقية، والمساعدة في اكتشاف القصور أو فرط النشاط، ولمتابعة علاج اضطرابات الغدة."
        )
        n == "esr" -> Pair(
            "يقيس سرعة ترسب كرات الدم الحمراء، وهو مؤشر غير نوعي قد يرتفع مع وجود التهاب في الجسم.",
            "يطلب مع الأعراض أو فحوص أخرى للمساعدة في تقييم أو متابعة حالات التهابية؛ ولا يحدد سبب الالتهاب بمفرده."
        )
        n == "creatinine" -> Pair(
            "يقيس الكرياتينين، وهو ناتج فضلات من نشاط العضلات تتخلص منه الكلى بصورة أساسية.",
            "يطلب لتقييم وظائف الكلى ومتابعتها، وغالبا يستخدم مع eGFR أو تحاليل كلوية أخرى."
        )
        n == "hbs ag" || n == "hbsag" -> Pair(
            "يكشف مستضد سطح فيروس التهاب الكبد B ‏(HBsAg) في الدم.",
            "يستخدم ضمن فحوص عدوى فيروس B. وجود HBsAg قد يدل على عدوى حالية ويحتاج تفسيره مع بقية مؤشرات فيروس B والسياق السريري."
        )
        n == "hcv ab" -> Pair(
            "يكشف الأجسام المضادة لفيروس التهاب الكبد C، أي استجابة الجسم السابقة أو الحالية للتعرض للفيروس.",
            "يستخدم كفحص مبدئي للتعرض لفيروس C. النتيجة الإيجابية عادة تحتاج اختبار HCV RNA لتحديد وجود عدوى نشطة."
        )
        n == "urine analysis" -> Pair(
            "فحص للبول يشمل خواصه الفيزيائية والكيميائية وقد يشمل الفحص الميكروسكوبي للخلايا والبلورات والميكروبات.",
            "يطلب في تقييم أعراض المسالك البولية، بعض أمراض الكلى والسكري، أو كجزء من فحص عام حسب الحالة."
        )
        n == "cbc" -> Pair(
            "يقيس مكونات الدم الأساسية مثل كرات الدم الحمراء والبيضاء والصفائح والهيموجلوبين ومؤشرات الخلايا.",
            "يطلب لتقييم الأنيميا، العدوى، اضطرابات الصفائح والدم، أو كجزء من الفحص والمتابعة العامة."
        )
        n.contains("glycated haemoglobin") || n.contains("hba1c") -> Pair(
            "يعكس متوسط مستوى سكر الدم تقريبا خلال آخر شهرين إلى ثلاثة أشهر من خلال قياس الهيموجلوبين المرتبط بالجلوكوز.",
            "يستخدم لتشخيص ومتابعة السكري وتقييم التحكم طويل المدى في سكر الدم، مع مراعاة الحالات التي تؤثر في عمر كرات الدم الحمراء."
        )
        n == "ferritin" -> Pair(
            "يقيس الفيريتين، وهو بروتين يخزن الحديد ويعطي مؤشرا على مخزون الحديد في الجسم.",
            "يطلب عند تقييم نقص الحديد أو زيادته، وغالبا يفسر مع CBC والحديد وTIBC حسب الحالة."
        )
        n.contains("vit d3") || n.contains("25 hydroxy") -> Pair(
            "يقيس 25-hydroxy vitamin D، وهو الفحص الأكثر استخداما لتقييم مخزون فيتامين د في الجسم.",
            "يطلب عند الاشتباه في نقص فيتامين د أو متابعة العلاج أو تقييم اضطرابات العظام والكالسيوم حسب الحالة."
        )
        n.contains("vit b12") -> Pair(
            "يقيس مستوى فيتامين B12 الضروري لتكوين خلايا الدم ووظائف الجهاز العصبي.",
            "يطلب عند تقييم بعض أنواع الأنيميا أو أعراض عصبية أو الاشتباه في نقص B12."
        )
        n.startsWith("alt") -> Pair(
            "يقيس إنزيم ALT الموجود بتركيز مرتفع في خلايا الكبد.",
            "يطلب للمساعدة في تقييم إصابة أو التهاب خلايا الكبد ومتابعة أمراض الكبد مع بقية اختبارات الوظائف."
        )
        n.startsWith("ast") -> Pair(
            "يقيس إنزيم AST الموجود في الكبد والعضلات وأنسجة أخرى.",
            "يطلب ضمن تقييم وظائف الكبد أو إصابات أنسجة أخرى، ويُفسر عادة مع ALT وبقية النتائج."
        )
        n == "crp" -> Pair(
            "يقيس بروتين C التفاعلي الذي يرتفع استجابة للالتهاب.",
            "يستخدم للمساعدة في تقييم وجود وشدة التهاب ومتابعة تطوره؛ لكنه لا يحدد سبب الالتهاب بمفرده."
        )
        n == "d dimer" || n == "d-dimer" -> Pair(
            "يقيس نواتج تكسير الفيبرين التي قد ترتفع عند تكوّن وتحلل الجلطات.",
            "يستخدم ضمن تقييم احتمالية الجلطات مع التقييم السريري؛ والنتيجة المرتفعة ليست تشخيصا لجلطة بمفردها."
        )
        n.contains("blood glucose fasting") || n.contains("fasting blood glucose") -> Pair(
            "يقيس تركيز الجلوكوز في الدم بعد فترة صيام.",
            "يستخدم في تقييم أو تشخيص اضطرابات سكر الدم ومتابعتها حسب الخطة الطبية."
        )
        n == "glucose random" -> Pair(
            "يقيس مستوى الجلوكوز في الدم دون اشتراط صيام.",
            "يستخدم لتقييم مستوى السكر في وقت القياس، خاصة عند وجود أعراض أو أثناء المتابعة."
        )
        n.contains("lipids profile") -> Pair(
            "مجموعة فحوص للدهون عادة تشمل الكوليسترول الكلي وHDL وLDL والدهون الثلاثية.",
            "تستخدم لتقييم خطر أمراض القلب والأوعية ومتابعة اضطرابات الدهون والعلاج."
        )
        n == "troponin i (qualitative)" || n == "troponin i (quantitative)" -> Pair(
            "يقيس بروتين التروبونين القلبي الذي قد يرتفع عند حدوث إصابة في عضلة القلب.",
            "يطلب عند الاشتباه في متلازمة الشريان التاجي الحادة أو إصابة عضلة القلب، ويُفسر مع الأعراض وECG والتغير الزمني."
        )
        else -> null
    }

    private fun genericOverview(n: String, a: String): String = when {
        isCulture(n) -> "فحص ميكروبيولوجي للعينة بهدف عزل الكائن المسبب للعدوى، وقد يتبعه اختبار حساسية للمضادات عند نمو ميكروب مناسب."
        n.contains("pcr") || n.contains("dna") || n.contains("rna") -> "فحص جزيئي للكشف عن مادة وراثية مرتبطة بـ $a أو قياسها حسب نوع الاختبار."
        n.contains("igm") -> "فحص للأجسام المضادة من نوع IgM المرتبطة بـ $a. تفسير IgM يختلف حسب الميكروب والفحص ولا يثبت عدوى حديثة بمفرده في كل الحالات."
        n.contains("igg") -> "فحص للأجسام المضادة من نوع IgG المرتبطة بـ $a، وقد يعكس تعرضا سابقا أو استجابة مناعية حسب نوع الفحص والسياق."
        n.contains("antibody") || n.contains(" ab") || n.startsWith("anti ") || n.contains("ana") || n.contains("anca") -> "فحص مناعي للكشف عن أجسام مضادة مرتبطة بـ $a. النتيجة تُفسر مع الأعراض والفحص السريري وبقية التحاليل."
        isTumorMarker(n) -> "يقيس مؤشرا حيويا قد يرتبط ببعض الأورام أو حالات غير سرطانية، ويُستخدم في سياق سريري محدد."
        isHormone(n) -> "يقيس مستوى هرمون أو مادة تنظيمية مرتبطة بـ $a للمساعدة في تقييم المحور الهرموني أو العضو المرتبط به."
        isDrugLevel(n) -> "يقيس مستوى دواء أو مادة في الدم للمساعدة في المتابعة العلاجية أو تقييم السمية حسب التوقيت السريري."
        n.contains("enzyme") || n.contains("ase") || n.contains("ck") || n.contains("ldh") || n.contains("ggt") -> "يقيس مستوى $a كجزء من تقييم وظيفة أو إصابة أنسجة/أعضاء مرتبطة به، ويُفسر مع بقية النتائج."
        else -> "فحص معملي لقياس أو الكشف عن $a. الغرض الدقيق والتفسير يعتمد على سبب طلب الطبيب وبقية النتائج."
    }

    private fun genericWhyWhen(n: String, a: String): String = when {
        isCulture(n) -> "يطلب عند الاشتباه في عدوى بالمكان الذي أُخذت منه العينة، للمساعدة في تحديد الميكروب والعلاج المناسب."
        n.contains("pcr") || n.contains("dna") || n.contains("rna") -> "يطلب عندما تكون هناك حاجة لكشف جزيئي أكثر مباشرة، أو لتأكيد/قياس عامل معدٍ أو تغير وراثي حسب نوع الاختبار."
        isTumorMarker(n) -> "يستخدم غالبا للمساعدة في المتابعة أو في تقييم موجه، وليس كاختبار تشخيص أو فحص سرطان منفردا دون سياق طبي."
        isDrugLevel(n) -> "يطلب لضبط الجرعة أو التأكد من الوصول لمستوى علاجي آمن أو عند الاشتباه في السمية/عدم الالتزام."
        isHormone(n) -> "يطلب عند وجود أعراض أو متابعة حالة قد ترتبط باضطراب هرموني؛ التوقيت والتحضير قد يكونان مهمين لبعض الهرمونات."
        n.contains("urine") || n.contains("stool") || n.contains("semen") -> "يطلب حسب الأعراض والعضو المراد تقييمه. جودة جمع العينة وتوقيتها قد تؤثر مباشرة على النتيجة."
        else -> "يطلب عندما يحتاج الطبيب معلومة عن $a ضمن التشخيص أو المتابعة. لا تُفسر النتيجة وحدها بعيدا عن الحالة السريرية."
    }

    private fun sourceFor(n: String): Pair<String, String> = when {
        n == "tsh" -> Pair("MedlinePlus — TSH Test", "https://medlineplus.gov/lab-tests/tsh-thyroid-stimulating-hormone-test/")
        n == "esr" -> Pair("MedlinePlus — ESR", "https://medlineplus.gov/lab-tests/erythrocyte-sedimentation-rate-esr/")
        n == "creatinine" -> Pair("MedlinePlus — Creatinine Test", "https://medlineplus.gov/lab-tests/creatinine-test/")
        n == "urine analysis" -> Pair("MedlinePlus — Urinalysis", "https://medlineplus.gov/urinalysis.html")
        n == "hbs ag" || n == "hbsag" -> Pair("CDC — Hepatitis B testing", CDC_HBV)
        n.contains("hbv") || n.contains("hbe") || n.contains("hbc") || n.contains("hbs") -> Pair("CDC — Hepatitis B testing", CDC_HBV)
        n.contains("hcv") || n.contains("hepatitis") -> Pair("MedlinePlus — Hepatitis Testing", MEDLINE_HEPATITIS)
        else -> Pair("MedlinePlus — Medical Tests", MEDLINE_BASE)
    }

    private fun tubeFor(n: String): TubeSpec {
        if (n.contains("blood culture") || n == "sample blood culture") {
            return TubeSpec("دم", "زجاجات مزرعة دم (هوائي/لاهوائي حسب الطلب)", "زجاجات مزرعة مخصصة", "culture", "خاص", "تُتبع تعليمات التعقيم وحجم السحب وترتيب الزجاجات حسب بروتوكول المعمل.")
        }
        if (n.contains("urine") || n.contains("(ur)") || n.contains("acetone")) {
            return TubeSpec("بول", "وعاء بول مناسب للفحص", "لا توجد أنبوبة دم", "urine", "مؤكد", "للمزرعة يُفضل وعاء معقم وجمع clean-catch حسب تعليمات المعمل.")
        }
        if (n.contains("stool")) {
            return TubeSpec("براز", "وعاء براز نظيف/معقم حسب الفحص", "لا توجد أنبوبة دم", "stool", "مؤكد")
        }
        if (n.contains("semen") || n.contains("seminal") || n.contains("sperm")) {
            return TubeSpec("سائل منوي", "وعاء معقم واسع الفوهة مخصص للعينة", "لا توجد أنبوبة دم", "semen", "مؤكد")
        }
        if (n.contains("sputum") || n.contains("wound") || n.contains("pus c/s") || n.contains("swab") || n.contains("cervical") || n.contains("vaginal") || n.contains("ear c/s") || n.contains("eye c/s")) {
            return TubeSpec("حسب موضع العينة", "وعاء/مسحة ووسط نقل مناسب للميكروبيولوجي", "لا توجد أنبوبة دم", "culture", "خاص", "نوع المسحة ووسط النقل يحدده دليل الميكروبيولوجي الخاص بالمعمل.")
        }
        if (n.contains("ascitic") || n.contains("synovial") || n.contains("csf")) {
            return TubeSpec("سائل جسم", "وعاء معقم/أنبوبة مخصصة حسب نوع الفحص", "أنبوبة خاصة — راجع SOP", "special", "خاص", "تختلف العبوة حسب كيمياء/خلايا/مزرعة؛ يجب اتباع SOP المعمل.")
        }
        if (n.contains("stone analysis")) {
            return TubeSpec("حصوة", "وعاء جاف نظيف", "لا توجد أنبوبة دم", "special", "مؤكد")
        }

        // Whole-blood / hematology.
        if (
            n == "cbc" || n == "haemoglobin hb" || n.contains("platelets count") ||
            n.contains("total leucocytic") || n.contains("reticulocytic") || n.contains("hba1c") ||
            n.contains("glycated haemoglobin") || n.contains("hb electrophoresis") || n == "g6pd" ||
            n == "cd4" || n == "cd8" || n.contains("cd4/cd8") || n.contains("malaria film") ||
            n.contains("tacrolimus")
        ) {
            return TubeSpec("دم كامل EDTA", "K2EDTA / K3EDTA", "بنفسجي Lavender", "lavender", "شائع", "BD يحدد أنابيب EDTA بغطاء Lavender؛ بعض الاختبارات قد تشترط نوع EDTA وحجم عينة محدد.")
        }

        if (n == "abo" || n == "rh" || n.contains("coomb's direct")) {
            return TubeSpec("دم كامل EDTA", "K2EDTA / K3EDTA", "بنفسجي Lavender", "lavender", "شائع", "يعرض الدليل اللون البنفسجي باعتباره خيار EDTA الروتيني الشائع. إذا كان بنك الدم لديك يعتمد أنبوبة مخصصة فاتبع SOP المعمل.")
        }

        // Coagulation.
        if (
            n.contains("d dimer") || n.contains("d-dimer") || n == "ptt" || n.contains("prothrombin time") ||
            n == "fibrinogen" || n == "factor v" || n == "factor vii" || n == "factor viii" ||
            n == "protein c" || n == "protein s" || n.contains("anti thrombin") || n.contains("antithrombin") ||
            n.contains("lupus anticoagulant") || n.contains("von willebrand") || n == "fdps"
        ) {
            return TubeSpec("بلازما سيترات", "Sodium citrate 3.2%", "أزرق فاتح Light Blue", "light_blue", "مؤكد", "يلزم امتلاء الأنبوبة للنسبة الصحيحة بين الدم والسيترات؛ متطلبات خاصة قد تنطبق حسب الفحص.")
        }

        if (n == "esr") {
            return TubeSpec("دم كامل", "Sodium citrate ESR tube (Westergren) أو EDTA حسب الجهاز", "أسود ESR غالبا", "black", "متغير", "طريقة Westergren الشائعة تستخدم أنبوبة ESR سيترات؛ بعض أنظمة المعمل تعمل من EDTA. اتبع SOP المعمل.")
        }

        // Glucose preservation / toxicology alcohol / lactate.
        if (n.contains("glucose") || n.contains("blood glucose") || n == "lactate" || n.contains("alcohol in blood")) {
            return TubeSpec("بلازما/دم حسب الطريقة", "Fluoride/Oxalate أو أنبوبة معتمدة للجلوكوز", "رمادي Gray", "gray", "شائع", "الرمادي شائع لتثبيط تحلل الجلوكوز. قد يقبل جهاز المعمل serum/plasma مع فصل سريع.")
        }

        if (n.contains("acth")) {
            return TubeSpec("بلازما EDTA", "EDTA — معالجة مبردة", "بنفسجي Lavender", "lavender", "خاص", "ACTH حساس قبل التحليل؛ غالبا يحتاج تبريد سريع وفصل/نقل حسب تعليمات المعمل والجهاز.")
        }
        if (n.contains("antidiuretic hormone") || n == "adh") {
            return TubeSpec("بلازما", "أنبوبة خاصة حسب طريقة القياس", "أنبوبة خاصة — راجع SOP", "special", "خاص", "ADH من الاختبارات الحساسة قبل التحليل؛ لا يعرض الدليل لون غطاء غير شائع دون SOP المعمل.")
        }
        if (n.contains("ammonia")) {
            return TubeSpec("بلازما", "أنبوبة معتمدة حسب الجهاز + تبريد فوري", "أنبوبة خاصة — راجع SOP", "special", "خاص", "الأمونيا تحتاج تعامل سريع ومبرد؛ نوع الأنبوبة يحدد وفقا لطريقة جهاز المعمل، لذلك لا يعرض الدليل لون غطاء غير روتيني.")
        }
        if (n.contains("calcium (ionized)")) {
            return TubeSpec("دم كامل/بلازما مهيبرنة", "Balanced heparin / lithium heparin حسب الجهاز", "أخضر Green غالبا", "green", "خاص", "يجب تقليل التعرض للهواء لأن تغير pH يؤثر على الكالسيوم المتأين.")
        }
        if (n.contains("zinc") || n.contains("copper")) {
            return TubeSpec("مصل/بلازما للعناصر النزرة", "أنبوبة عناصر نزرة معتمدة حسب بروتوكول المعمل", "أنبوبة خاصة — راجع SOP", "special", "خاص", "العناصر النزرة تحتاج عبوة معتمدة قليلة التلوث. لا يعرض الدليل لون غطاء نادر لأن النوع المطلوب يختلف حسب المنصة والمعمل.")
        }
        if (n.contains("karyotyping")) {
            return TubeSpec("دم كامل", "Sodium heparin", "أخضر Green", "green", "شائع", "تحاليل الكروموسومات تحتاج خلايا حية؛ متطلبات النقل والزمن مهمة.")
        }
        if (n.contains("quantiferon") || n.contains("t.b gold")) {
            return TubeSpec("دم كامل", "QuantiFERON dedicated collection tubes", "أنابيب QuantiFERON المخصصة", "special", "مؤكد", "لا تستبدل الأنابيب المخصصة بأنبوبة روتينية؛ اتبع تعليمات الكيت المستخدم.")
        }
        if (n.contains("pcr") || n.contains("dna") || n.contains("rna") || n.contains("mthfr") || n.contains("factor v leiden") || n.contains("hla b27")) {
            return TubeSpec("دم كامل EDTA أو بلازما EDTA حسب الاختبار", "EDTA", "بنفسجي Lavender غالبا", "lavender", "متغير", "الـPCR يختلف حسب الهدف: بعض الفحوص تحتاج دم كامل وبعضها بلازما؛ راجع SOP لكل اختبار.")
        }

        // Most routine chemistry, hormones and serology use serum, but the analyser/SOP wins.
        return TubeSpec(
            specimen = "مصل Serum غالبا",
            name = "Serum Separator Tube (SST) / Clot activator",
            color = "ذهبي Gold غالبا",
            key = "gold",
            confidence = "شائع",
            note = "يعرض الدليل الذهبي SST كخيار روتيني شائع لمعظم الكيمياء/الهرمونات/السيرولوجي. إذا كان معملك يعتمد الأحمر Serum tube لنفس المجموعة فاتبع SOP المعمل."
        )
    }

    private fun preparationFor(n: String): String = when {
        n.contains("24 hrs") || n.contains("24 hr") -> "تجميع العينة لمدة 24 ساعة حسب تعليمات المعمل؛ توقيت البداية والنهاية وحفظ الوعاء مهمان."
        n.contains("fasting") -> "صيام حسب تعليمات الطبيب/المعمل، وغالبا 8 ساعات للمطلوب كـ Fasting. الماء عادة مسموح ما لم توجد تعليمات أخرى."
        n.contains("2 hrs p.p") || n.contains(" pp") -> "التزم بتوقيت ما بعد الوجبة المكتوب في الطلب؛ غالبا تُحسب الساعتان من بداية الوجبة حسب بروتوكول المعمل."
        n.contains("acth am") || n.contains("cortisol am") -> "العينة صباحية في التوقيت المحدد لأن للهرمون إيقاعا يوميا؛ اتبع وقت المعمل المطلوب."
        n.contains("acth pm") || n.contains("cortisol pm") -> "العينة مسائية في التوقيت المحدد؛ التوقيت جزء أساسي من تفسير النتيجة."
        isDrugLevel(n) -> "سجل وقت آخر جرعة ووقت سحب العينة. بعض مستويات الأدوية تحتاج عينة قبل الجرعة التالية (trough) حسب وصف الطبيب."
        isCulture(n) -> "يفضل جمع عينة جيدة قبل بدء المضاد الحيوي متى كان ذلك ممكنا طبيا، مع الالتزام بالتعقيم ووسيلة النقل المناسبة."
        n == "urine analysis" -> "عينة بول clean-catch مناسبة؛ يفضل تسليمها سريعا للمعمل. قد يطلب المعمل عينة صباحية في بعض الحالات."
        n.contains("urine c/s") -> "بول midstream clean-catch في وعاء معقم، مع تجنب التلوث وتسليم العينة بسرعة حسب تعليمات المعمل."
        n.contains("semen analysis") -> "اتبع تعليمات المعمل بدقة بخصوص مدة الامتناع، جمع كامل العينة، ووقت تسليمها؛ هذه العوامل تؤثر على النتيجة."
        n.contains("gastrin") -> "قد يتطلب صياما ومراجعة أدوية تؤثر على حموضة المعدة؛ لا توقف أي دواء إلا بتوجيه الطبيب/المعمل."
        else -> "لا يوجد تحضير عام ثابت لهذا الفحص. راجع طلب الطبيب وتعليمات المعمل، خصوصا للأدوية والصيام والتوقيت."
    }

    private fun turnaroundFor(n: String): String = when {
        n.contains("t.b c/s") -> "تقريبي: قد يستغرق عدة أسابيع حسب طريقة مزرعة الدرن."
        n.contains("fungus c/s") -> "تقريبي: عدة أيام وقد يمتد أكثر حسب نوع الفطر وطريقة الزرع."
        isCulture(n) -> "تقريبي: 48–72 ساعة للنمو البكتيري المعتاد، وقد يزيد حسب العينة والميكروب والحساسية."
        n.contains("karyotyping") -> "تقريبي: من عدة أيام إلى أسابيع حسب زراعة الخلايا وخط سير المعمل."
        n.contains("pcr") || n.contains("dna") || n.contains("rna") -> "تقريبي: 1–5 أيام حسب تشغيل الـPCR وهل الفحص داخلي أو مرسل لمعمل مرجعي."
        n.contains("antibody") || n.contains(" ab") || n.startsWith("anti ") || n.contains("ana") || n.contains("anca") || n.contains("igg") || n.contains("igm") -> "تقريبي: نفس اليوم إلى عدة أيام حسب جدول تشغيل الاختبار."
        isHormone(n) || isTumorMarker(n) -> "تقريبي: نفس اليوم أو خلال 24 ساعة في التشغيل الروتيني؛ قد يزيد للاختبارات الخاصة."
        n == "urine analysis" || n == "stool analysis" || n == "cbc" || n == "esr" || n.contains("glucose") -> "تقريبي: من أقل من ساعة إلى بضع ساعات بعد وصول العينة حسب ضغط وتشغيل المعمل."
        else -> "تقريبي: غالبا نفس اليوم للاختبارات الروتينية؛ المدة الفعلية تعتمد على جهاز وجدول وسياسة المعمل."
    }

    private fun isCulture(n: String): Boolean = n.contains("c/s") || n.contains("culture")
    private fun isTumorMarker(n: String): Boolean = n.contains("tumor marker") || n.startsWith("ca ") || n == "cea" || n == "afp" || n.contains("psa")
    private fun isDrugLevel(n: String): Boolean = n.contains("depakine") || n.contains("digoxin") || n.contains("epanutin") || n.contains("phenytoin") || n == "lithium" || n.contains("tacrolimus") || n.contains("tegratol") || n.contains("valproic")
    private fun isHormone(n: String): Boolean =
        n.contains("hormone") || n == "tsh" || n == "fsh" || n == "lh" || n == "prl" ||
            n.contains("cortisol") || n.contains("testosterone") || n.contains("progesterone") ||
            n.contains("estradiol") || n.contains("insulin") || n.contains("c peptide") ||
            n.contains("growth hormone") || n.contains("igf 1") || n.contains("aldosterone") ||
            n.contains("acth") || n.contains("pth") || n.contains("calcitonin") || n.contains("dhea") ||
            n.contains("androstenedione") || n.contains("amh") || n.contains("leptin") || n.contains("gastrin")
 }

internal fun labTestGuideFor(test: LabTest): LabTestGuide = LabTestGuideRepository.forTest(test)

@Composable
fun TestGuideMoreButton(
    test: LabTest,
    modifier: Modifier = Modifier
) {
    var open by remember(test.id) { mutableStateOf(false) }

    LabeledIconAction(label = tr("المزيد — دليل التحليل", "More — Test guide"), onClick = { open = true }, modifier = modifier
            .fillMaxWidth()
            .shadow(15.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFF2FD6E6), spotColor = Color(0xFF2FD6E6))) { Icon(Icons.Default.Info, contentDescription = null) }

    if (open) {
        TestGuideHologramDialog(test = test, onDismiss = { open = false })
    }
}

@Composable
private fun TestGuideHologramDialog(
    test: LabTest,
    onDismiss: () -> Unit
) {
    val guide = remember(test.id) { LabTestGuideRepository.forTest(test) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var busyAction by remember { mutableStateOf<String?>(null) }
    var pendingLegacySave by remember { mutableStateOf(false) }

    fun buildGuideImage() = generateGuideHologramImage(context, test, guide)

    fun saveGuideNow() {
        if (busyAction != null) return
        busyAction = "save"
        try {
            val file = buildGuideImage()
            if (file != null) {
                PdfGenerator.saveGeneratedImageToGallery(
                    context = context,
                    file = file,
                    displayName = "Tahalil_Alakkad_Guide_${test.englishName}_${System.currentTimeMillis()}.png"
                )
            }
        } finally {
            busyAction = null
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldSave = pendingLegacySave
        pendingLegacySave = false
        if (granted && shouldSave) saveGuideNow()
    }

    fun requestGuideSave() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingLegacySave = true
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveGuideNow()
        }
    }

    fun shareGuideNow() {
        if (busyAction != null) return
        busyAction = "share"
        try {
            val file = buildGuideImage()
            if (file != null) {
                PdfGenerator.shareGeneratedImage(
                    context = context,
                    file = file,
                    subject = "دليل تحليل ${test.englishName} - تحاليل العقاد",
                    chooserTitle = "مشاركة دليل التحليل"
                )
            }
        } finally {
            busyAction = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9101822)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color(0x9931E6F2))
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF071824),
                                Color(0xFF0A2532),
                                Color(0xFF071A28),
                                Color(0xFF07131D)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF41DDEB), RoundedCornerShape(28.dp))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 17.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                HoloBadge("دليل التحليل")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = test.englishName,
                                    fontSize = 22.sp,
                                    lineHeight = 27.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (test.arabicName.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = test.arabicName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9DECF4)
                                    )
                                }
                            }
                            LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = tr("إغلاق", "Close"),
                                    tint = Color(0xFFB8F7FC)
                                )
                            }
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x1617D4E5),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0x5537DDE9))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                TubeIllustration(
                                    key = guide.tubeVisualKey,
                                    colorName = guide.tubeColorName
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Biotech, contentDescription = null, tint = Color(0xFF7AF4FF), modifier = Modifier.size(21.dp))
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            "العينة والأنبوبة",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF7AF4FF)
                                        )
                                    }
                                    Spacer(Modifier.height(9.dp))
                                    TubeLine(Icons.Outlined.Biotech, "نوع العينة", guide.specimen)
                                    TubeLine(Icons.Default.ReceiptLong, "اسم الأنبوبة", guide.tubeName)
                                    TubeLine(Icons.Default.Palette, "لون الغطاء", guide.tubeColorName)
                                    TubeLine(Icons.Default.CheckCircle, "درجة الاعتماد", guide.tubeConfidence)
                                }
                            }
                        }
                    }

                    item {
                        HoloInfoSection(
                            title = "إيه هو التحليل؟",
                            text = guide.overview,
                            icon = Icons.Default.Info
                        )
                    }
                    item {
                        HoloInfoSection(
                            title = "بيتطلب إمتى وليه؟",
                            text = guide.whyWhen,
                            icon = Icons.Default.Search
                        )
                    }
                    item {
                        HoloInfoSection(
                            title = "التحضير قبل العينة",
                            text = guide.preparation,
                            icon = Icons.Default.Tune
                        )
                    }
                    item {
                        HoloInfoSection(
                            title = "مدة ظهور النتيجة",
                            text = guide.turnaround,
                            icon = Icons.Default.Schedule
                        )
                    }

                    if (guide.specialNote.isNotBlank()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0x22FFB020),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color(0x66FFB020))
                            ) {
                                Row(
                                    modifier = Modifier.padding(13.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC55A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        guide.specialNote,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFE1A6)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x1210E5B0),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0x5540E6BC))
                        ) {
                            Column(modifier = Modifier.padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF67F0C1),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        "المصادر الطبية",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF9AFADE),
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    guide.medicalSourceName,
                                    color = Color(0xFFD8FFF4),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "سحب العينات والأنابيب: WHO Phlebotomy + BD Vacutainer specifications",
                                    color = Color(0xFFB7D9D4),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LabeledIconAction(label = "المصدر الطبي", onClick = { uriHandler.openUri(guide.medicalSourceUrl) }) { Icon(Icons.Default.Info, contentDescription = null) }
                                    LabeledIconAction(label = "WHO", onClick = { uriHandler.openUri("https://www.who.int/publications/i/item/9789241599221") }) { Icon(Icons.Default.Search, contentDescription = null) }
                                    LabeledIconAction(label = "BD", onClick = { uriHandler.openUri("https://www.bd.com/en-us/products-and-solutions/products/product-families/bd-vacutainer-blood-collection-tubes") }) { Icon(Icons.Outlined.Biotech, contentDescription = null) }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x1825DCE8),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0x6657EAF3))
                        ) {
                            Column(
                                modifier = Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Text(
                                    "احتفظ بالدليل كصورة",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF8CF7FF),
                                    fontSize = 14.sp
                                )
                                Text(
                                    "الصورة بتجمع المعلومة الأساسية والعينة والأنبوبة والتحضير والمدة والمصادر في شكل واحد.",
                                    color = Color(0xFFCBE9EC),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LabeledIconAction(label = "حفظ صورة", onClick = { requestGuideSave() }, modifier = Modifier.weight(1f), enabled = busyAction == null) { Icon(Icons.Default.Save, contentDescription = null) }
                                    LabeledIconAction(label = "مشاركة", onClick = { shareGuideNow() }, modifier = Modifier.weight(1f), enabled = busyAction == null) { Icon(Icons.Default.Share, contentDescription = null) }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x14FFFFFF),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "الألوان الروتينية المعروضة: ذهبي SST، بنفسجي EDTA، أزرق فاتح Citrate، رمادي Fluoride، أخضر Heparin، وأسود ESR عند الحاجة. الاختبارات الخاصة تظهر كـ «أنبوبة خاصة» بدل ألوان نادرة. الـSOP المحلي هو المرجع النهائي قبل سحب أي عينة.",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC6D7DE),
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}


/** V77: export the premium educational hologram guide as a standalone PNG. */
private fun generateGuideHologramImage(
    context: android.content.Context,
    test: LabTest,
    guide: LabTestGuide
): File? = try {
    val width = 1080
    val side = 64f
    val top = 54f

    fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.replace("\n", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#DDF7FA")
        textSize = 31f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#78F4FF")
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#AFCBD1")
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }

    val maxTextWidth = width - side * 2 - 58f
    val sections = listOf(
        "إيه هو التحليل؟" to guide.overview,
        "بيتطلب إمتى وليه؟" to guide.whyWhen,
        "التحضير قبل العينة" to guide.preparation,
        "مدة ظهور النتيجة" to guide.turnaround
    )
    val sectionLines = sections.map { (title, body) -> title to wrap(body, bodyPaint, maxTextWidth) }
    val specialLines = if (guide.specialNote.isBlank()) emptyList() else wrap(guide.specialNote, bodyPaint, maxTextWidth)
    val disclaimer = "ألوان الدليل الروتينية فقط: ذهبي، بنفسجي، أزرق فاتح، رمادي، أخضر، وأسود ESR. الاختبارات الخاصة تظهر كأنبوبة خاصة بدل ألوان نادرة؛ SOP المعمل هو المرجع النهائي."
    val disclaimerLines = wrap(disclaimer, smallPaint, maxTextWidth)

    val headerHeight = 330f
    val tubeHeight = 330f
    val eachSectionsHeight = sectionLines.sumOf { (_, lines) -> (116f + lines.size * 43f).toDouble() }.toFloat()
    val specialHeight = if (specialLines.isEmpty()) 0f else 110f + specialLines.size * 43f
    val sourceHeight = 230f
    val actionsSafeBottom = 100f + disclaimerLines.size * 36f
    val height = (top + headerHeight + tubeHeight + eachSectionsHeight + specialHeight + sourceHeight + actionsSafeBottom + 150f).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                android.graphics.Color.parseColor("#04131D"),
                android.graphics.Color.parseColor("#073141"),
                android.graphics.Color.parseColor("#061823")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#123E4A")
        style = Paint.Style.FILL
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#31DDE8")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0A2633")
        style = Paint.Style.FILL
    }

    var y = top
    canvas.drawRoundRect(RectF(38f, y, width - 38f, y + headerHeight - 20f), 42f, 42f, glow)
    canvas.drawRoundRect(RectF(38f, y, width - 38f, y + headerHeight - 20f), 42f, 42f, border)

    val logo = BitmapFactory.decodeResource(context.resources, com.example.R.drawable.ic_clinic_logo)
    if (logo != null) {
        val ls = 122f
        canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), RectF(width - side - ls, y + 28f, width - side, y + 28f + ls), null)
    }

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#174C59") }
    canvas.drawRoundRect(RectF(side, y + 30f, side + 270f, y + 86f), 28f, 28f, badgePaint)
    val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#83F8FF")
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("دليل التحليل • V77", side + 135f, y + 67f, badgeText)

    val testTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 45f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val testAr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8DECF4")
        textSize = 31f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(test.englishName.take(46), width - side, y + 186f, testTitle)
    if (test.arabicName.isNotBlank()) canvas.drawText(test.arabicName.take(52), width - side, y + 235f, testAr)
    y += headerHeight

    // Tube / specimen card.
    canvas.drawRoundRect(RectF(48f, y, width - 48f, y + tubeHeight - 22f), 34f, 34f, card)
    canvas.drawRoundRect(RectF(48f, y, width - 48f, y + tubeHeight - 22f), 34f, 34f, border)
    canvas.drawText("العينة والأنبوبة", width - 78f, y + 58f, titlePaint)

    val capColor = when (guide.tubeVisualKey) {
        "lavender" -> android.graphics.Color.parseColor("#9B7BEA")
        "light_blue" -> android.graphics.Color.parseColor("#77D4F6")
        "black" -> android.graphics.Color.parseColor("#202733")
        "green" -> android.graphics.Color.parseColor("#52B788")
        "gray" -> android.graphics.Color.parseColor("#9AA5B1")
        "yellow" -> android.graphics.Color.parseColor("#F4D35E")
        "gold" -> android.graphics.Color.parseColor("#D9A441")
        else -> android.graphics.Color.parseColor("#8EDCE6")
    }
    val tubeBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#DDF8FA")
        style = Paint.Style.FILL
    }
    val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = capColor; style = Paint.Style.FILL }
    val tx = 100f
    canvas.drawRoundRect(RectF(tx, y + 95f, tx + 130f, y + 270f), 32f, 32f, tubeBody)
    canvas.drawRoundRect(RectF(tx - 4f, y + 77f, tx + 134f, y + 125f), 15f, 15f, cap)

    val tubeInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E2F8FA")
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val tubeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#7EA5AE")
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    var ty = y + 112f
    listOf(
        "نوع العينة" to guide.specimen,
        "اسم الأنبوبة" to guide.tubeName,
        "لون الغطاء" to guide.tubeColorName,
        "درجة الاعتماد" to guide.tubeConfidence
    ).forEach { (label, value) ->
        canvas.drawText(label, width - 82f, ty, tubeLabelPaint)
        canvas.drawText(value.take(55), width - 82f, ty + 34f, tubeInfoPaint)
        ty += 62f
    }
    y += tubeHeight

    sectionLines.forEachIndexed { index, (title, lines) ->
        val h = 116f + lines.size * 43f
        canvas.drawRoundRect(RectF(48f, y, width - 48f, y + h - 16f), 32f, 32f, card)
        canvas.drawRoundRect(RectF(48f, y, width - 48f, y + h - 16f), 32f, 32f, border)
        canvas.drawText("0${index + 1}  $title", width - 78f, y + 58f, titlePaint)
        var ly = y + 105f
        lines.forEach { line ->
            canvas.drawText(line, width - 78f, ly, bodyPaint)
            ly += 43f
        }
        y += h
    }

    if (specialLines.isNotEmpty()) {
        val h = 110f + specialLines.size * 43f
        val warningCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#392A12") }
        val warningBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#D59B35"); style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRoundRect(RectF(48f, y, width - 48f, y + h - 16f), 32f, 32f, warningCard)
        canvas.drawRoundRect(RectF(48f, y, width - 48f, y + h - 16f), 32f, 32f, warningBorder)
        val wt = Paint(titlePaint).apply { color = android.graphics.Color.parseColor("#FFD07A") }
        canvas.drawText("ملاحظة مهمة", width - 78f, y + 58f, wt)
        var ly = y + 105f
        specialLines.forEach { line ->
            canvas.drawText(line, width - 78f, ly, bodyPaint)
            ly += 43f
        }
        y += h
    }

    canvas.drawRoundRect(RectF(48f, y, width - 48f, y + sourceHeight - 18f), 32f, 32f, card)
    canvas.drawRoundRect(RectF(48f, y, width - 48f, y + sourceHeight - 18f), 32f, 32f, border)
    canvas.drawText("المصادر الطبية", width - 78f, y + 58f, titlePaint)
    val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#D8FFF4")
        textSize = 27f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(guide.medicalSourceName.take(65), width - 78f, y + 108f, sourcePaint)
    smallPaint.color = android.graphics.Color.parseColor("#AFCBD1")
    canvas.drawText("سحب العينات والأنابيب: WHO Phlebotomy + BD Vacutainer", width - 78f, y + 152f, smallPaint)
    y += sourceHeight

    val disclaimerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#102A35") }
    val dHeight = 82f + disclaimerLines.size * 36f
    canvas.drawRoundRect(RectF(48f, y, width - 48f, y + dHeight), 28f, 28f, disclaimerBg)
    var dy = y + 48f
    disclaimerLines.forEach { line ->
        canvas.drawText(line, width - 78f, dy, smallPaint)
        dy += 36f
    }

    val dir = File(context.cacheDir, "guide_images").apply { mkdirs() }
    val safe = test.englishName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifBlank { "test" }
    val file = File(dir, "guide_${safe}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
    bitmap.recycle()
    file
} catch (_: Exception) {
    null
}

@Composable
private fun HoloBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x2230E7F1),
        border = BorderStroke(1.dp, Color(0x6630E7F1))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF7CF5FF)
        )
    }
}

@Composable
private fun HoloInfoSection(
    title: String,
    text: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x121DE6F2),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0x4438DCE8))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0E5260), Color(0xFF0A3444))
                            )
                        )
                        .border(1.dp, Color(0xFF38DCE8), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF76F5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF7BF3FC)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE1F3F6)
            )
        }
    }
}

@Composable
private fun TubeLine(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(30.dp).background(Color(0x1817D4E5), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF66EAF2), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7EA5AE))
            Text(value, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE4FAFC), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun TubeIllustration(
    key: String,
    colorName: String
) {
    val capColor = when (key) {
        "lavender" -> Color(0xFF9B7BEA)
        "light_blue" -> Color(0xFF77D4F6)
        "black" -> Color(0xFF202733)
        "gray" -> Color(0xFF9AA4AE)
        "green" -> Color(0xFF45C879)
        "gold" -> Color(0xFFE7B83D)
        "special" -> Color(0xFFADB7C0)
        "culture" -> Color(0xFF33B794)
        "urine" -> Color(0xFFFFD85A)
        "stool" -> Color(0xFFA9825E)
        "semen" -> Color(0xFFE7EEF3)
        else -> Color(0xFF6EDFEA)
    }

    Box(
        modifier = Modifier
            .width(86.dp)
            .height(170.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(58.dp)
                .height(152.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = capColor)
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x443EF4FF),
                            Color(0x22FFFFFF),
                            Color(0x553EF4FF)
                        )
                    )
                )
                .border(1.dp, Color(0x8899F7FF), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = 12.dp, topEnd = 12.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 7.dp)
                    .width(44.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp, topStart = 7.dp, topEnd = 7.dp))
                    .background(capColor.copy(alpha = 0.24f))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 5.dp),
                shape = RoundedCornerShape(7.dp),
                color = Color(0xDDF4FBFD)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Biotech,
                        contentDescription = null,
                        tint = Color(0xFF0D6170),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "SAMPLE",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF315966),
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(25.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(capColor)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
        )
        Text(
            text = colorName.substringBefore(" "),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFC8F8FC),
            maxLines = 1
        )
    }
}
