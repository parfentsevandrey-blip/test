package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1150 to 1160: Bhaskara's peacock riddle and the primes of the decade, each in Python and C. */
val db89Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.CROSS,
        section = "Puzzles",
        title = "The peacock flock of Bhaskara's riddle in Python and C",
        originalTitle = "Peacock riddle",
        author = "Bhaskara II",
        year = 1150,
        summary = "A riddle of Bhaskara's Lilavati asks for a flock whose parts add up to the whole, and a script checks that the Python and C programs both find the flock of 72.",
        details = "The riddle asks for the square root of half the flock, eight ninths of it, and two more birds, which together make the whole flock. The programs test every multiple of 18 up to 200, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder as peacock.py, peacock.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### peacock.py
            from math import isqrt

            for flock in range(1, 201):
                if flock % 18:
                    continue                  # half the flock and eight ninths must both be whole numbers
                root = isqrt(flock // 2)      # square root of half the flock
                if root * root == flock // 2 and root + 8 * flock // 9 + 2 == flock:
                    print("flock:", flock)
                    print("checked:", root, "+", 8 * flock // 9, "+", 2, "=", flock)
            ### peacock.c
            #include <stdio.h>

            int main(void) {
                for (int flock = 1; flock <= 200; flock++) {
                    if (flock % 18 != 0) continue;   /* half the flock and eight ninths must be whole numbers */
                    int half = flock / 2;
                    int root = 0;
                    while ((root + 1) * (root + 1) <= half) root++;   /* whole square root of half the flock */
                    int eight_ninths = 8 * flock / 9;
                    if (root * root == half && root + eight_ninths + 2 == flock) {
                        printf("flock: %d\n", flock);
                        printf("checked: %d + %d + %d = %d\n", root, eight_ninths, 2, flock);
                    }
                }
                return 0;
            }
            ### run.sh
            python3 peacock.py > python.txt
            cc -o peacock_c peacock.c
            ./peacock_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.CROSS,
        section = "Numbers",
        title = "The primes of the years 1150 to 1160 in Python and C",
        originalTitle = "Prime test by trial division",
        author = "No single author",
        year = 1155,
        summary = "The years from 1150 to 1160 include two primes, 1151 and 1153, and a script checks that the Python and C programs find the same two by trial division.",
        details = "The programs test each year by dividing it by every number up to its square root, and they print the primes and then their count. The run script compares the two outputs line by line, and the result is the same on every run.",
        instructions = """
            1. Save the files below in one folder as primes.py, primes.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### primes.py
            def is_prime(n):
                if n < 2:
                    return False
                d = 2
                while d * d <= n:
                    if n % d == 0:
                        return False
                    d += 1
                return True

            primes = [n for n in range(1150, 1161) if is_prime(n)]
            for p in primes:
                print(p)
            print("count:", len(primes))
            ### primes.c
            #include <stdio.h>

            int is_prime(int n) {
                if (n < 2) return 0;
                for (int d = 2; d * d <= n; d++) {
                    if (n % d == 0) return 0;
                }
                return 1;
            }

            int main(void) {
                int count = 0;
                for (int n = 1150; n <= 1160; n++) {
                    if (is_prime(n)) {
                        printf("%d\n", n);
                        count++;
                    }
                }
                printf("count: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 primes.py > python.txt
            cc -o primes_c primes.c
            ./primes_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
