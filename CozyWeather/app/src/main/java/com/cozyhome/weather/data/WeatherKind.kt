package com.cozyhome.weather.data

/** Grouping of WMO weather codes into the cozy scenes the app can render. */
enum class WeatherKind {
    CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER;

    companion object {
        fun fromCode(code: Int): WeatherKind = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57, in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDER
            else -> CLOUDY
        }
    }
}

fun WeatherKind.emoji(isDay: Boolean): String = when (this) {
    WeatherKind.CLEAR -> if (isDay) "☀️" else "🌙"
    WeatherKind.PARTLY -> if (isDay) "⛅" else "☁️"
    WeatherKind.CLOUDY -> "☁️"
    WeatherKind.FOG -> "🌫️"
    WeatherKind.RAIN -> "🌧️"
    WeatherKind.SNOW -> "❄️"
    WeatherKind.THUNDER -> "⛈️"
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1 -> "Почти ясно"
    2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55 -> "Морось"
    56, 57 -> "Ледяная морось"
    61 -> "Небольшой дождь"
    63 -> "Дождь"
    65 -> "Сильный дождь"
    66, 67 -> "Ледяной дождь"
    71 -> "Небольшой снег"
    73 -> "Снег"
    75 -> "Сильный снег"
    77 -> "Снежные зёрна"
    80, 81 -> "Ливень"
    82 -> "Сильный ливень"
    85, 86 -> "Снегопад"
    95 -> "Гроза"
    96, 99 -> "Гроза с градом"
    else -> "Облачно"
}

/** A cozy one-liner for each scene — part of the game-like mood. */
fun WeatherKind.cozyLine(isDay: Boolean): String = when (this) {
    WeatherKind.CLEAR -> if (isDay) "Вентилятор жужжит, лимонад ждёт" else "Светлячки вышли на смену"
    WeatherKind.PARTLY -> "Облака играют в прятки с солнцем"
    WeatherKind.CLOUDY -> "Идеально для чая с пледом"
    WeatherKind.FOG -> "Мир укрылся одеялом тумана"
    WeatherKind.RAIN -> "Камин потрескивает, дождь шумит"
    WeatherKind.SNOW -> "Свеча горит, снежинки танцуют"
    WeatherKind.THUNDER -> "Гром гремит — а дома тепло"
}
