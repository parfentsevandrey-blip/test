package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1180: the pulverizer for a linear congruence, written in Python and in C. */
val db86Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.CROSS,
        section = "Congruences",
        title = "Solving 13x = 3 modulo 25 by the pulverizer in Python and C",
        originalTitle = "Kuttaka",
        author = "Brahmagupta and Bhaskara II",
        year = 1180,
        summary = "The pulverizer, an Indian method for solving congruences, finds x in 13x = 3 modulo 25, and a script checks that the Python and C programs print the same answer.",
        details = "Brahmagupta described the method in 628, and Bhaskara II taught it again in his treatise of 1150, so the year 1180 stands for its use in the twelfth century. The run script compares the two outputs line by line, and the answer x = 6 can be checked by hand, since 13 times 6 is 78 and 78 leaves 3 when divided by 25.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pulverize.py
            a, c, m = 13, 3, 25
            old_r, r = a, m
            old_s, s = 1, 0
            while r != 0:
                q = old_r // r
                old_r, r = r, old_r - q * r
                old_s, s = s, old_s - q * s
            g, p = old_r, old_s
            x = (c // g * p) % m
            print("gcd", g)
            print("x", x)
            print("check", a * x % m)
            ### pulverize.c
            #include <stdio.h>

            int main(void) {
                int a = 13, c = 3, m = 25;
                int old_r = a, r = m;
                int old_s = 1, s = 0;
                while (r != 0) {
                    int q = old_r / r;
                    int t = old_r - q * r;
                    old_r = r;
                    r = t;
                    t = old_s - q * s;
                    old_s = s;
                    s = t;
                }
                int g = old_r;
                int p = old_s;
                int x = ((c / g) * p) % m;
                if (x < 0) x += m;
                printf("gcd %d\n", g);
                printf("x %d\n", x);
                printf("check %d\n", (a * x) % m);
                return 0;
            }
            ### run.sh
            python3 pulverize.py > python.txt
            cc -o pulverize_c pulverize.c
            ./pulverize_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
