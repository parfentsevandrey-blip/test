package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * Additions to database 1: devices, systems, languages, tools and practices of the 1980s
 * that were missing from the first edition of the catalogue, including rare and regional records.
 * Assembled from five files, one per kind of record.
 */
val db1Additions: List<RecordEntity> =
    db1ExtraLanguages + db1ExtraSystems + db1ExtraDevices + db1ExtraTools + db1ExtraPractices
