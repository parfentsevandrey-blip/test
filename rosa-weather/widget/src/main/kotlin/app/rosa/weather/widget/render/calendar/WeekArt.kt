package app.rosa.weather.widget.render.calendar

import app.rosa.weather.core.model.Argb
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.render.WidgetLight
import kotlin.math.atan2

/**
 * How a week of the year looks: every one of the 52 has a picture of its own — the snowbound
 * fields and forests of the classic Russian landscapes, and what the season is lived as: Christmas
 * night by a wooden church, an evening in a log house with frost on the window, a ski track, the
 * straw lady burning at Maslenitsa, mimosa on a sill for the 8th of March, a paper boat in a
 * stream, a dacha opening, the thunderstorm of early May, a beach, a campfire by a tent, a café
 * window on an autumn street, a castle hall with a fire, a book by a lamp, a skating rink, the
 * New Year's tree.
 *
 * [sky] runs the picture's key colours from top to bottom (for a landscape, its sky from zenith to
 * horizon); the light in it — the sun, the moon, a lamp or a fire — sits at [bodyX], [bodyY] (0..1
 * of the scene) and lights the glass ([light]); [accent] marks today and the weekends; [dark] says
 * light type reads over it. In the dark theme the picture is taken toward night by [nightfall]:
 * all the way for a day scene, a touch for one already at night. [motion] is what moves over it.
 */
internal data class WeekArt(
    /** 1..52: the week of the year. */
    val week: Int,
    val sky: List<Argb>,
    val accent: Argb,
    val dark: Boolean,
    val bodyX: Float,
    val bodyY: Float,
    val bodyPower: Float,
    val bodyColor: Argb,
    val isMoon: Boolean = false,
    val nightfall: Float = if (isMoon) 0.35f else 1f,
    val motion: LiveWeather? = null,
) {
    /** How far the year has come by this week: the snow, the leaves, the ice, the frost. */
    val season: Season = SeasonClock.of(week)

    /** The zenith, the horizon and the light's glow, for palettes built from this picture. */
    val zenith: Argb get() = sky.first()
    val horizon: Argb get() = sky.last()

    /** The painted light as the glass sees it, from the centre of a [w] × [h] pane. */
    fun light(w: Float, h: Float): WidgetLight = WidgetLight(
        angle = atan2(bodyY * h - h / 2f, bodyX * w - w / 2f),
        power = bodyPower,
        color = Argb.White.lerp(bodyColor, 0.65f),
        sky = zenith,
    )

    companion object {
        /** [week]'s picture (1..52; others wrap into the year). */
        fun of(week: Int): WeekArt = WEEKS[(week - 1).mod(SeasonClock.WEEKS)]

        /** The week in the middle of [month]: the picture a page of that month shows when it isn't today's. */
        fun ofMonth(month: Int): WeekArt = of(SeasonClock.weekOf(java.time.LocalDate.of(2025, (month - 1).mod(12) + 1, 15)))

        private fun hex(vararg colors: Long) = colors.map { Argb.hex(it) }

        private fun a(rgb: Long) = Argb.hex(rgb)

        private val WEEKS = listOf(
            // 1. Рождество: a wooden church in the snow at night under the Christmas star, lanterns along the path.
            WeekArt(1, hex(0x07102E, 0x16275E, 0x34498A, 0x6378B4), a(0xF2B84A), dark = true, bodyX = 0.28f, bodyY = 0.13f, bodyPower = 0.55f, bodyColor = a(0xFFF0C8), isMoon = true, motion = LiveWeather.SnowLight),
            // 2. «Мороз и солнце»: a frosty morning, snowbound spruces, the New Year's lights still on the house.
            WeekArt(2, hex(0x24569E, 0x4F86C9, 0x9DBFE6, 0xF3DCC4), a(0x5A9BFF), dark = true, bodyX = 0.58f, bodyY = 0.3f, bodyPower = 0.9f, bodyColor = a(0xFFE3B5), motion = LiveWeather.Frost),
            // 3. Крещенские морозы: the Epiphany cross cut in the river's ice, birches white with rime, sun dogs.
            WeekArt(3, hex(0x3A68AE, 0x7FA8DA, 0xD2E0F0, 0xF4DCC6), a(0x4A86D8), dark = false, bodyX = 0.7f, bodyY = 0.3f, bodyPower = 0.85f, bodyColor = a(0xFFE8C8), motion = LiveWeather.Frost),
            // 4. Вечер в избе: a log house by candlelight, tea and jam, the cat at a frosted window on the moonlit village.
            WeekArt(4, hex(0x4A3020, 0x6E4A2C, 0x8A5E38, 0x3A2A1E), a(0xFFB15A), dark = true, bodyX = 0.36f, bodyY = 0.7f, bodyPower = 0.6f, bodyColor = a(0xFFC878), nightfall = 0.45f, motion = LiveWeather.Embers),
            // 5. Лыжня: a ski track through a sunny forest, skis and poles stuck in the snow.
            WeekArt(5, hex(0x2F66B8, 0x6CA2DE, 0xC0D8F0, 0xF4E6D4), a(0xE0483A), dark = false, bodyX = 0.2f, bodyY = 0.32f, bodyPower = 0.9f, bodyColor = a(0xFFE8C0), motion = LiveWeather.Frost),
            // 6. Метель: a blizzard over a village street, a lamp burning in it.
            WeekArt(6, hex(0x4A5270, 0x7A8098, 0xA8ACBA, 0xC4C4CA), a(0xFFB85C), dark = true, bodyX = 0.74f, bodyY = 0.4f, bodyPower = 0.45f, bodyColor = a(0xFFD08A), motion = LiveWeather.Blizzard),
            // 7. After Grabar's «Февральская лазурь»: white birches against a deep azure sky.
            WeekArt(7, hex(0x1B4FA8, 0x3E80D8, 0x9CC4EE, 0xE6EFF8), a(0x3C8CFF), dark = true, bodyX = 0.12f, bodyY = 0.06f, bodyPower = 0.8f, bodyColor = a(0xFFF4DC), motion = LiveWeather.Frost),
            // 8. Масленица: the straw lady burning on a snowy field at sunset, a pole hung with ribbons.
            WeekArt(8, hex(0x3A4A8A, 0xA86A8E, 0xF4A06C, 0xFFD6A0), a(0xFF6A3A), dark = true, bodyX = 0.82f, bodyY = 0.5f, bodyPower = 0.85f, bodyColor = a(0xFFC890), motion = LiveWeather.Embers),
            // 9. Капель: icicles along a carved eave in the March sun, a starling house on the birch.
            WeekArt(9, hex(0x2A68C6, 0x68A2E8, 0xB8D6F4, 0xEAF2F8), a(0x2F8FE0), dark = false, bodyX = 0.8f, bodyY = 0.16f, bodyPower = 1f, bodyColor = a(0xFFF4DC), motion = LiveWeather.Drips),
            // 10. Мимоза: a sunny sill for the 8th of March — mimosa in a vase, a card, the town thawing outside.
            WeekArt(10, hex(0xF2E8DA, 0xE8D8C4, 0xCFE0F0, 0xF6F0E6), a(0xE8A81E), dark = false, bodyX = 0.74f, bodyY = 0.2f, bodyPower = 0.9f, bodyColor = a(0xFFF0D0), motion = LiveWeather.Motes),
            // 11. «Грачи прилетели»: the thaw, rooks back in the birches, puddles full of sky.
            WeekArt(11, hex(0x3C78CC, 0x7FB0E6, 0xC4DCF2, 0xEDF1F2), a(0x2FA38F), dark = false, bodyX = 0.62f, bodyY = 0.14f, bodyPower = 0.95f, bodyColor = a(0xFFF3D6), motion = LiveWeather.Birds),
            // 12. Ручьи: a paper boat sailing a stream through the melting snow.
            WeekArt(12, hex(0x4A86D6, 0x8EBCEC, 0xD4E6F4, 0xF0F0EC), a(0x2F8AD0), dark = false, bodyX = 0.3f, bodyY = 0.12f, bodyPower = 0.9f, bodyColor = a(0xFFF2D8), motion = LiveWeather.Drips),
            // 13. Подснежники: snowdrops coming up through the last snow and last year's leaves.
            WeekArt(13, hex(0xA8C0DA, 0xC8D6E2, 0xD8D8CE, 0x8A7A5E), a(0x4E9A48), dark = false, bodyX = 0.78f, bodyY = 0.08f, bodyPower = 0.7f, bodyColor = a(0xFFF4DC), motion = LiveWeather.Motes),
            // 14. Ледоход: the ice breaking up on the river under a town on its high bank, gulls.
            WeekArt(14, hex(0x6A84A8, 0xA0B4CC, 0xD0D8E0, 0xE8E8E2), a(0x3A78B8), dark = false, bodyX = 0.28f, bodyY = 0.18f, bodyPower = 0.6f, bodyColor = a(0xFFF4DC), motion = LiveWeather.Birds),
            // 15. After Levitan's «Весна. Большая вода»: flood water mirroring birches, a rainbow after the shower.
            WeekArt(15, hex(0x6A95C8, 0xA3C3E4, 0xD8E6EE, 0xEEF1E8), a(0x3FAE55), dark = false, bodyX = 0.66f, bodyY = 0.16f, bodyPower = 0.75f, bodyColor = a(0xFFF1CF), motion = LiveWeather.RainLight),
            // 16. Пасха: the Easter table — a kulich, painted eggs, pussy willow — by a window on the church.
            WeekArt(16, hex(0xDCE6E0, 0xC8D6CE, 0xB8D0E8, 0xF4F0E8), a(0xD8402E), dark = false, bodyX = 0.2f, bodyY = 0.2f, bodyPower = 0.8f, bodyColor = a(0xFFF2D6), motion = LiveWeather.Motes),
            // 17. Дачный сезон: the dacha opened for the year — beds dug, the greenhouse, birches in a green haze.
            WeekArt(17, hex(0x4A8ADA, 0x92C2EE, 0xD6E8F4, 0xF4F0E4), a(0x4AA84A), dark = false, bodyX = 0.8f, bodyY = 0.12f, bodyPower = 0.95f, bodyColor = a(0xFFF2D6), motion = LiveWeather.Birds),
            // 18. «Люблю грозу в начале мая»: lightning out of a dark sky over young green fields, a rainbow.
            WeekArt(18, hex(0x2A3046, 0x485068, 0x7A8296, 0xC8D0B8), a(0x7CC84E), dark = true, bodyX = 0.12f, bodyY = 0.1f, bodyPower = 0.55f, bodyColor = a(0xFFF4DC), motion = LiveWeather.Storm),
            // 19. Черёмуха: bird cherry in white flower over a river, a footbridge.
            WeekArt(19, hex(0x5A92D8, 0x9CC4EC, 0xD8E8F4, 0xF0F2F2), a(0x5A7FD8), dark = false, bodyX = 0.72f, bodyY = 0.14f, bodyPower = 0.9f, bodyColor = a(0xFFF6E2), motion = LiveWeather.Petals),
            // 20. An apple orchard in blossom, dandelions in the new grass.
            WeekArt(20, hex(0x5C9BE0, 0x9FC8EE, 0xDCE8F2, 0xF6E6E2), a(0xE8618C), dark = false, bodyX = 0.7f, bodyY = 0.14f, bodyPower = 0.9f, bodyColor = a(0xFFEBC9), motion = LiveWeather.Petals),
            // 21. Сирень: lilac in flower by an old wooden house at evening, a bench under it.
            WeekArt(21, hex(0x5A74B8, 0xA496C8, 0xF0C6B8, 0xF8E0C6), a(0x9A5AC8), dark = false, bodyX = 0.16f, bodyY = 0.42f, bodyPower = 0.75f, bodyColor = a(0xFFD8A8), motion = LiveWeather.Lilac),
            // 22. Воздушный змей: a kite high over a hill of dandelion clocks.
            WeekArt(22, hex(0x2A70D0, 0x6AA8EC, 0xB8D8F4, 0xE8F0F4), a(0xE84A3A), dark = false, bodyX = 0.16f, bodyY = 0.12f, bodyPower = 1f, bodyColor = a(0xFFF6E0), motion = LiveWeather.Fluff),
            // 23. An old oak on a flowering meadow under towering summer clouds.
            WeekArt(23, hex(0x2A6ACC, 0x69A2E6, 0xB9D8F2, 0xE4EFF4), a(0x2E7FD9), dark = true, bodyX = 0.74f, bodyY = 0.1f, bodyPower = 1f, bodyColor = a(0xFFF6DE), motion = LiveWeather.Butterflies),
            // 24. Тополиный пух: a town courtyard under poplars, the fluff drifting in the sun.
            WeekArt(24, hex(0x4A8ADA, 0x8ABCEC, 0xCCE2F2, 0xF0EEE6), a(0x3A8AD8), dark = false, bodyX = 0.62f, bodyY = 0.1f, bodyPower = 0.95f, bodyColor = a(0xFFF4DA), motion = LiveWeather.Fluff),
            // 25. Белые ночи: the river in the white night, a bridge raised, scarlet sails going by.
            WeekArt(25, hex(0x6E7CB0, 0xC4A6C2, 0xF4C8B8, 0xFBE4CC), a(0xE0403A), dark = false, bodyX = 0.84f, bodyY = 0.56f, bodyPower = 0.5f, bodyColor = a(0xFFD8B0), motion = LiveWeather.Mist),
            // 26. Сенокос: haymaking on an evening meadow, the stacks going up, swallows over it.
            WeekArt(26, hex(0x4A7AC8, 0x9AB8E0, 0xF0D8B0, 0xFFE2AA), a(0xE0A030), dark = false, bodyX = 0.76f, bodyY = 0.42f, bodyPower = 0.9f, bodyColor = a(0xFFD89A), motion = LiveWeather.Butterflies),
            // 27. Иван Купала: wreaths with candles floating down the dark river, a bonfire on the bank.
            WeekArt(27, hex(0x080C28, 0x18204E, 0x2C386C, 0x4A5288), a(0xFFB24A), dark = true, bodyX = 0.2f, bodyY = 0.62f, bodyPower = 0.8f, bodyColor = a(0xFFB860), isMoon = true, motion = LiveWeather.Fireflies),
            // 28. After Shishkin's «Рожь»: rye at sunset, a road through it, pines standing tall.
            WeekArt(28, hex(0x4A5FA8, 0xB7809D, 0xFFAE78, 0xFFD89A), a(0xFF9A2E), dark = true, bodyX = 0.6f, bodyY = 0.5f, bodyPower = 0.9f, bodyColor = a(0xFFC77A), motion = LiveWeather.Fireflies),
            // 29. Море: a beach — a striped umbrella, a deck chair, a sail, a lighthouse on the point, gulls.
            WeekArt(29, hex(0x1E6AC8, 0x5AA2E8, 0xA8D4F4, 0xE0F0F8), a(0x0A96C8), dark = false, bodyX = 0.8f, bodyY = 0.12f, bodyPower = 1f, bodyColor = a(0xFFF8E6), motion = LiveWeather.Birds),
            // 30. На даче: tea from the samovar on the veranda, strawberries, the hammock in the garden.
            WeekArt(30, hex(0x5A94D8, 0x9CC4EC, 0xDCEAF0, 0xF6EAD8), a(0xE0403A), dark = false, bodyX = 0.82f, bodyY = 0.18f, bodyPower = 0.9f, bodyColor = a(0xFFF0D0), motion = LiveWeather.Butterflies),
            // 31. Костёр в лесу: a campfire and a tent among the pines by a lake at dusk.
            WeekArt(31, hex(0x16244E, 0x34427A, 0x8A7A98, 0xE0A080), a(0xFF8A3A), dark = true, bodyX = 0.38f, bodyY = 0.8f, bodyPower = 0.8f, bodyColor = a(0xFFB060), nightfall = 0.45f, motion = LiveWeather.Embers),
            // 32. Falling stars over a still lake: the Perseids, the Milky Way, haystacks on the shore.
            WeekArt(32, hex(0x060A22, 0x101944, 0x232D66, 0x3A4278), a(0xB9A8FF), dark = true, bodyX = 0.8f, bodyY = 0.34f, bodyPower = 0.3f, bodyColor = a(0xFFE9C4), isMoon = true, motion = LiveWeather.Night),
            // 33. Яблочный Спас: an orchard heavy with apples, baskets full, a ladder in a tree, the church beyond.
            WeekArt(33, hex(0x4A86D0, 0x8AB8E8, 0xD0E2F0, 0xF2EEDC), a(0xD8342A), dark = false, bodyX = 0.76f, bodyY = 0.14f, bodyPower = 0.9f, bodyColor = a(0xFFF0D0), motion = LiveWeather.Butterflies),
            // 34. Туман над озером: a boat at a little pier at dawn, the mist lying on the water.
            WeekArt(34, hex(0x8A9AC0, 0xC8B8C8, 0xF2D2C4, 0xFCE8D8), a(0x5A86C0), dark = false, bodyX = 0.7f, bodyY = 0.44f, bodyPower = 0.6f, bodyColor = a(0xFFE0C0), motion = LiveWeather.Mist),
            // 35. Подсолнухи: a field of sunflowers at sunset.
            WeekArt(35, hex(0x3A56A4, 0x8A78AE, 0xF0A070, 0xFFD890), a(0xF2B01E), dark = true, bodyX = 0.5f, bodyY = 0.5f, bodyPower = 0.9f, bodyColor = a(0xFFC880), motion = LiveWeather.Motes),
            // 36. Журавли: cranes going south over the stubble, gossamer on the air of the Indian summer.
            WeekArt(36, hex(0x3A7ACC, 0x7AB0E6, 0xC8DCEC, 0xF0ECDC), a(0xD89A2E), dark = false, bodyX = 0.2f, bodyY = 0.15f, bodyPower = 0.9f, bodyColor = a(0xFFF0D0), motion = LiveWeather.Birds),
            // 37. Грибная пора: mushrooms in the moss under the birches, a basket filling.
            WeekArt(37, hex(0x8AA070, 0xB8B880, 0x7A6A40, 0x4A4A2A), a(0xD8402A), dark = true, bodyX = 0.74f, bodyY = 0.08f, bodyPower = 0.6f, bodyColor = a(0xFFE8B0), motion = LiveWeather.LeavesGold),
            // 38. After Levitan's «Золотая осень»: golden birches along a blue river.
            WeekArt(38, hex(0x3F7ED0, 0x7FB0E6, 0xC8DCEC, 0xEEEDE2), a(0xE6A21E), dark = false, bodyX = 0.7f, bodyY = 0.13f, bodyPower = 0.9f, bodyColor = a(0xFFE7B5), motion = LiveWeather.LeavesGold),
            // 39. Кафе у окна: coffee at a café window on an autumn street, the lamps lit, rain on the glass.
            WeekArt(39, hex(0x2A2020, 0x4A3A3A, 0x8A6A5A, 0x3A2A22), a(0xE8A04A), dark = true, bodyX = 0.5f, bodyY = 0.06f, bodyPower = 0.6f, bodyColor = a(0xFFC878), nightfall = 0.5f, motion = LiveWeather.RainLight),
            // 40. A misty morning in a red and gold forest, the sun's rays through the trees.
            WeekArt(40, hex(0x6F7BA6, 0xB99A96, 0xEBBF9E, 0xF6D7B4), a(0xF26A2E), dark = true, bodyX = 0.6f, bodyY = 0.34f, bodyPower = 0.75f, bodyColor = a(0xFFD2A0), motion = LiveWeather.LeavesRed),
            // 41. Листопад в парке: an alley under orange maples, lamps and a bench, the leaves coming down.
            WeekArt(41, hex(0x8A9AB8, 0xD8B898, 0xE8A060, 0xB86A3A), a(0xE8702A), dark = true, bodyX = 0.5f, bodyY = 0.34f, bodyPower = 0.8f, bodyColor = a(0xFFD8A0), motion = LiveWeather.LeavesOrange),
            // 42. Камин в замке: a fire in a castle hall, candles on the mantel, rain at the tall window.
            WeekArt(42, hex(0x2A2426, 0x3E3434, 0x6A4A3A, 0x2A2020), a(0xFF8A3A), dark = true, bodyX = 0.32f, bodyY = 0.72f, bodyPower = 0.9f, bodyColor = a(0xFFA050), nightfall = 0.35f, motion = LiveWeather.Embers),
            // 43. Осенний дождь: a town street in the rain at dusk, umbrellas, the lamps in the puddles.
            WeekArt(43, hex(0x343C50, 0x545C70, 0x8A8A94, 0xA8A0A0), a(0xF2B84A), dark = true, bodyX = 0.7f, bodyY = 0.34f, bodyPower = 0.5f, bodyColor = a(0xFFC878), motion = LiveWeather.RainLight),
            // 44. Первый иней: fallen leaves and grass white with the first hoarfrost at sunrise.
            WeekArt(44, hex(0xE8C8B0, 0xD8C8C8, 0xB8C0D0, 0x8A7A70), a(0xC8603A), dark = false, bodyX = 0.8f, bodyY = 0.24f, bodyPower = 0.7f, bodyColor = a(0xFFD8B0), motion = LiveWeather.Frost),
            // 45. Предзимье: a dark river at dusk, bare trees, crows.
            WeekArt(45, hex(0x46526A, 0x77849A, 0xB1B3B6, 0xD9CDB5), a(0x86B3D6), dark = true, bodyX = 0.55f, bodyY = 0.5f, bodyPower = 0.25f, bodyColor = a(0xFFE2B8), motion = LiveWeather.SnowLight),
            // 46. Вечер с книгой: an armchair and a lamp, tea, the cat asleep, the first snow at the window.
            WeekArt(46, hex(0x2E3426, 0x4A4632, 0x8A6A3A, 0x2A2418), a(0xE8A050), dark = true, bodyX = 0.74f, bodyY = 0.5f, bodyPower = 0.6f, bodyColor = a(0xFFC878), nightfall = 0.45f, motion = LiveWeather.Motes),
            // 47. Первый снег: the first snow on a village street, the rowan still red, the last leaves yellow.
            WeekArt(47, hex(0x8A96AE, 0xB8C0CC, 0xDCDCDA, 0xEEE6DC), a(0xD8483A), dark = false, bodyX = 0.3f, bodyY = 0.2f, bodyPower = 0.4f, bodyColor = a(0xFFF2E0), motion = LiveWeather.SnowLight),
            // 48. Снегири: bullfinches on a snowy rowan branch.
            WeekArt(48, hex(0xA8B8D0, 0xC8D4E2, 0xE0E6EE, 0xF0F2F6), a(0xE0504A), dark = false, bodyX = 0.8f, bodyY = 0.14f, bodyPower = 0.5f, bodyColor = a(0xFFF0DC), motion = LiveWeather.SnowLight),
            // 49. Каток: an evening skating rink in the park, strings of lights over the ice.
            WeekArt(49, hex(0x0E1A44, 0x24356E, 0x4A5C94, 0x8A8CB0), a(0x5AB8FF), dark = true, bodyX = 0.5f, bodyY = 0.3f, bodyPower = 0.5f, bodyColor = a(0xFFE0B0), nightfall = 0.4f, motion = LiveWeather.Twinkle),
            // 50. A village asleep under the full moon, warm windows, snow falling.
            WeekArt(50, hex(0x0A1230, 0x1A2A5C, 0x31467E, 0x566CA6), a(0xFF8A70), dark = true, bodyX = 0.64f, bodyY = 0.2f, bodyPower = 0.45f, bodyColor = a(0xE8EEFF), isMoon = true, motion = LiveWeather.SnowHeavy),
            // 51. Морозное окно: frost ferns on the glass, cocoa and tangerines on the sill, lights along the frame.
            WeekArt(51, hex(0x3A2A2E, 0x1E2A50, 0x3A4A7A, 0xE8E2D8), a(0xFF8A3A), dark = true, bodyX = 0.5f, bodyY = 0.1f, bodyPower = 0.6f, bodyColor = a(0xFFD08A), nightfall = 0.45f, motion = LiveWeather.Twinkle),
            // 52. Новогодняя ночь: the tree and the presents, fireworks over the town in the window.
            WeekArt(52, hex(0x1A3434, 0x14282A, 0x2A1E2A, 0x3A2A1E), a(0xFFC23A), dark = true, bodyX = 0.24f, bodyY = 0.2f, bodyPower = 0.7f, bodyColor = a(0xFFD66A), nightfall = 0.35f, motion = LiveWeather.Twinkle),
        )
    }
}
