package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1470 to 1480: the look-and-say sequence and the least common multiple of two cycles, each in Python and C. */
val db57Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.CROSS,
        section = "Counting codes",
        title = "The look-and-say sequence, in Python and C",
        originalTitle = "Expositio Sancti Hieronymi in Symbolum Apostolorum",
        author = "Theodoric Rood",
        year = 1478,
        summary = "The look-and-say sequence writes each run of equal digits as its length and digit, and a script checks that the Python and C programs print the same five terms.",
        details = "The year is only the date of the Oxford press's first book, and the sequence has no link to that press. The example starts from the digit 1, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### look_and_say.py
            def look_and_say(digits):
                out = []
                i = 0
                while i < len(digits):
                    j = i
                    while j < len(digits) and digits[j] == digits[i]:
                        j += 1
                    out.append(str(j - i) + digits[i])
                    i = j
                return "".join(out)

            term = "1"
            for step in range(1, 6):
                term = look_and_say(term)
                print(f"term {step}: {term}")
            ### look_and_say.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                char term[64] = "1";
                char next[64];
                for (int step = 1; step <= 5; step++) {
                    size_t n = strlen(term);
                    size_t out = 0;
                    for (size_t i = 0; i < n; ) {
                        size_t j = i;
                        while (j < n && term[j] == term[i]) j++;
                        out += (size_t)snprintf(next + out, sizeof next - out, "%d%c", (int)(j - i), term[i]);
                        i = j;
                    }
                    next[out] = '\0';
                    strcpy(term, next);
                    printf("term %d: %s\n", step, term);
                }
                return 0;
            }
            ### run.sh
            python3 look_and_say.py > python.txt
            cc -o look_and_say_c look_and_say.c
            ./look_and_say_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.CROSS,
        section = "Cycles",
        title = "Least common multiple of two cycles, in Python and C",
        originalTitle = "Ineffabilis summi providentia",
        author = "Pope Paul II",
        year = 1470,
        summary = "The least common multiple gives the first year on which two repeating cycles coincide, and a script checks that the Python and C programs print the same answer.",
        details = "The example pairs a twenty-five-year cycle, the Holy Year rule of Paul II's bull, with cycles of four and six years. Both programs compute the answer with the greatest common divisor, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lcm_cycles.py
            from math import gcd

            def lcm(a, b):
                return a * b // gcd(a, b)

            for a, b in [(25, 4), (25, 6), (12, 18)]:
                print(f"cycles {a} and {b}: coincide every {lcm(a, b)} years")
            ### lcm_cycles.c
            #include <stdio.h>

            static int gcd(int a, int b) {
                while (b != 0) {
                    int t = a % b;
                    a = b;
                    b = t;
                }
                return a;
            }

            static int lcm(int a, int b) {
                return a / gcd(a, b) * b;
            }

            int main(void) {
                int pairs[3][2] = {{25, 4}, {25, 6}, {12, 18}};
                for (int i = 0; i < 3; i++) {
                    printf("cycles %d and %d: coincide every %d years\n", pairs[i][0], pairs[i][1], lcm(pairs[i][0], pairs[i][1]));
                }
                return 0;
            }
            ### run.sh
            python3 lcm_cycles.py > python.txt
            cc -o lcm_cycles_c lcm_cycles.c
            ./lcm_cycles_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
