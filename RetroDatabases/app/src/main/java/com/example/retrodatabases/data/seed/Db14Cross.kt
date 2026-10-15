package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1900 to 1910: the same result from two languages, checked by a run script. */
val db14Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.CROSS,
        section = "Definability",
        title = "Richard's diagonal in Python and C",
        originalTitle = "Les principes des mathematiques et le probleme des ensembles (1905)",
        author = "Jules Richard",
        year = 1905,
        summary = "A diagonal rule builds a digit string that differs from every number in a list of definitions, and Python and C each compute it.",
        details = "Richard's 1905 construction uses a list of definitions, each giving the digits of a number, and changes one digit of each. Here five definitions give the digits 45127. The two programs print the same lines, and the run script compares them.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### richard.py
            # Five definitions, each a fraction p/q whose decimal digits define a number.
            definitions = [(1, 3), (1, 7), (1, 4), (1, 9), (2, 3)]

            def digit(p, q, k):
                # The k-th digit after the decimal point of p/q.
                return (p * 10 ** k // q) % 10

            # Diagonal rule: the k-th digit of the new number differs from the k-th digit of the k-th definition.
            diagonal = [(digit(p, q, k) + 1) % 10 for k, (p, q) in enumerate(definitions, start=1)]
            print("diagonal digits: " + "".join(str(d) for d in diagonal))
            differs = all(diagonal[k - 1] != digit(p, q, k) for k, (p, q) in enumerate(definitions, start=1))
            print("differs from every definition:", differs)
            ### richard.c
            #include <stdio.h>

            /* The k-th digit after the decimal point of p/q, for k >= 1. */
            static int digit(long p, long q, int k) {
                long power = 1;
                for (int i = 0; i < k; i++) {
                    power *= 10;
                }
                return (int)((p * power / q) % 10);
            }

            int main(void) {
                /* The same five definitions as the Python program. */
                long p[] = {1, 1, 1, 1, 2};
                long q[] = {3, 7, 4, 9, 3};
                int n = 5;
                int diagonal[5];
                int differs = 1;
                for (int k = 1; k <= n; k++) {
                    diagonal[k - 1] = (digit(p[k - 1], q[k - 1], k) + 1) % 10;
                    if (diagonal[k - 1] == digit(p[k - 1], q[k - 1], k)) {
                        differs = 0;
                    }
                }
                printf("diagonal digits: ");
                for (int k = 0; k < n; k++) {
                    printf("%d", diagonal[k]);
                }
                printf("\n");
                printf("differs from every definition: %s\n", differs ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 richard.py > python.txt
            cc -o richard_c richard.c
            ./richard_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.CROSS,
        section = "Radio signals",
        title = "Morse SOS in Python and C",
        originalTitle = "International Radiotelegraph Convention, Berlin (1906)",
        author = "Berlin radiotelegraph conference of 1906",
        year = 1906,
        summary = "The distress signal SOS, three dots, three dashes, three dots, is spelled in Morse by two programs in different languages, and a script checks that they agree.",
        details = "The 1906 Berlin convention adopted this sequence as the ship distress signal, and it took effect in 1908. The programs encode SOS and MARCONI, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sos.py
            # Morse code for the letters used here; dots and dashes are written as . and -
            MORSE = {"S": "...", "O": "---", "M": "--", "A": ".-", "R": ".-.", "C": "-.-.", "N": "-.", "I": ".."}

            def encode(word):
                return " ".join(MORSE[ch] for ch in word)

            for word in ["SOS", "MARCONI"]:
                print(f"{word}: {encode(word)}")
            ### sos.c
            #include <stdio.h>
            #include <string.h>

            /* Morse code for the letters used here. */
            static const char *morse(char ch) {
                switch (ch) {
                    case 'S': return "...";
                    case 'O': return "---";
                    case 'M': return "--";
                    case 'A': return ".-";
                    case 'R': return ".-.";
                    case 'C': return "-.-.";
                    case 'N': return "-.";
                    case 'I': return "..";
                    default: return "?";
                }
            }

            static void encode(const char *word) {
                for (size_t i = 0; i < strlen(word); i++) {
                    if (i > 0) {
                        printf(" ");
                    }
                    printf("%s", morse(word[i]));
                }
                printf("\n");
            }

            int main(void) {
                printf("SOS: ");
                encode("SOS");
                printf("MARCONI: ");
                encode("MARCONI");
                return 0;
            }
            ### run.sh
            python3 sos.py > python.txt
            cc -o sos_c sos.c
            ./sos_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
