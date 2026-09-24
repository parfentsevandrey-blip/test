package app.opal.core.data

/**
 * Presets for split tunnelling. Package names verified against the RuStore catalog
 * (rustore.ru/catalog/app/<package>) on 2026-09-24; apps not installed are simply ignored.
 */
object SplitTunnelPresets {

    /** «Российские банки и Госуслуги — напрямую»: these services often block Tor exits. */
    val russianBanksAndGovernment: List<String> =
        listOf(
            "ru.sberbankmobile", // СберБанк Онлайн
            "ru.vtb24.mobilebanking.android", // ВТБ Онлайн
            "com.idamob.tinkoff.android", // Т-Банк
            "ru.alfabank.mobile.android", // Альфа-Банк
            "ru.raiffeisennews", // Райффайзен Онлайн
            "ru.gazprombank.android.mobilebank.app", // Газпромбанк
            "logo.com.mbanking", // ПСБ
            "ru.rshb.dbo", // Россельхозбанк
            "com.openbank", // БМ-Банк (Открытие)
            "ru.sovcomcard.halva.v1", // Халва — Совкомбанк
            "ru.ozon.fintech.finance", // Ozon Банк
            "ru.nspk.mirpay", // Mir Pay
            "ru.nspk.sbpay", // СБПэй
            "ru.rostel", // Госуслуги
            "ru.gosuslugi.pos", // Госуслуги Решаем вместе
            "ru.fns.lkfl", // Налоги ФЛ
        )
}
