package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * Все записи базы 1: языки, системы, устройства, инструменты и практики 1980-х,
 * а также пояснения, примеры, проекты, флекс-проекты и кросс-код эксперименты.
 * Порядок показа задаёт экран (вид → раздел → год), а не порядок в этих списках.
 */
val db1Records: List<RecordEntity> =
    db1Languages + db1Systems + db1Devices + db1Tools + db1Practices +
        db1Guides + db1Examples + db1Projects + db1Flex + db1Cross + db1Additions
