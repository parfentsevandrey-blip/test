package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1215 and 1220: the clauses of Magna Carta in 1215 and 1225, and a kenning builder, each in Python and C. */
val db83Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.CROSS,
        section = "Law",
        title = "Magna Carta clause counts in Python and C",
        originalTitle = "Clauses of Magna Carta",
        author = "King John and his barons",
        year = 1215,
        summary = "The first charter of 1215 had sixty-three clauses and the 1225 version had thirty-seven, and a script checks that the Python and C programs print the same count of clauses dropped.",
        details = "The example works out the clauses dropped and the share dropped, in Python and in C, and the run script compares the two outputs line by line. The share is printed to one decimal place in both programs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### clauses.py
            clauses_1215 = 63
            clauses_1225 = 37
            dropped = clauses_1215 - clauses_1225
            print("clauses in 1215:", clauses_1215)
            print("clauses in 1225:", clauses_1225)
            print("clauses dropped:", dropped)
            print("share dropped:", f"{100 * dropped / clauses_1215:.1f} per cent")
            ### clauses.c
            #include <stdio.h>

            int main(void) {
                const int clauses_1215 = 63;
                const int clauses_1225 = 37;
                int dropped = clauses_1215 - clauses_1225;
                printf("clauses in 1215: %d\n", clauses_1215);
                printf("clauses in 1225: %d\n", clauses_1225);
                printf("clauses dropped: %d\n", dropped);
                printf("share dropped: %.1f per cent\n", 100.0 * dropped / clauses_1215);
                return 0;
            }
            ### run.sh
            python3 clauses.py > python.txt
            cc -o clauses_c clauses.c
            ./clauses_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.CROSS,
        section = "Poetry",
        title = "A kenning builder in Python and C",
        originalTitle = "Kenning",
        author = "Snorri Sturluson",
        year = 1220,
        summary = "A kenning names a thing by two words, such as whale-road for the sea, and a script checks that the Python and C programs print the same kennings.",
        details = "The two example kennings are the common examples whale-road for the sea and sea-steed for a ship, and the run script compares the outputs line by line. The pattern belongs to the Prose Edda of about 1220.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### kenning.py
            kennings = [("whale", "road", "sea"), ("sea", "steed", "ship")]
            for first, second, meaning in kennings:
                print(first + "-" + second + " names the " + meaning)
            print("kennings listed: " + str(len(kennings)))
            ### kenning.c
            #include <stdio.h>

            int main(void) {
                const char *first[] = {"whale", "sea"};
                const char *second[] = {"road", "steed"};
                const char *meaning[] = {"sea", "ship"};
                int count = 2;
                for (int i = 0; i < count; i++) {
                    printf("%s-%s names the %s\n", first[i], second[i], meaning[i]);
                }
                printf("kennings listed: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 kenning.py > python.txt
            cc -o kenning_c kenning.c
            ./kenning_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
