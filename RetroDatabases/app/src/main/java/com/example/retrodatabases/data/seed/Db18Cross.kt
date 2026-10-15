package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1860 to 1870: the same result from two languages, checked by a run script. */
val db18Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.CROSS,
        section = "Facsimile",
        title = "Pantelegraph scan lines in Python and C",
        originalTitle = "Pantelegrafo, Caselli's facsimile telegraph (1865)",
        author = "Giovanni Caselli",
        year = 1865,
        summary = "A picture is sent as scan lines, each coded as runs of dark and light cells, and two programs in different languages code the same small picture and agree.",
        details = "Caselli's pantelegraph scanned a picture line by line with pendulums kept in step at both ends, and the Paris to Lyon line of 1865 is the date used here. The example codes a four by six picture as runs in Python and in C, and the run script compares the two outputs.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pantelegraph.py
            # A small picture: 1 is dark, 0 is light. Each scan line is sent as runs of equal cells.
            picture = ["000110", "001100", "011110", "110011"]

            def runs(line):
                out = []
                for ch in line:
                    if out and out[-1][0] == ch:
                        out[-1][1] += 1
                    else:
                        out.append([ch, 1])
                return out

            for number, line in enumerate(picture, start=1):
                coded = " ".join(f"{value}x{count}" for value, count in runs(line))
                print(f"line {number}: {coded}")
            print("ink cells:", sum(line.count("1") for line in picture))
            ### pantelegraph.c
            #include <stdio.h>
            #include <string.h>

            /* The same four by six picture as the Python program: 1 is dark, 0 is light. */
            static const char *picture[] = {"000110", "001100", "011110", "110011"};

            int main(void) {
                int rows = 4;
                int ink = 0;
                for (int r = 0; r < rows; r++) {
                    const char *line = picture[r];
                    size_t n = strlen(line);
                    printf("line %d:", r + 1);
                    size_t i = 0;
                    while (i < n) {
                        size_t j = i;
                        while (j < n && line[j] == line[i]) {
                            j++;
                        }
                        printf(" %cx%zu", line[i], j - i);
                        i = j;
                    }
                    printf("\n");
                    for (size_t k = 0; k < n; k++) {
                        if (line[k] == '1') {
                            ink++;
                        }
                    }
                }
                printf("ink cells: %d\n", ink);
                return 0;
            }
            ### run.sh
            python3 pantelegraph.py > python.txt
            cc -o pantelegraph_c pantelegraph.c
            ./pantelegraph_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.CROSS,
        section = "Frequency theory",
        title = "Venn's running frequency of successes, in Python and C",
        originalTitle = "The Logic of Chance: An Essay on the Foundations and Province of the Theory of Probability (1866)",
        author = "John Venn",
        year = 1866,
        summary = "Probability is treated as the limit that the share of successes settles toward in a long series of trials, and the two programs print the running share for one fixed series.",
        details = "Venn's book of 1866 argued that probability is a matter of frequencies in long series, rather than of belief or of equally likely cases. The example runs a fixed series of ten trials, prints the running frequency of successes in Python and in C, and the run script checks that the two outputs agree.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### venn.py
            # Venn's frequency view: the share of successes in a long series settles toward a limit.
            # A fixed series of ten trials: 1 is a success and 0 is a failure.
            outcomes = [1, 0, 1, 1, 0, 0, 1, 1, 1, 0]
            print("series: " + " ".join(str(o) for o in outcomes))
            successes = 0
            for n, o in enumerate(outcomes, start=1):
                successes += o
                print(f"trial {n}: successes {successes}, frequency {successes / n:.4f}")
            print(f"successes in the series: {successes}")
            ### venn.c
            #include <stdio.h>

            /* The same fixed series as the Python program: 1 is a success, 0 is a failure. */
            static const int outcomes[] = {1, 0, 1, 1, 0, 0, 1, 1, 1, 0};

            int main(void) {
                int count = (int)(sizeof outcomes / sizeof outcomes[0]);
                int successes = 0;
                printf("series:");
                for (int i = 0; i < count; i++) {
                    printf(" %d", outcomes[i]);
                }
                printf("\n");
                for (int i = 0; i < count; i++) {
                    successes += outcomes[i];
                    printf("trial %d: successes %d, frequency %.4f\n", i + 1, successes, (double)successes / (i + 1));
                }
                printf("successes in the series: %d\n", successes);
                return 0;
            }
            ### run.sh
            python3 venn.py > python.txt
            cc -o venn_c venn.c
            ./venn_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
