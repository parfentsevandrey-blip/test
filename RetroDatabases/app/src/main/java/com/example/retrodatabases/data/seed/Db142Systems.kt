package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 620 to 630: the Constitution of Medina, the Wude code, Taizong's seizure of power in 626 and Heraclius's victory at Nineveh in 627. */
val db142Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.SYSTEM,
        section = "Arabic",
        title = "The Constitution of Medina, the written agreement of the migrants, the supporters and the Jewish tribes, usually dated to 622",
        originalTitle = "Sahifat al-Madina",
        author = "Muhammad, by tradition",
        year = 622,
        summary = "The Constitution of Medina sets out the mutual duties of the Muslim migrants, the Medinan supporters and the Jewish tribes of Medina, and is usually dated to 622.",
        details = "The text survives in later sources, chiefly the biographies of Muhammad through Ibn Hisham's recension of Ibn Ishaq's work, which gives it no chain of narration. Accounts date it to 622, though some place the drafting in 623, and scholars still dispute whether it is one document or a set of separate agreements.",
        rare = true,
    ),

    entry(
        databaseId = 142,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "The Wude code, the first law code of the Tang dynasty, promulgated in 624",
        originalTitle = "Wude lu ling",
        author = "The Tang court",
        year = 624,
        summary = "The Tang court issued its first law code in 624, the seventh year of the Wude era, and it was followed by the Zhenguan code of 637.",
        details = "The code is named for the Wude era, whose seventh year is 624. The Zhenguan code of 637 followed it as the next major Tang code.",
        rare = true,
    ),

    entry(
        databaseId = 142,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "Taizong's seizure of power at the Xuanwu Gate in 626, when he killed his brothers and took the Tang throne",
        originalTitle = "Xuanwumen zhi bian",
        author = "Li Shimin, later Emperor Taizong",
        year = 626,
        summary = "Li Shimin killed his elder brother, the crown prince, and his younger brother at the Xuanwu Gate in 626, and within weeks his father gave him the throne.",
        details = "The killings took place on 4 July 626, and Li Shimin was then named crown prince, with Emperor Gaozu giving up the throne to him in August. Accounts of the incident differ in detail, and the official histories that describe it were written under the victorious side.",
    ),

    entry(
        databaseId = 142,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Heraclius's victory over the Persians at Nineveh on 12 December 627, followed by a civil war in Persia",
        originalTitle = "Battle of Nineveh (627)",
        author = "Heraclius",
        year = 627,
        summary = "Heraclius defeated the Persian army near Nineveh on 12 December 627, and the Persian commander Rhahzadh was killed in the fighting.",
        details = "A civil war in Persia followed the battle, and Khosrow II was overthrown in 628, which weakened the Sasanian state. The sizes of the armies given by later writers are disputed, and so is the story of a single combat between Heraclius and Rhahzadh.",
    ),

)
