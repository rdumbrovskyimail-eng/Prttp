// Путь: app/src/main/java/com/translator/app/presentation/therapy/CrisisResourcesScreen.kt
//
// Экран экстренной помощи. Открывается из кризисного баннера сессии.
//
// ВАЖНО: номера служб зависят от страны и со временем меняются. НЕ хардкодим
// здесь возможно-неверные телефоны. Подставь проверенные локальные контакты в
// CrisisResources.forLocale(...) под свой регион (и обновляй их). Универсальное
// сообщение «позвони в местную экстренную службу» работает везде.
//
// Кнопка звонка использует ACTION_DIAL (открывает набор номера, не звонит сам).
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.therapy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Один ресурс помощи. */
data class CrisisResource(
    val title: String,
    val subtitle: String,
    val phone: String? = null // null → только текст, без кнопки звонка
)

object CrisisResources {
    /**
     * Верни проверенные контакты под регион пользователя.
     * Сейчас — безопасные дефолты: универсальный совет + ПУСТЫЕ места под
     * локальные службы. ЗАПОЛНИ их актуальными номерами своей страны.
     */
    fun forLocale(): List<CrisisResource> = listOf(
        CrisisResource(
            title = "Экстренная служба",
            subtitle = "Если есть угроза жизни — позвони в местную службу экстренного вызова прямо сейчас.",
            phone = null // TODO: подставь номер экстренной службы своей страны
        ),
        CrisisResource(
            title = "Кризисная линия психологической помощи",
            subtitle = "Бесплатно, анонимно, круглосуточно — разговор с живым специалистом.",
            phone = null // TODO: подставь проверенный номер кризисной линии своего региона
        ),
        CrisisResource(
            title = "Близкий человек",
            subtitle = "Позвони тому, кому доверяешь, и скажи, что тебе сейчас тяжело. Не оставайся один.",
            phone = null
        )
    )
}

@Composable
fun CrisisResourcesScreen() {
    val context = LocalContext.current
    val resources = CrisisResources.forLocale()

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ты не один.", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Если тебе сейчас невыносимо тяжело или есть мысли о том, чтобы причинить себе вред — обратись за живой помощью. Рядом есть те, кто умеет помочь.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        resources.forEach { r ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(r.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!r.phone.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Позвонить: ${r.phone}") }
                    }
                }
            }
        }
    }
}
