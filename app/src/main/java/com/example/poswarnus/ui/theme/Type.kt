package com.example.poswarnus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Design reference calls for "Inter" (400/500/600/700), with a Google Fonts downloadable
// font per the task brief's Step 3. That setup requires a certificate-hash resource file
// (e.g. res/values-v23/font_certs.xml, see the ui-text-google-fonts docs / Jetchat sample)
// outside this ui/theme package, which is out of scope for this task. Falling back to the
// platform sans-serif family as explicitly allowed by the brief — swap this FontFamily for
// a real Inter GoogleFont.Provider setup in a follow-up task if exact typeface fidelity is
// needed.
private val PosFontFamily = FontFamily.SansSerif

// Sizes mapped from docs/design-reference.md §1 "Typography" onto the closest Material3
// Typography slots. Sizes without a natural M3 slot (e.g. the 44sp grand total) are left
// for call sites to build as ad-hoc TextStyles, per the brief.
val Typography = Typography(
    displaySmall = TextStyle( // cart footer total style, 32sp/700
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle( // login "Masuk" heading / success heading, ~24-28sp/700
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle( // detail dialog item name / stepper number, 22sp/700
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle( // section headers ("Pesanan", panel titles), 18sp/700
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle( // login subtitle / cart line price / success row emphasis, 17sp/700
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle( // menu item/cart item name, button label, 16sp/600
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle( // body default, option labels, 15sp/600
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle( // hints, secondary captions, 14sp/500
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle( // section labels, "sec" style, 13sp/600
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle( // small tags ("Wajib" badge), 12sp/600
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PosFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
