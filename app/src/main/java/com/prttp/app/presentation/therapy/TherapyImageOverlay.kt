package com.prttp.app.presentation.therapy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import com.prttp.app.data.TherapyImage
import com.prttp.app.therapy.ImageTheme

/**
 * Визуальный оверлей терапевтического изображения.
 * Появляется снизу экрана когда ИИ вызывает show_therapeutic_image.
 * Исчезает через 40 секунд или при нажатии.
 */
@Composable
fun TherapyImageOverlay(
    image: TherapyImage?,
    isLoading: Boolean,
    currentTheme: ImageTheme,
    onThemeChange: (ImageTheme) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = image != null || isLoading

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(600)) { it / 2 } + fadeIn(tween(600)),
        exit  = slideOutVertically(tween(400)) { it / 2 } + fadeOut(tween(400)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D2020))
                .clickable { onDismiss() }
        ) {
            if (isLoading && image == null) {
                // Spinner пока грузится
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6FE3C9),
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (image != null) {
                Column {
                    // Фотография
                    AsyncImage(
                        model = image.url,
                        contentDescription = image.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )

                    // Градиентный переход фото → текст
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFF0D2020))
                                )
                            )
                    )

                    // Подпись от ИИ
                    if (image.caption.isNotBlank()) {
                        Text(
                            text = image.caption,
                            color = Color(0xCCCFE3E0),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    // Подсказка «нажми чтобы скрыть»
                    Text(
                        text = "нажми чтобы скрыть",
                        color = Color(0x446FE3C9),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )

                    // ── Выбор темы ──
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ImageTheme.entries.toList()) { theme ->
                            val selected = theme == currentTheme
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) Color(0x446FE3C9) else Color(0x1A6FE3C9)
                                    )
                                    .border(
                                        width = if (selected) 1.dp else 0.dp,
                                        color = if (selected) Color(0xFF6FE3C9) else Color.Transparent,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onThemeChange(theme) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${theme.emoji} ${theme.label}",
                                    color = if (selected) Color(0xFF6FE3C9) else Color(0x99CFE3E0),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}