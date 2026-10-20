package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 630 to 640: Isidore's twenty books of the Etymologies, counted in Python and in C. */
val db141Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.CROSS,
        section = "Latin",
        title = "Isidore's twenty books of the Etymologies, counted in Python and C, with the books on words and on things counted apart",
        originalTitle = "Etymologiae sive Originum libri XX",
        author = "Isidore of Seville",
        year = 630,
        summary = "A Python program and a C program count the twenty books of Isidore's Etymologies, ten on words and ten on things, and a script checks that both print the same figures.",
        details = "The work is usually described as twenty books, the first ten on words and learning and the last ten on things, so both programs print the same three figures. The check confirms the arithmetic only, while the book count and the grouping come from the usual description of the work.",
        instructions = """
            1. Save the example as etym_books.py, etym_books.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### etym_books.py
            BOOKS = 20   # the twenty books of the Etymologies
            WORDS = 10   # the books on words and learning, I to X
            THINGS = BOOKS - WORDS
            print("books in the Etymologies:", BOOKS)
            print("books on words and learning:", WORDS)
            print("books on things:", THINGS)
            ### etym_books.c
            #include <stdio.h>

            int main(void) {
                int books = 20, words = 10;
                printf("books in the Etymologies: %d\n", books);
                printf("books on words and learning: %d\n", words);
                printf("books on things: %d\n", books - words);
                return 0;
            }
            ### run.sh
            python3 etym_books.py > python.txt
            cc -o etym_books_c etym_books.c
            ./etym_books_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
