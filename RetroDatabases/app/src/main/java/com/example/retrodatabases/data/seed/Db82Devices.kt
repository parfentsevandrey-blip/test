package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1220 to 1224: a sailor's lodestone needle in Jacques de Vitry's history of the East, and two dated brass astrolabes kept at Oxford. */
val db82Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Jacques de Vitry's lodestone needle for sailors, in his Eastern history",
        originalTitle = "Historia Orientalis",
        author = "Jacques de Vitry",
        year = 1220,
        summary = "Jacques de Vitry describes an iron needle touched to a lodestone so that it swings toward the north star, a guide that sailors needed, in his history of the East.",
        details = "He was bishop of Acre, and the history was assembled in stages in the years about 1216 to 1227, so 1220 is a midpoint rather than a firm date. Alexander Neckam had described a magnetized needle for sailors in the late 1100s, so this passage is an early witness to the sea compass in Europe rather than the first.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The Isfahan astrolabe of 1221/2 by Muhammad ibn Abi Bakr",
        originalTitle = "Astrolabe, AH 618",
        author = "Muhammad ibn Abi Bakr",
        year = 1221,
        summary = "A brass astrolabe dated AH 618, which runs from 1221 to 1222, is kept at Oxford's History of Science Museum, and its maker is named as Muhammad ibn Abi Bakr of Isfahan.",
        details = "The instrument came to Oxford by the gift of Lewis Evans in 1924. An astrolabe of this kind has interchangeable plates, one for each latitude, so that a single body could serve several places.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The astrolabe of AH 621 by Muhammad ibn Fattuh al-Khamairi",
        originalTitle = "Astrolabe, AH 621",
        author = "Muhammad ibn Fattuh al-Khamairi",
        year = 1224,
        summary = "A brass astrolabe dated AH 621, which runs from 1224 to 1225, is kept at Oxford's History of Science Museum with Muhammad ibn Fattuh al-Khamairi named as its maker.",
        details = "The place recorded for the instrument is Seville, and it came to Oxford from the same donor, Lewis Evans, in 1924 as the Isfahan astrolabe of 1221/2. Its place makes it a western counterpart to the eastern instrument of the same years.",
        rare = true,
    ),

)
