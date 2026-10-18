package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1145 and 1150: the Japanese era years of the 1140s and a broken bamboo of Bhaskara's kind, each in Python and C. */
val db90Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.CROSS,
        section = "Measure",
        title = "A broken bamboo of Bhaskara's kind in Python and C",
        originalTitle = "Bamboo problem",
        author = "Bhaskara II",
        year = 1150,
        summary = "A broken bamboo problem of the kind found in Bhaskara's arithmetic is solved in Python and in C, and a script checks that both programs print the same stub and top.",
        details = "The example uses the same thirty-two cubit bamboo with a distance of sixteen cubits in both languages, and the run script compares the two outputs line by line. The numbers are the ones of the example, not a quotation from the Lilavati.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### bamboo.py
            height = 32
            root_to_tip = 16
            stub = (height * height - root_to_tip * root_to_tip) // (2 * height)
            top = height - stub
            print("upright stub:", stub, "cubits")
            print("fallen top:", top, "cubits")
            print("check:", stub * stub + root_to_tip * root_to_tip == top * top)
            ### bamboo.c
            #include <stdio.h>

            int main(void) {
                int height = 32, root_to_tip = 16;
                int stub = (height * height - root_to_tip * root_to_tip) / (2 * height);
                int top = height - stub;
                int ok = stub * stub + root_to_tip * root_to_tip == top * top;
                printf("upright stub: %d cubits\n", stub);
                printf("fallen top: %d cubits\n", top);
                printf("check: %s\n", ok ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 bamboo.py > python.txt
            cc -o bamboo_c bamboo.c
            ./bamboo_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "Japanese era years of the 1140s in Python and C",
        originalTitle = "Nengo",
        author = "The Japanese court",
        year = 1145,
        summary = "The first years of four Japanese eras of the 1140s are printed in Python and in C, and a script checks that both programs print the same Common Era years.",
        details = "Each era began a new count of years, and the example lists the Common Era year of the first year of four eras, then the third year of Kyuan. The run script compares the two outputs line by line, so the two languages must agree on every year.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### nengo.py
            ERAS = [("Eiji", 1141), ("Koji", 1142), ("Ten'yo", 1144), ("Kyuan", 1145)]
            for name, first_year in ERAS:
                print(name, "1 =", first_year)
            print("Kyuan 3 =", 1145 + 3 - 1)
            ### nengo.c
            #include <stdio.h>

            int main(void) {
                const char *names[] = {"Eiji", "Koji", "Ten'yo", "Kyuan"};
                int first_years[] = {1141, 1142, 1144, 1145};
                for (int i = 0; i < 4; i++) {
                    printf("%s 1 = %d\n", names[i], first_years[i]);
                }
                printf("Kyuan 3 = %d\n", 1145 + 3 - 1);
                return 0;
            }
            ### run.sh
            python3 nengo.py > python.txt
            cc -o nengo_c nengo.c
            ./nengo_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
