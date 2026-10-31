package com.example.pdf_reader.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_reader.R

/**
 * Clean Custom Splash Screen for OpenPDF.
 *
 * Designed with a pure white background, smooth entry animation,
 * authentic OpenPDF logo, and branding slogans:
 * - Title: OpenPDF
 * - Slogan: Open Source • Ad Free
 * - Footer: Fast • Lightweight • Private
 */
@Composable
fun OpenPdfSplashScreen(
    modifier: Modifier = Modifier,
) {
    val transitionState = remember {
        MutableTransitionState(initialState = false).apply {
            targetState = true
        }
    }
    val transition = rememberTransition(transitionState, label = "SplashScreenTransition")

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
        label = "AlphaAnimation",
    ) { state ->
        if (state) 1f else 0f
    }

    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
        label = "ScaleAnimation",
    ) { state ->
        if (state) 1f else 0.94f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Centered Branding: Logo + App Name + Slogan
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
                .padding(horizontal = 24.dp),
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                shadowElevation = 8.dp,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.openpdf_logo_img),
                    contentDescription = "OpenPDF Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name "OpenPDF"
            Text(
                text = "OpenPDF",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                letterSpacing = (-0.5).sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slogan: "Open Source • Ad Free"
            Text(
                text = "Open Source • Ad Free",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE11D48),
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Bottom Footer: "Fast • Lightweight • Private"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 36.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "Fast • Lightweight • Private",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
