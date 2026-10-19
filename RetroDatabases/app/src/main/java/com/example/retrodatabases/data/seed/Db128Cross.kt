package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 760 to 770: the four gates of the Round City placed on a circle, computed in Python and in C. */
val db128Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.CROSS,
        section = "Abbasid",
        title = "The four gates of the Round City placed on a circle, computed in Python and in C",
        originalTitle = "Gates on a circle",
        author = "Planners of the Round City, unnamed",
        year = 762,
        summary = "A Python program and a C program place the four gates of the Round City on a circle, and a script checks that both print the same coordinates.",
        details = "The gates are set at the four diagonal points of a circle of radius 1000 units: northeast for Khorasan, northwest for Sham, southwest for Kufa and southeast for Basra. The radius is an illustration and not the city's measured size, since the gate directions are known but not an exact layout.",
        rare = true,
        instructions = """
            1. Save the example as gates.py, gates.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### gates.py
            import math

            R = 1000  # an illustrative radius in units, not the measured size of the city
            GATES = [
                ("Bab al-Khorasan, northeast", 1),
                ("Bab al-Sham, northwest", 3),
                ("Bab al-Kufa, southwest", 5),
                ("Bab al-Basra, southeast", 7),
            ]
            for name, step in GATES:
                angle = step * math.pi / 4
                print(name, round(R * math.cos(angle)), round(R * math.sin(angle)))
            print("chord between Bab al-Khorasan and Bab al-Sham:", round(2 * R * math.sin(math.pi / 4)))
            ### gates.c
            #include <math.h>
            #include <stdio.h>

            int main(void) {
                const double pi = 3.141592653589793;
                const double radius = 1000.0; /* an illustrative radius in units */
                const char *names[] = {"Bab al-Khorasan, northeast", "Bab al-Sham, northwest",
                                       "Bab al-Kufa, southwest", "Bab al-Basra, southeast"};
                const int steps[] = {1, 3, 5, 7};
                for (int i = 0; i < 4; i++) {
                    double angle = steps[i] * pi / 4;
                    printf("%s %ld %ld\n", names[i], lround(radius * cos(angle)), lround(radius * sin(angle)));
                }
                printf("chord between Bab al-Khorasan and Bab al-Sham: %ld\n", lround(2 * radius * sin(pi / 4)));
                return 0;
            }
            ### run.sh
            python3 gates.py > python.txt
            cc -o gates_c gates.c -lm
            ./gates_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
