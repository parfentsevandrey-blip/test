package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 90 to 100: the thirty-two poems of Statius's Silvae in five books, counted in code. */
val db195Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.EXAMPLE,
        section = "Poetry",
        title = "The 32 poems of Statius's Silvae in five books, counted in code",
        originalTitle = "Silvae",
        author = "Statius",
        year = 95,
        summary = "Statius's Silvae, thirty-two occasional poems in five books written under Domitian, is counted in code by the books and the poems left over.",
        details = "The usual count gives thirty-two poems in five books, and the example shares the poems among the books, printing the poems per book and the remainder so that the figures can be checked by hand. The poems were written under Domitian and gathered in the 90s, so the year 95 is a round date within that span rather than the date of a single publication.",
        instructions = """
            1. Save the example as silvae.py.
            2. Run it: python3 silvae.py
        """,
        example = """
            POEMS = 32    # poems in the Silvae, as the usual count gives them
            BOOKS = 5     # books of the Silvae
            per_book, left_over = divmod(POEMS, BOOKS)
            print("poems in the Silvae:", POEMS)
            print("books in the Silvae:", BOOKS)
            print("poems per book, rounded down:", per_book)
            print("poems left over when each book has that many:", left_over)
        """,
    ),

)
