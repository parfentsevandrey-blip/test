package com.example.calendarwidget.ui.theme

import androidx.compose.ui.graphics.Color

// Surfaces — раздел 7 (Design Tokens)
val TextPrimaryDark = Color(0xFFF1F1F6)
val TextPrimaryLight = Color(0xFF1B1B23)
val TextMutedDark = Color(0xFFFFFFFF).copy(alpha = 0.42f)
val TextMutedLight = Color(0xFF16141C).copy(alpha = 0.45f)

// App background gradient (раздел 7 — Фон приложения)
val AppBgTop = Color(0xFF14131B)
val AppBgBottom = Color(0xFF0A0A0E)

// Card surfaces
val CardFill = Color(0xFFFFFFFF).copy(alpha = 0.045f)
val CardStroke = Color(0xFFFFFFFF).copy(alpha = 0.07f)
val Hairline = Color(0xFFFFFFFF).copy(alpha = 0.10f)

// Accent palette
val DefaultAccent = Color(0xFF7C9CFF)

// Category colours
val CatPersonal = Color(0xFFC9A6FF)
val CatHealth = Color(0xFFFF8A6B)
val CatSocial = Color(0xFF54E6C0)
