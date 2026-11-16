package com.cinemaroya.studio.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val synopsis: String = "",
    val genre: String = "وثائقي سينمائي",
    val aspectRatio: String = "16:9",
    val visualStyle: String = "Photorealistic 8K IMAX",
    val targetDurationSeconds: Long = 120L,
    val sceneCount: Long = 0L,
    val globalLightingPreset: String = "CINEMATIC_GOLD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoScene(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val sceneIndex: Long = 0L,
    val title: String = "",
    val narrationAr: String = "",
    val visualPrompt: String = "",
    val durationSeconds: Long = 10L,
    val cameraMovement: String = "DOLLY_FORWARD",
    val transitionType: String = "CROSSFADE",
    val lightingKeyIntensity: Long = 85L,
    val lightingFillIntensity: Long = 45L,
    val lightingRimIntensity: Long = 70L,
    val colorTempKelvin: Long = 4500L,
    val lightAngleDeg: Long = 45L,
    val lightElevationDeg: Long = 35L,
    val atmosphereFog: Long = 25L,
    val colorGradePreset: String = "CINEMATIC_GOLD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val cameraShotAngle: String = "EYE_LEVEL",
    @get:Exclude val cameraPitchDeg: Long = 0L,
    @get:Exclude val lensFocalMm: Long = 35L
)

enum class CameraShotAnglePreset(
    val code: String,
    val labelAr: String,
    val descAr: String,
    val defaultPitchDeg: Long,
    val promptTag: String
) {
    EYE_LEVEL(
        "EYE_LEVEL",
        "مستوى العين (Eye Level)",
        "زاوية طبيعية متوازنة تمنح المشاهد شعوراً بالواقعية والحضور المباشر",
        0L,
        "eye-level shot"
    ),
    LOW_ANGLE(
        "LOW_ANGLE",
        "زاوية منخفضة بطولية (Low Angle)",
        "التصوير من الأسفل للأعلى لإبراز العظمة والهيبة والعمق المعماري",
        -22L,
        "dramatic low-angle heroic shot looking up"
    ),
    HIGH_ANGLE(
        "HIGH_ANGLE",
        "زاوية مرتفعة شاملة (High Angle)",
        "التصوير من الأعلى لكشف تفاصيل المسرح والعناصر المحيطة بالكامل",
        25L,
        "high-angle cinematic overview shot"
    ),
    AERIAL_DRONE(
        "AERIAL_DRONE",
        "لقطة جوية بالدرون (Aerial Drone)",
        "تحليق سينمائي مرتفع يكشف التضاريس والمدن من منظور عين الصقر",
        40L,
        "sweeping aerial drone cinematography shot"
    ),
    DUTCH_ANGLE(
        "DUTCH_ANGLE",
        "زاوية مائلة درامية (Dutch Tilt)",
        "ميلان أفقي للكاميرا لإضفاء التوتر والغموض والحركة الديناميكية",
        -10L,
        "cinematic dutch-angle tilted frame"
    ),
    CLOSE_UP_MACRO(
        "CLOSE_UP_MACRO",
        "لقطة قريبة دقيقة (Macro / Close-Up)",
        "تركيز شديد على الملامح وتفاصيل الخامات والانعكاسات الضوئية",
        0L,
        "extreme close-up macro cinema shot with shallow depth of field"
    );

    companion object {
        fun fromCode(code: String): CameraShotAnglePreset =
            entries.find { it.code == code } ?: EYE_LEVEL
    }
}

enum class CameraLensPreset(
    val focalMm: Long,
    val labelAr: String,
    val fovZoomFactor: Float
) {
    LENS_16MM(16L, "16mm عريضة جداً", 0.92f),
    LENS_24MM(24L, "24mm سينمائية واسعة", 0.96f),
    LENS_35MM(35L, "35mm أنامورفيك", 1.00f),
    LENS_50MM(50L, "50mm قياسية واقعية", 1.08f),
    LENS_85MM(85L, "85mm بورتريه وعزل", 1.18f);

    companion object {
        fun fromFocal(mm: Long): CameraLensPreset =
            entries.find { it.focalMm == mm } ?: LENS_35MM
    }
}

enum class CameraMovementPreset(val code: String, val labelAr: String, val descAr: String) {
    STATIC("STATIC", "ثابت سينمائي", "كاميرا ثابتة على حامل ثلاثي لتركيز درامي عميق"),
    PAN_LEFT("PAN_LEFT", "تحريك لليسار", "التفاف أفقي سلس للكشف عن تفاصيل المشهد"),
    PAN_RIGHT("PAN_RIGHT", "تحريك لليمين", "التفاف أفقي بانورامي نحو اليمين"),
    ZOOM_IN("ZOOM_IN", "تقريب بطيء (Zoom In)", "تركيز تدريجي نحو العنصر الرئيسي لزيادة التوتر"),
    ZOOM_OUT("ZOOM_OUT", "تبعيد سينمائي (Zoom Out)", "كشف تدريجي للبيئة المحيطة بالكامل"),
    DOLLY_FORWARD("DOLLY_FORWARD", "تقدم للأمام (Dolly)", "حركة كاميرا واقعية على مسار نحو العمق"),
    ORBIT_3D("ORBIT_3D", "دوران محيطي 3D", "التفاف ثلاثي الأبعاد حول الهدف بزاوية سينمائية");

    companion object {
        fun fromCode(code: String): CameraMovementPreset =
            entries.find { it.code == code } ?: DOLLY_FORWARD
    }
}

enum class TransitionPreset(val code: String, val labelAr: String) {
    CROSSFADE("CROSSFADE", "تلاشي متداخل ناعم"),
    CUT("CUT", "قطع سينمائي مباشر"),
    DIP_TO_BLACK("DIP_TO_BLACK", "تعتيم تدريجي للأسود"),
    LIGHT_FLASH("LIGHT_FLASH", "وميض ضوئي درامي"),
    SLIDE_RTL("SLIDE_RTL", "انزلاق ديناميكي");

    companion object {
        fun fromCode(code: String): TransitionPreset =
            entries.find { it.code == code } ?: CROSSFADE
    }
}

enum class ColorGradePreset(
    val code: String,
    val labelAr: String,
    val subtitleAr: String,
    val defaultKelvin: Long,
    val keyLight: Long,
    val fillLight: Long,
    val rimLight: Long,
    val fog: Long
) {
    CINEMATIC_GOLD(
        "CINEMATIC_GOLD",
        "ذهبي سينمائي (Golden Hour)",
        "إضاءة شمس دافئة وظلال ناعمة واقعية",
        3600L, 90L, 45L, 75L, 30L
    ),
    TEAL_ORANGE(
        "TEAL_ORANGE",
        "تيل وبرتقالي هوليوود",
        "تباين لوني احترافي للأفلام الطويلة",
        4800L, 85L, 40L, 85L, 20L
    ),
    NOIR_CONTRAST(
        "NOIR_CONTRAST",
        "دراما وظلال عميقة (Chiaroscuro)",
        "إضاءة جانبية حادة مع تباين درامي عالي",
        5200L, 95L, 15L, 80L, 40L
    ),
    DESERT_WARM(
        "DESERT_WARM",
        "ملحمة صحراوية شرقية",
        "وهج رملي دافئ وغبار ضوئي في الهواء",
        3200L, 92L, 55L, 65L, 45L
    ),
    CYBER_NEON(
        "CYBER_NEON",
        "نيون مستقبلي (Cyberpunk)",
        "إضاءة محيطية باردة مع انعكاسات سيان وأرجواني",
        7800L, 80L, 35L, 95L, 55L
    ),
    NATURAL_8K(
        "NATURAL_8K",
        "واقعي طبيعي 8K (Documentary)",
        "إضاءة نهارية متوازنة فائقة الواقعية",
        5600L, 80L, 65L, 50L, 10L
    );

    companion object {
        fun fromCode(code: String): ColorGradePreset =
            entries.find { it.code == code } ?: CINEMATIC_GOLD
    }
}

@IgnoreExtraProperties
data class GeneratedVideo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val genre: String = "وثائقي سينمائي",
    val aspectRatio: String = "16:9",
    val durationSeconds: Long = 10L,
    val sceneCount: Long = 1L,
    val colorGradePreset: String = "CINEMATIC_GOLD",
    val thumbnailBase64: String = "",
    val localFilePath: String = "",
    val videoUrl: String = "",
    val mediaMimeType: String = "video/mp4",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class CinematicTemplatePreset(
    val code: String,
    val titleEn: String,
    val titleAr: String,
    val descriptionAr: String,
    val colorGrade: ColorGradePreset,
    val cameraMovement: CameraMovementPreset,
    val cameraShotAngle: CameraShotAnglePreset,
    val lensFocalMm: Long,
    val lightAngleDeg: Long,
    val lightElevationDeg: Long,
    val accentHex: Long,
    val secondaryHex: Long
) {
    NOIR(
        code = "NOIR",
        titleEn = "Noir",
        titleAr = "نوار كلاسيكي درامي",
        descriptionAr = "ظلال عميقة حادة وإضاءة جانبية درامية مع حركة بطيئة وزاوية مائلة",
        colorGrade = ColorGradePreset.NOIR_CONTRAST,
        cameraMovement = CameraMovementPreset.DOLLY_FORWARD,
        cameraShotAngle = CameraShotAnglePreset.DUTCH_ANGLE,
        lensFocalMm = 35L,
        lightAngleDeg = 125L,
        lightElevationDeg = 28L,
        accentHex = 0xFF94A3B8,
        secondaryHex = 0xFF0F172A
    ),
    GOLDEN_HOUR(
        code = "GOLDEN_HOUR",
        titleEn = "Golden Hour",
        titleAr = "الساعة الذهبية",
        descriptionAr = "شمس دافئة 3600K وتوهج ذهبي ناعم مع التفاف بانورامي متوازن",
        colorGrade = ColorGradePreset.CINEMATIC_GOLD,
        cameraMovement = CameraMovementPreset.PAN_RIGHT,
        cameraShotAngle = CameraShotAnglePreset.EYE_LEVEL,
        lensFocalMm = 50L,
        lightAngleDeg = 42L,
        lightElevationDeg = 18L,
        accentHex = 0xFFF59E0B,
        secondaryHex = 0xFF78350F
    ),
    WIDE_CINEMATIC(
        code = "WIDE_CINEMATIC",
        titleEn = "Wide Cinematic",
        titleAr = "بانوراما سينمائية عريضة",
        descriptionAr = "عدسة عريضة جداً 16mm مع لقطة جوية وتقدم مستمر وتدرج تيل وبرتقالي",
        colorGrade = ColorGradePreset.TEAL_ORANGE,
        cameraMovement = CameraMovementPreset.DOLLY_FORWARD,
        cameraShotAngle = CameraShotAnglePreset.AERIAL_DRONE,
        lensFocalMm = 16L,
        lightAngleDeg = 55L,
        lightElevationDeg = 48L,
        accentHex = 0xFF38BDF8,
        secondaryHex = 0xFFEA580C
    ),
    CYBER_NEON(
        code = "CYBER_NEON",
        titleEn = "Cyber Neon",
        titleAr = "نيون مستقبلي 8K",
        descriptionAr = "إضاءة باردة عالية التباين مع دوران محيطي 3D وزاوية بطولية منخفضة",
        colorGrade = ColorGradePreset.CYBER_NEON,
        cameraMovement = CameraMovementPreset.ORBIT_3D,
        cameraShotAngle = CameraShotAnglePreset.LOW_ANGLE,
        lensFocalMm = 24L,
        lightAngleDeg = 220L,
        lightElevationDeg = 32L,
        accentHex = 0xFF22D3EE,
        secondaryHex = 0xFF9333EA
    ),
    DESERT_EPIC(
        code = "DESERT_EPIC",
        titleEn = "Desert Epic",
        titleAr = "ملحمة صحراوية شرقية",
        descriptionAr = "إضاءة رملية دافئة وضباب حجمي مع تحليق علوي شامل",
        colorGrade = ColorGradePreset.DESERT_WARM,
        cameraMovement = CameraMovementPreset.PAN_LEFT,
        cameraShotAngle = CameraShotAnglePreset.HIGH_ANGLE,
        lensFocalMm = 24L,
        lightAngleDeg = 70L,
        lightElevationDeg = 24L,
        accentHex = 0xFFD97706,
        secondaryHex = 0xFF451A03
    ),
    PORTRAIT_INTIMATE(
        code = "PORTRAIT_INTIMATE",
        titleEn = "Intimate 85mm",
        titleAr = "بورتريه وعزل سينمائي",
        descriptionAr = "عدسة 85mm بعمق ميدان ضحل وتقريب بطيء لإبراز الملامح والتفاصيل",
        colorGrade = ColorGradePreset.NATURAL_8K,
        cameraMovement = CameraMovementPreset.ZOOM_IN,
        cameraShotAngle = CameraShotAnglePreset.CLOSE_UP_MACRO,
        lensFocalMm = 85L,
        lightAngleDeg = 30L,
        lightElevationDeg = 38L,
        accentHex = 0xFF10B981,
        secondaryHex = 0xFF064E3B
    );
}

