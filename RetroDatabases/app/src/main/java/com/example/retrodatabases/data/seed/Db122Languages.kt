package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 820 to 830: the Royal Frankish Annals of 829, the Historia Brittonum, Ermold the Black's poem of 826 and the Hortulus of 827. */
val db122Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.LANGUAGE,
        section = "Frankish realm",
        title = "The Royal Frankish Annals, the official year-by-year record kept at the Frankish court from 741 to 829",
        originalTitle = "Annales regni Francorum",
        author = "Anonymous court annalists",
        year = 829,
        summary = "The Annales regni Francorum, the annals kept at the Frankish court, were completed about 829 and record the years from 741 to that date.",
        details = "The annals begin in 741, the year of Charles Martel's death, and record each year's events under the Frankish kings down to 829, in the reign of Louis the Pious. They were composed and revised in stages, so the usual date of about 829 belongs to the finished work, and no single writer is named.",
        rare = true,
    ),

    entry(
        databaseId = 122,
        kind = Kind.LANGUAGE,
        section = "Britain",
        title = "The Historia Brittonum, a Latin history of the Britons traditionally ascribed to Nennius",
        originalTitle = "Historia Brittonum",
        author = "Nennius by tradition, though the author is unknown",
        year = 829,
        summary = "The Historia Brittonum, a Latin history of the Britons, was composed about 829 to 830 and is ascribed by tradition to a writer called Nennius.",
        details = "The name Nennius rests mainly on a preface found in some copies, and many scholars treat the text as the work of an unnamed compiler. The oldest surviving copies are two to three centuries later than the date of composition, and accounts differ between about 828 and 830 for the year of writing.",
        rare = true,
    ),

    entry(
        databaseId = 122,
        kind = Kind.LANGUAGE,
        section = "Frankish realm",
        title = "Ermold the Black's Latin poem in honour of Louis the Pious, written in exile at Strasbourg",
        originalTitle = "Carmen in honorem Hludowici",
        author = "Ermold the Black",
        year = 826,
        summary = "Ermold the Black, a Latin poet, wrote a poem in praise of Louis the Pious between 826 and 828 while he was an exile at Strasbourg.",
        details = "The poem is written in elegiac verse and sets out the deeds of Louis, in the hope of winning back the emperor's favour. The dates 826 to 828 are the usual ones, and the year 826 used here is the start of that range.",
        rare = true,
    ),

    entry(
        databaseId = 122,
        kind = Kind.LANGUAGE,
        section = "Frankish realm",
        title = "Walahfrid Strabo's Hortulus, a Latin poem on the herbs of a monastery garden",
        originalTitle = "Hortulus",
        author = "Walahfrid Strabo",
        year = 827,
        summary = "Walahfrid Strabo's Hortulus, a Latin poem on the herbs of a monastery garden, was written about 827 to 829 by a young monk who later became abbot of Reichenau.",
        details = "The poem gives a short verse to each herb, saying how it grows and what it is used for in medicine or the kitchen. The dates 827 to 829 are the usual ones, and the year 827 used here is the start of that range.",
        rare = true,
    ),

)
