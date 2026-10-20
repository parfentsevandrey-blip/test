package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code experiment of 330: the Chi-Rho monogram drawn as text in Python and in C, placed at 330 for the city named after Constantine. */
val db171Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.CROSS,
        section = "Symbols",
        title = "The Chi-Rho monogram drawn as text in Python and in C",
        originalTitle = "Chi-Rho",
        author = "Christian makers of the Chi-Rho monogram",
        year = 330,
        summary = "The Chi-Rho monogram takes its name from the first two letters of Christos, and a script prints chi and rho side by side as text, with the same output in Python and C.",
        details = "The drawing is a stand-in: each letter is a five-row pattern of hash marks, and the two letters stand side by side rather than overlapped, so the example copies no surviving sign. The year 330 is the placed year, the year Constantinople was dedicated, because the sources do not date a text drawing of the monogram, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### chirho.py
            chi = ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]
            rho = ["####.", "#...#", "####.", "#....", "#...."]
            for c, r in zip(chi, rho):
                print(c, r)
            ### chirho.c
            #include <stdio.h>

            int main(void) {
                const char *chi[] = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
                const char *rho[] = {"####.", "#...#", "####.", "#....", "#...."};
                for (int i = 0; i < 5; i++) {
                    printf("%s %s\n", chi[i], rho[i]);
                }
                return 0;
            }
            ### run.sh
            python3 chirho.py > python.txt
            cc -o chirho_c chirho.c
            ./chirho_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
