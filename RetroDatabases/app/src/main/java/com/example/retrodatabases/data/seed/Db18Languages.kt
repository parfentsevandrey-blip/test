package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1860 to 1870: chemical, telegraph, phonetic, military and logical notations of the decade. */
val db18Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Chemical notation",
        title = "Kekule's structural formulas and the benzene ring",
        originalTitle = "Sur la constitution des substances aromatiques (1865)",
        author = "August Kekule",
        year = 1865,
        summary = "Atoms are drawn as joined symbols, so a formula shows which atoms are bonded, and benzene is written as a ring of six carbon atoms with alternating bonds.",
        details = "Kekule's 1865 paper in the Bulletin of the Paris Chemical Society proposed the closed ring for benzene, and a longer German account followed in 1866. Josef Loschmidt had drawn ring diagrams in 1861, so the priority of the ring idea is disputed. Kekule's later story of a dream about a snake biting its tail, told in 1890, is not part of the 1865 record.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Telegraph codes",
        title = "International Morse code, the Paris form of 1865",
        originalTitle = "Telegraph alphabet adopted at the International Telegraph Conference, Paris (1865)",
        author = "International Telegraph Conference, Paris",
        year = 1865,
        summary = "A revised Morse alphabet, built on the letter table of the German telegraph administration, became the standard code for wire telegraphy in Europe.",
        details = "The Paris conference of 1865 chose a code that differed from the American Morse alphabet, and its letter table follows the German revision that Friedrich Gerke made in 1848. Accounts differ on how far the conference changed Gerke's table, so the record gives only the conference date. The same conference set up the telegraph union, which is a separate record in this database.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Phonetic notation",
        title = "Visible Speech, symbols that show how each sound is made",
        originalTitle = "Visible Speech: The Science of Universal Alphabetics (1867)",
        author = "Alexander Melville Bell",
        year = 1867,
        summary = "Each speech sound is written as a symbol built from parts that show the position of the lips, tongue and throat, so a reader can make the sound from its written form.",
        details = "Bell published the system in 1867 after years of teaching speech, including work with deaf pupils. The symbols were meant to serve any language, and Ellis's palaeotype, a separate record in this database, borrowed its theory from them.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Military signals",
        title = "Myer's wigwag code for flag signalling",
        originalTitle = "A Manual of Signals for the Use of Signal Officers in the Field (1864)",
        author = "Albert James Myer",
        year = 1864,
        summary = "A signaller waves one flag left and right to make short and long movements, which stand for the dots and dashes of a code read by a distant station.",
        details = "Myer, an army surgeon, built the wigwag method for the United States Army, and the signal corps he led was formed in 1860. The 1864 manual sets out the field procedures and the code. Accounts give different dates for the first trials of the method, so this record uses the year of the manual.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Peirce's notation for relatives",
        originalTitle = "Description of a Notation for the Logic of Relatives, Resulting from an Amplification of the Conceptions of Boole's Calculus of Logic (1870)",
        author = "Charles Sanders Peirce",
        year = 1870,
        summary = "Peirce extended Boole's letters so that they stand for relations between objects, and wrote a relation and its composition in one symbolic form.",
        details = "The paper appeared in the Memoirs of the American Academy of Arts and Sciences in 1870. It is an earlier and separate paper from Peirce's later algebra of logic, and the two should not be confused. Its notation treats relations as things that can be added, multiplied and converted.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.LANGUAGE,
        section = "Phonetic notation",
        title = "Ellis's palaeotype, a phonetic alphabet in roman type",
        originalTitle = "On Early English Pronunciation, Part I, with the palaeotype chart (1869)",
        author = "Alexander John Ellis",
        year = 1869,
        summary = "A phonetic alphabet built from roman letters with added marks, so that English dialects and older pronunciations could be written and compared in print.",
        details = "Ellis printed the palaeotype chart in 1869 in the first part of On Early English Pronunciation, with a design that adapted Bell's Visible Speech to roman type and took some letters from Lepsius's Standard Alphabet. The invention is dated between 1866 and 1869 in different accounts, so this record uses the year of the printed chart. Some letters taken from Lepsius were adopted without their original values being fully understood.",
        rare = true,
    ),

)
