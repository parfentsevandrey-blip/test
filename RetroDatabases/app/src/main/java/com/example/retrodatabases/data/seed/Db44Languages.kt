package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1600 to 1610: English and French word books, a Quechua and a Japanese dictionary, a sign alphabet and the Chinese Euclid. */
val db44Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Cawdrey's table of hard English words",
        originalTitle = "A Table Alphabeticall of hard usuall English wordes",
        author = "Robert Cawdrey",
        year = 1604,
        summary = "Cawdrey's book of 1604 listed hard English words in alphabetical order with short explanations, and it is often called the first English dictionary.",
        details = "The book was printed in London in 1604 for readers who wanted the meanings of learned words taken from Latin and other tongues. Earlier glossaries of hard words existed, so accounts differ on whether a book of this kind deserves the name dictionary.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Nicot's Thresor of the French language",
        originalTitle = "Thresor de la langue francoyse",
        author = "Jean Nicot",
        year = 1606,
        summary = "Nicot's dictionary of 1606 gave French words with their Latin equivalents and sample phrases, and it became a model for the large French dictionaries that followed.",
        details = "The Thresor was printed in Paris in 1606, and its title calls the French language a treasure, or thresor, to be set out word by word. It is often named as an early large dictionary of French, though earlier French word books had been printed.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Andean languages",
        title = "Gonzalez Holguin's Quechua dictionary",
        originalTitle = "Vocabulario de la lengua general de todo el Peru llamada lengua Qquichua o lengua del Inca",
        author = "Diego Gonzalez Holguin",
        year = 1608,
        summary = "Holguin's dictionary of 1608 gave the words of Quechua, the language of the Inca, with Spanish glosses for the priests who worked among its speakers.",
        details = "It was printed in Lima in 1608, and the Quechua it records was spoken widely across the Andes under Spanish rule. Holguin was a Jesuit, and his dictionary was written for missionaries who needed the language in order to preach.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Jesuit Japanese-Portuguese dictionary",
        originalTitle = "Vocabulario da Lingoa de Iapam",
        author = "Jesuit missionaries, among them Joao Rodrigues",
        year = 1603,
        summary = "The Jesuit dictionary of 1603 gave Japanese words in Roman letters with Portuguese glosses, and it is the earliest printed dictionary of Japanese for a European language.",
        details = "It was printed at Nagasaki in 1603 on the Jesuit mission press, and a supplement dated 1604 followed. Several Jesuits named Rodrigues are linked to its making, and few copies are known to survive.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Willis's art of stenography",
        originalTitle = "The Art of Stenography",
        author = "John Willis",
        year = 1602,
        summary = "Willis's shorthand gave every letter of the alphabet its own sign, an alphabetic sign system that appeared without the author's name in 1602.",
        details = "The first edition did not name its author, and the book ran to fourteen editions. Timothy Bright's Characterie of 1588 came earlier, so Willis's book is best called the first shorthand alphabet of its kind, and writers label it in different ways.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.LANGUAGE,
        section = "Mathematics",
        title = "Ricci and Xu's Chinese Euclid",
        originalTitle = "Jihe yuanben",
        author = "Matteo Ricci and Xu Guangqi",
        year = 1607,
        summary = "The Chinese Euclid of 1607 translated the first six books of the Elements and introduced many Chinese terms for points, lines and figures.",
        details = "Ricci read the Latin text aloud and Xu Guangqi rendered it into Chinese, which was the usual method of the Jesuit translations. Only the first six books were translated in 1607, and the Chinese terms it introduced were kept in later mathematics in China.",
        rare = true,
    ),

)
