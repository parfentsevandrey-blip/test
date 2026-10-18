package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code record of 919: a lot drawn from a pot of five tickets, in Python and in C, with the same output. */
val db113Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.CROSS,
        section = "Lots",
        title = "A lot drawn from a pot of five tickets, in Python and C",
        originalTitle = "A lot drawn from a pot of five tickets, in Python and C",
        author = "Uttiramerur assembly",
        year = 919,
        summary = "A Python program and a C program each draw one ticket from a pot of five by a fixed index and print the rest, and a script compares their output.",
        details = "The letters stand for tickets and are placeholders, not names from the inscriptions. The fixed index makes every run print the same result, so the two programs can be compared line by line.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### ballot.py
            tickets = ["A", "B", "C", "D", "E"]
            drawn = tickets.pop(3)
            print("drawn:", drawn)
            print("left in the pot:", " ".join(tickets))
            ### ballot.c
            #include <stdio.h>

            int main(void) {
                char pot[] = {'A', 'B', 'C', 'D', 'E'};
                int n = 5;
                int index = 3;
                printf("drawn: %c\n", pot[index]);
                printf("left in the pot:");
                for (int i = 0; i < n; i++) {
                    if (i != index) printf(" %c", pot[i]);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 ballot.py > python.txt
            cc -o ballot_c ballot.c
            ./ballot_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
