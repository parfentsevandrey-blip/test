package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * Дополнения к базе 1: устройства, системы, языки, инструменты и практики 1980-х,
 * которых не было в первой редакции каталога. Здесь же редкие и региональные записи.
 * Собираются из пяти файлов по видам; каждый файл описывает один вид записей.
 */
val db1Additions: List<RecordEntity> =
    db1ExtraLanguages + db1ExtraSystems + db1ExtraDevices + db1ExtraTools + db1ExtraPractices
