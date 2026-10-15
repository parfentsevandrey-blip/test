package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1820 to 1830: notations, writing systems, linguistic studies and a dictionary, each described from its first public form. */
val db22Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Analysis",
        title = "Cauchy's limit definitions in the Cours d'analyse",
        originalTitle = "Cours d'analyse de l'Ecole royale polytechnique, premiere partie (1821)",
        author = "Augustin-Louis Cauchy",
        year = 1821,
        summary = "Cauchy's course text defined limits, continuity and convergence by inequalities that say how close a value must come, in place of arguments with infinitely small quantities.",
        details = "The Cours d'analyse of 1821 was written for the Ecole polytechnique and set out the algebraic part of analysis in this form. The symbolic epsilon-delta version now used in textbooks is usually credited to Weierstrass and his lectures, so Cauchy's wording is an earlier stage of the same idea. The book also gave the convergence tests that students still learn.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Comparative linguistics",
        title = "Humboldt's study of the Basque language",
        originalTitle = "Pruefung der Untersuchungen ueber die Urbewohner Hispaniens vermittelst der vaskischen Sprache (1821)",
        author = "Wilhelm von Humboldt",
        year = 1821,
        summary = "Humboldt used the grammar and vocabulary of Basque to ask who the earliest inhabitants of Spain had been, and to test whether the language survived from them.",
        details = "The 1821 study compares Basque with other languages and treats its structure as evidence about an older population. Basque is still treated as a language isolate with no proven relatives, so the wider claims about the early peoples of Spain remain unproven. The study is an early model of careful grammatical comparison.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Writing systems",
        title = "Champollion's phonetic signs for the hieroglyphs",
        originalTitle = "Lettre a M. Dacier relative a l'alphabet des hieroglyphes phonetiques (1822)",
        author = "Jean-Francois Champollion",
        year = 1822,
        summary = "Champollion showed that hieroglyphic writing mixed signs for sounds with signs for whole words, and used the sound signs to read the names of Egyptian kings.",
        details = "The letter to Dacier, dated 27 September 1822 and printed in Paris that year, set out the phonetic values that opened the names in royal cartouches. Champollion checked the values on the Rosetta Stone and on other inscriptions, and the method then spread to whole texts.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Writing systems",
        title = "Sequoyah's Cherokee syllabary",
        originalTitle = "Cherokee syllabary (1821)",
        author = "Sequoyah",
        year = 1821,
        summary = "Sequoyah gave each spoken syllable of Cherokee its own sign, so the language could be written with about eighty-five characters.",
        details = "Sequoyah completed the syllabary around 1821, and many Cherokee speakers learned to read and write it within a few years. The Cherokee Phoenix, the first newspaper printed by a Native American nation, began in 1828 with type in Cherokee and English.",
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Linguistics",
        title = "Grimm's law of consonant shifts",
        originalTitle = "Deutsche Grammatik, volume 1, second edition (1822)",
        author = "Jacob Grimm",
        year = 1822,
        summary = "Grimm set out a regular pattern in which the consonants of the Germanic languages shifted away from those of Latin and Greek, and wrote the pattern as a table of correspondences.",
        details = "The rule had appeared in the first volume of Grimm's German grammar of 1819, and the table of correspondences is in the second edition of 1822. Accounts differ on the year to give for the law, so this record uses the edition that set out the table in full.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Morrison's Chinese dictionary, printed 1815 to 1823",
        originalTitle = "A Dictionary of the Chinese Language (1815 to 1823)",
        author = "Robert Morrison",
        year = 1823,
        summary = "Robert Morrison's dictionary arranged Chinese characters under their radicals, so an English reader could find each character and read its sound and meaning.",
        details = "Morrison printed the dictionary in parts at Macau, where he worked for the East India Company, and the parts appeared between 1815 and 1823. The record uses 1823, the year the last part was printed, because the work was complete only then.",
        rare = true,
    ),
)
