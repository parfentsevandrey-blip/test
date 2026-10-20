package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 530 to 540: Justinian's Digest and Institutes of 533 and the second edition of his Code of 534. */
val db151Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Justinian's Digest and Institutes, the case-law and textbook parts of his law corpus, issued in 533",
        originalTitle = "Digesta and Institutiones",
        author = "Emperor Justinian and his legal commission under Tribonian",
        year = 533,
        summary = "Justinian issued in 533 the Digest, fifty books of extracts from classical Roman jurists, and the Institutes, a four-book textbook for students of law.",
        details = "A commission under Tribonian drew the Digest from the writings of the classical jurists, and it took effect at the end of 533, the same year that the Institutes were issued. The Digest's extracts became the chief source of Roman case law for the courts of Byzantium and later for the law schools of Europe.",
    ),

    entry(
        databaseId = 151,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The second edition of Justinian's Code, issued on 16 November 534 and in force from 29 December 534",
        originalTitle = "Codex repetitae praelectionis",
        author = "Emperor Justinian and his legal commission",
        year = 534,
        summary = "Justinian's revised Code of 534 replaced the first edition of 529 and became the collection of imperial laws that later Byzantine and European lawyers used.",
        details = "The first edition of 529 was revised into a second edition, issued on 16 November 534 and in force from 29 December 534, which replaced the earlier text. The Code is arranged in twelve books by subject, and this revised form is the one that has come down to the present.",
    ),

)
