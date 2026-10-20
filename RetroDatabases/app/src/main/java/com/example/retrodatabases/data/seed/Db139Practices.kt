package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 650 to 660: the burning of rival Qur'an codices that the hadith accounts tie to Uthman's standard text. */
val db139Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.PRACTICE,
        section = "Arabic",
        title = "The burning of rival Qur'an codices under Uthman, as the hadith accounts tell it, about 650",
        originalTitle = "al-Masahif",
        author = "Uthman ibn Affan",
        year = 650,
        summary = "Uthman is said to have ordered rival Qur'an sheets and codices burned once the standard text was made, and the hadith accounts record the order.",
        details = "The hadith accounts say divergent sheets were to be burned once copies of the standard text had gone to every region, and the order is tied here to the standardization of about 650. Scholars differ on whether the burning was carried out as told, and the reports differ in their details, so the event rests on a tradition and not on a dated record.",
    ),

)
