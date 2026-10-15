package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1770 to 1780: a symbol for the imaginary unit, grammars of Persian and Bengali, a German dictionary, a school for the deaf and an essay on the origin of language. */
val db27Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Complex numbers",
        title = "Euler's symbol i for the square root of minus one",
        originalTitle = "De formulis integralibus imaginariis",
        author = "Leonhard Euler",
        year = 1777,
        summary = "Euler wrote the square root of minus one as the single letter i, which made complex expressions short enough to calculate with.",
        details = "Euler submitted a memoir using i for the imaginary unit on 5 May 1777, but the memoir was not published until 1794. Euler himself began to use the symbol later in 1777, so the notation spread only gradually in the following decades. The date given here is the year of the submitted memoir, with the printing date noted.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Jones's grammar of the Persian language",
        originalTitle = "A Grammar of the Persian Language",
        author = "William Jones",
        year = 1771,
        summary = "A grammar that set out the forms of Persian for English-speaking students of the language.",
        details = "Jones published the grammar in London in 1771, twelve years before he sailed to India as a judge in Calcutta. It describes the nouns, verbs and particles of Persian with examples from its literature. Jones later became one of the founders of the Asiatic Society.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Halhed's grammar of the Bengal language",
        originalTitle = "A Grammar of the Bengal Language",
        author = "Nathaniel Brassey Halhed",
        year = 1778,
        summary = "A grammar of Bengali written in English and printed with Bengali type at Hooghly, one of the first books in that script.",
        details = "Halhed printed the grammar at Hooghly near Calcutta in 1778, using Bengali type cut by Charles Wilkins. It is often called the first book printed in Bengali characters, though accounts differ on which earlier items should count. The grammar was written in English so that British officials could learn the language.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Lexicography",
        title = "Adelung's dictionary of High German",
        originalTitle = "Grammatisch-kritisches Woerterbuch der hochdeutschen Mundart",
        author = "Johann Christoph Adelung",
        year = 1774,
        summary = "A German dictionary that gave each word with its grammar, pronunciation and regional use, and advised readers on the correct forms.",
        details = "Adelung published the first volume in 1774 and the last in 1786, so the work took about twelve years. Its entries record the grammar of each word and where its usage differs between regions. The dictionary became a standard reference for German spelling and usage in the following decades.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Deaf education",
        title = "Heinicke's Leipzig school for the deaf",
        originalTitle = "Leipzig school for the deaf",
        author = "Samuel Heinicke",
        year = 1778,
        summary = "A school in Leipzig that taught deaf pupils to speak and read lips rather than to use signs.",
        details = "Heinicke opened his school in Leipzig in 1778 and taught deaf pupils to speak and to read the lips of others. His method stood against the sign methods of the Paris school of the abbe de l'Epee, and the two approaches were argued over for the next century.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.LANGUAGE,
        section = "Philosophy of language",
        title = "Herder's essay on the origin of language",
        originalTitle = "Abhandlung ueber den Ursprung der Sprache",
        author = "Johann Gottfried Herder",
        year = 1772,
        summary = "An essay arguing that human reason, not a gift from God, produced language, written in answer to a prize question of the Berlin Academy.",
        details = "The Berlin Academy asked in 1769 whether men left to their own powers could invent language, and Herder's answer won the prize in 1771. The essay is dated here to its printed year of 1772. It became a founding text for the idea that language and thought develop together.",
    ),

)
