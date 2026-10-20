package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 350: a Greek sacred name cut short to its first and last letters, in Python and in C. */
val db169Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.CROSS,
        section = "Writing",
        title = "How a Greek sacred name is cut short to its first and last letters, in Python and C",
        originalTitle = "Nomina sacra",
        author = "Greek scribes of the Christian books",
        year = 350,
        summary = "Greek scribes wrote the sacred names of God and of Christ in short form, keeping the first and last letters with a stroke above them, and two programs write out two of these forms.",
        details = "The practice is older than the codices of the mid-fourth century, such as Codex Sinaiticus, which use it widely, so 350 is the year of that anchor and not the year the practice began. The record is placed for that reason, and the two programs use ASCII stand-ins for the Greek letters: T theta, E epsilon, O omicron, S sigma, K kappa, Y upsilon, R rho and I iota.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### nomina.py
            # ASCII stand-ins for the Greek letters: T theta, E epsilon, O omicron, S sigma, K kappa, Y upsilon, R rho, I iota
            WORDS = {"theos": "TEOS", "kyrios": "KYRIOS"}
            saved = 0
            for word, letters in WORDS.items():
                print(word, "is written", letters[0] + letters[-1], "from", len(letters), "letters")
                saved += len(letters) - 2
            print("letters saved:", saved)
            ### nomina.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                /* the same ASCII stand-ins as nomina.py */
                const char *words[] = {"theos", "kyrios"};
                const char *letters[] = {"TEOS", "KYRIOS"};
                int saved = 0;
                for (int i = 0; i < 2; i++) {
                    size_t n = strlen(letters[i]);
                    printf("%s is written %c%c from %d letters\n", words[i], letters[i][0], letters[i][n - 1], (int)n);
                    saved += (int)n - 2;
                }
                printf("letters saved: %d\n", saved);
                return 0;
            }
            ### run.sh
            python3 nomina.py > python.txt
            cc -o nomina_c nomina.c
            ./nomina_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
