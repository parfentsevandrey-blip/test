package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 590 to 600: the book counts of four works of the decade, totalled and ranked in Python and in C. */
val db145Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.CROSS,
        section = "Mixed",
        title = "The book counts of four works of the 590s, totalled and ranked in Python and in C",
        originalTitle = "Book counts of four works",
        author = "Authors of the four works",
        year = 594,
        summary = "The book counts of four works of the 590s, ten, four, six and twelve, are totalled and ranked by a Python program and by a C program that print the same result.",
        details = "Gregory of Tours wrote ten books of Histories, Gregory the Great's Dialogues have four, Evagrius's history has six, and the Strategikon has twelve. The two programs print the same six lines, and a script checks that the outputs agree.",
        rare = true,
        instructions = """
            1. Save the example as book_counts.py, book_counts.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### book_counts.py
            BOOKS = [("Gregory of Tours, Histories", 10), ("Gregory the Great, Dialogues", 4),
                     ("Evagrius, Ecclesiastical History", 6), ("Strategikon", 12)]
            for title, count in BOOKS:
                print(title, count, "books")
            print("total:", sum(count for _, count in BOOKS), "books")
            longest = max(BOOKS, key=lambda item: item[1])
            print("longest:", longest[0], longest[1], "books")
            ### book_counts.c
            #include <stdio.h>

            int main(void) {
                const char *titles[] = {"Gregory of Tours, Histories", "Gregory the Great, Dialogues",
                                        "Evagrius, Ecclesiastical History", "Strategikon"};
                int books[] = {10, 4, 6, 12};
                int n = 4, total = 0, best = 0;
                for (int i = 0; i < n; i++) {
                    printf("%s %d books\n", titles[i], books[i]);
                    total += books[i];
                    if (books[i] > books[best]) best = i;
                }
                printf("total: %d books\n", total);
                printf("longest: %s %d books\n", titles[best], books[best]);
                return 0;
            }
            ### run.sh
            python3 book_counts.py > python.txt
            cc -o book_counts_c book_counts.c
            ./book_counts_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
