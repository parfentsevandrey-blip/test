package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1310 to 1320: Wang Zhen's wooden type, revolving case, bellows, chain pump and farm machines of 1313, and an English horary quadrant of 1311. */
val db73Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Wang Zhen's wooden movable type, carved and sawn into pieces",
        originalTitle = "Mu huozi",
        author = "Wang Zhen",
        year = 1313,
        summary = "Wang Zhen described printing with movable type of carved wood in his Nong shu, with each character cut from a block and sawn into a separate piece.",
        details = "Each piece could be set in a page, inked, printed and set again for another book. The Nong shu is dated 1313 in the usual account, but another account places the book about 1303, so the year given is the one of record.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Wang Zhen's revolving case of wooden type, grouped by rhyme",
        originalTitle = "Zhuanlun paizi jia",
        author = "Wang Zhen",
        year = 1313,
        summary = "Wang Zhen designed a revolving case that held wooden type pieces in sections, grouped mainly by rhyme, so that a compositor could reach a character without walking about the shop.",
        details = "The case is shown in the printing chapter of the Nong shu, and its orderly layout was meant to speed the search for each character. The date 1313 carries the same dispute about 1303 as the book.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Metal",
        title = "Bellows blown by a waterwheel for an iron furnace, in the Nong shu",
        originalTitle = "Shui pai",
        author = "Wang Zhen",
        year = 1313,
        summary = "The Nong shu of Wang Zhen includes an illustration of water-driven bellows that fed air to an iron-smelting furnace.",
        details = "The 1313 text has not survived, so the illustration is known from later printings and manuscripts. The book is dated 1313 here, and another account puts it about 1303.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Water",
        title = "The chain pump drawn in Wang Zhen's Nong shu",
        originalTitle = "Longgu che",
        author = "Wang Zhen",
        year = 1313,
        summary = "The Nong shu draws a chain pump, a loop of paddles on a chain that lifts water from a river or pond into a channel, and the drawing is known from a printed copy of 1530.",
        details = "The 1313 text itself has not survived, so the drawing is known from later printed copies. The book is dated 1313 here, and the dispute about 1303 is noted in the record for the book.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The Chetwode quadrant, an English horary quadrant of 1311",
        originalTitle = "Horary quadrant",
        author = "An unnamed English maker",
        year = 1311,
        summary = "A copper horary quadrant engraved with the date 1311 shows the hour from the sun, and a movable pointer on its reverse helps find the date of Easter.",
        details = "It is among the earliest dated English astronomical instruments known. Quadrants of this kind were used to tell the time from the altitude of the sun.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Farming",
        title = "The winnowing machine in the illustrated implements of the Nong shu",
        originalTitle = "Shan che",
        author = "Wang Zhen",
        year = 1313,
        summary = "The illustrated section of farm implements in Wang Zhen's Nong shu includes a winnowing machine, a fan in a frame that blew chaff from threshed grain.",
        details = "The section is called the Nongqi tupu, and the drawing is known from later printings, because the 1313 text has not survived. The book is dated 1313 here, and another account puts it about 1303.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Grain",
        title = "The trip-hammer husking mill of Wang Zhen's Nong shu",
        originalTitle = "Shui dui",
        author = "Wang Zhen",
        year = 1313,
        summary = "Wang Zhen's Nong shu describes a water-driven trip hammer, a wheel with cams that lifted pestles to husk grain.",
        details = "The drawing is known from later printings, because the 1313 text has not survived. The book is dated 1313 here, with the dispute about 1303 noted.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.DEVICE,
        section = "Silk",
        title = "The silk reeling wheel of the Nong shu's sericulture pages",
        originalTitle = "Sao che",
        author = "Wang Zhen",
        year = 1313,
        summary = "The sericulture pages of Wang Zhen's Nong shu include a reeling wheel that wound silk threads from cocoons.",
        details = "The drawing is known from later printings, because the 1313 text has not survived. The book is dated 1313 here, and another account puts it about 1303.",
    ),

)
