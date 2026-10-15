package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1590 to 1600: cipher symbol counts, vowels and consonants in a Latin phrase, and the letters of a title. */
val db45Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.FLEX,
        section = "Ciphers",
        title = "Symbol counts in a cipher message",
        originalTitle = "The Spanish cipher",
        author = "Francois Vieta",
        year = 1590,
        summary = "Counting how often each symbol occurs is the first step of reading a cipher, and a short program counts the symbols of a made-up message.",
        details = "The message is an illustrative string of four symbols, not one of Vieta's dispatches, and its counts are worked out by hand. The program lists symbols from the most frequent to the least, keeping the order of first appearance for ties.",
        instructions = """
            1. Save the example as symbol_counts.py.
            2. Run it: python3 symbol_counts.py
        """,
        example = """
            from collections import Counter

            message = "@#%@&#@%@"
            counts = Counter(message)
            for symbol, count in counts.most_common():
                print(repr(symbol), count)
            print("distinct symbols:", len(counts))
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.FLEX,
        section = "Letters",
        title = "Vowels and consonants in a Latin phrase",
        originalTitle = "In artem analyticem isagoge",
        author = "Francois Vieta",
        year = 1591,
        summary = "Vieta's two classes of letters count the vowels, which stood for unknowns, and the consonants, which stood for knowns, in a phrase from his title.",
        details = "The phrase is the title of the Isagoge, and the program counts only letters, treating y as a vowel as Vieta's list of vowels does. The counts are 12 vowels and 12 consonants among 24 letters.",
        instructions = """
            1. Save the example as vowel_count.py.
            2. Run it: python3 vowel_count.py
        """,
        example = """
            PHRASE = "In artem analyticem isagoge"
            VOWELS = set("aeiouy")

            letters = [ch for ch in PHRASE.lower() if ch.isalpha()]
            vowels = sum(1 for ch in letters if ch in VOWELS)
            print("letters:", len(letters))
            print("vowels:", vowels)
            print("consonants:", len(letters) - vowels)
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.FLEX,
        section = "Titles",
        title = "Letters in the Clementine title",
        originalTitle = "Biblia sacra vulgatae editionis",
        author = "Pope Clement VIII",
        year = 1592,
        summary = "Counting the words and letters of a title is a small text task, and a short program counts four words and 28 letters in the Clementine title.",
        details = "The title is the one this record gives for the Clementine Vulgate, and the counts are worked out by hand. The program also finds the longest word, which is editionis.",
        instructions = """
            1. Save the example as title_letters.py.
            2. Run it: python3 title_letters.py
        """,
        example = """
            TITLE = "Biblia sacra vulgatae editionis"
            words = TITLE.split()
            print("words:", len(words))
            print("letters:", sum(1 for ch in TITLE if ch.isalpha()))
            print("longest word:", max(words, key=len))
        """,
    ),

)
