package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1250 to 1260: the layout of a Tripitaka block, a council's majority, a law citation, the pipes of a silver tree and a rhyme test. */
val db79Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.GUIDE,
        section = "Woodblocks",
        title = "How a Tripitaka woodblock side is laid out in lines and characters",
        originalTitle = "Woodblock layout",
        author = "The carvers of the Tripitaka Koreana",
        year = 1251,
        summary = "Each side of a Tripitaka Koreana block holds 23 lines of 14 characters, and a short program works out the characters per side and per block.",
        details = "The layout is the one given in accounts of the blocks, and the example counts one side and then both sides of a block. The figures follow from the layout alone, so the output can be checked by hand.",
        instructions = """
            1. Save the example as tripitaka_layout.py.
            2. Run it: python3 tripitaka_layout.py
        """,
        example = """
            LINES = 23
            CHARS_PER_LINE = 14
            SIDES = 2
            per_side = LINES * CHARS_PER_LINE
            per_block = per_side * SIDES
            print("characters per side:", per_side)
            print("characters per block:", per_block)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.GUIDE,
        section = "Councils",
        title = "How a council of fifteen reaches a majority decision",
        originalTitle = "Majority of a council",
        author = "The councillors named in the Provisions of Oxford",
        year = 1258,
        summary = "A council of fifteen needs eight votes for a majority, and a short program counts the votes and reports whether a motion is carried.",
        details = "The Provisions of Oxford set up a council of fifteen, and the example counts an illustrative vote of nine in favour. The majority of fifteen is eight, so the motion is carried, and the program also prints the votes against.",
        instructions = """
            1. Save the example as council_majority.py.
            2. Run it: python3 council_majority.py
        """,
        example = """
            COUNCIL = 15
            needed = COUNCIL // 2 + 1
            in_favour = 9   # illustrative vote, not a record of the council
            against = COUNCIL - in_favour
            print("councillors:", COUNCIL)
            print("votes needed for a majority:", needed)
            print("in favour:", in_favour, "against:", against)
            print("carried:", "yes" if in_favour >= needed else "no")
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.GUIDE,
        section = "Law codes",
        title = "How a law in a four-book code is cited by its book, title and law",
        originalTitle = "Citation of laws",
        author = "The chancery of Alfonso X of Castile",
        year = 1255,
        summary = "A law in the Fuero Real is cited by its book, title and law number, and a short program writes an illustrative citation in that form.",
        details = "The Fuero Real is divided into books, titles and laws, so a citation gives the three numbers in order. The example uses an illustrative position in the code, not a particular law, and it shows only the form of the reference.",
        instructions = """
            1. Save the example as law_citation.py.
            2. Run it: python3 law_citation.py
        """,
        example = """
            BOOK = 2    # illustrative book number
            TITLE = 5   # illustrative title number
            LAW = 3     # illustrative law number
            print("cite as: Fuero Real", f"{BOOK}.{TITLE}.{LAW}")
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.GUIDE,
        section = "Mongol court",
        title = "How the four liquors of the silver tree are assigned to its four pipes",
        originalTitle = "Pipes of the silver tree",
        author = "Guillaume Boucher, a goldsmith of Paris",
        year = 1254,
        summary = "The silver tree at Karakorum is described as pouring four liquors, and a short program pairs four pipes with four labelled liquors in order.",
        details = "The common account gives four liquors, and the example labels them A to D because their names are not needed for the pairing. The pairing is written out in the order of the pipes, so the output can be read at a glance.",
        instructions = """
            1. Save the example as tree_pipes.py.
            2. Run it: python3 tree_pipes.py
        """,
        example = """
            pipes = ["pipe 1", "pipe 2", "pipe 3", "pipe 4"]
            liquors = ["liquor A", "liquor B", "liquor C", "liquor D"]   # illustrative labels
            for pipe, liquor in zip(pipes, liquors):
                print(pipe, "pours", liquor)
            print("pipes:", len(pipes))
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How a rhyme pair is tested by its final vowel and consonants",
        originalTitle = "Rhyme test",
        author = "Flemish poets of the thirteenth century",
        year = 1250,
        summary = "Two words rhyme when their last vowel and the consonants after it match, and a short program tests three pairs of Middle Dutch words in this way.",
        details = "The test follows the spelling of the words, so it stands for the rhyme of the verse rather than for exact sounds. The pairs show a true rhyme, a second true rhyme and a pair that does not rhyme.",
        rare = true,
        instructions = """
            1. Save the example as rhyme_test.py.
            2. Run it: python3 rhyme_test.py
        """,
        example = """
            def rhyme_tail(word):
                # the rhyme is the last vowel and everything after it
                for i in range(len(word) - 1, -1, -1):
                    if word[i] in "aeiou":
                        return word[i:]
                return word

            pairs = [("vos", "los"), ("hant", "lant"), ("vos", "hant")]
            for a, b in pairs:
                print(a, b, "rhyme" if rhyme_tail(a) == rhyme_tail(b) else "no rhyme")
        """,
    ),

)
