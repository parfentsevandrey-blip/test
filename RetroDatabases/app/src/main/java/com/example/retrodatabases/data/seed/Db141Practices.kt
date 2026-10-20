package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 630 to 640: the Qur'an gathered into one written collection under Abu Bakr, placed at 633 because the sources give no year. */
val db141Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.PRACTICE,
        section = "Arab",
        title = "The Qur'an gathered from scattered sheets into one written collection under Abu Bakr, placed at 633",
        originalTitle = "Jam al-Quran",
        author = "Zayd ibn Thabit, as the tradition names him, for the caliph Abu Bakr",
        year = 633,
        summary = "Tradition says the caliph Abu Bakr had the Qur'an gathered from separate sheets into one written collection, and that the copy was later kept by Hafsa.",
        details = "The sources do not date this collection; 633 is the year used here, the middle of Abu Bakr's caliphate from 632 to 634, and it is an anchor rather than the year the collection was made. Accounts differ on the method and on whether a single text was first fixed under Abu Bakr or under Uthman, so the record covers the tradition of Abu Bakr only.",
    ),

)
