package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1700 to 1710: binary arithmetic, a symbol for pi, dance notation, plant genera, a technical dictionary and the civil script. */
val db34Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Number systems",
        title = "Leibniz's binary arithmetic",
        originalTitle = "Explication de l'Arithmetique Binaire",
        author = "Gottfried Wilhelm Leibniz",
        year = 1703,
        summary = "Leibniz showed that every whole number can be written with only the digits 0 and 1, and gave the rules for adding and multiplying in that base.",
        details = "Leibniz's paper was printed in the Memoires of the Paris Academy for 1703, though he had worked on the idea in earlier notes. He linked the two digits to the hexagrams of the Chinese Yijing, a link that reached him from a Jesuit correspondent, and he presented it as a curiosity beside the arithmetic.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Geometry",
        title = "Jones's symbol pi for the circle ratio",
        originalTitle = "Synopsis Palmariorum Matheseos",
        author = "William Jones",
        year = 1706,
        summary = "The Greek letter pi was used in print for the ratio of a circle's circumference to its diameter, a symbol that later writers took up.",
        details = "William Jones used the symbol in his Synopsis Palmariorum Matheseos of 1706, a survey of mathematics for beginners. The notation spread slowly over the following decades, and it became the standard symbol for the ratio.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Dance",
        title = "Feuillet's dance notation",
        originalTitle = "Choregraphie, ou l'art de decrire la danse",
        author = "Raoul-Auger Feuillet",
        year = 1700,
        summary = "Dance steps written as symbols on a grid, with each step given its count in beats, so that a dance could be read from the page.",
        details = "Feuillet published the system in Paris in 1700, and it was used for ballroom and stage dances across Europe for decades. Each symbol shows a step and its direction, and the grid keeps the counts of each bar in line, which made the notation a symbolic language for movement.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Botany",
        title = "Tournefort's genera of plants",
        originalTitle = "Institutiones rei herbariae",
        author = "Joseph Pitton de Tournefort",
        year = 1700,
        summary = "A system that grouped plants into genera, each defined mainly by the shape of the flower and the fruit that followed it.",
        details = "Tournefort published the full system in Paris in 1700, after earlier work on the same method. His genus names were short descriptive phrases, and Linnaeus later introduced the two-part names that botanists use now.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Harris's Lexicon Technicum",
        originalTitle = "Lexicon Technicum: or, an Universal English Dictionary of Arts and Sciences",
        author = "John Harris",
        year = 1704,
        summary = "An early English dictionary of technical terms, arranged alphabetically, with articles that explain the arts and sciences they name.",
        details = "The first volume appeared in London in 1704, and a second followed in 1710. Its articles explain mathematical, astronomical and mechanical terms in English, at a time when many dictionaries were bilingual.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.LANGUAGE,
        section = "Writing",
        title = "The Russian civil script of 1708",
        originalTitle = "Grazhdanskii shrift",
        author = "Peter the Great",
        year = 1708,
        summary = "A simplified letter set for secular printing that replaced the older church letters in ordinary books.",
        details = "The first book printed in the new type was a land-surveying geometry text of March 1708, and a formal decree on the alphabet is dated 9 February 1710 in New Style. The old alphabet had 43 letters, more than half of them Greek, and accounts differ on how many letters the new one kept, with 32 or 33 lowercase letters and four capitals given in some sources.",
        rare = true,
    ),

)
