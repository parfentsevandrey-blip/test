package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1450 to 1460: a vowel count on a grammar-school sample and a line of type read backwards, each in Python and C. */
val db59Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.CROSS,
        section = "Grammar",
        title = "Vowels in a grammar-school sentence in Python and C",
        originalTitle = "Ars minor",
        author = "Aelius Donatus",
        year = 1450,
        summary = "A Python program and a C program count the vowels in one short sample sentence, and the run script checks that both print the same count of seven.",
        details = "Donatus's Ars minor taught boys the letters and the parts of speech, and the sample sentence here is invented for the example rather than quoted from the grammar. The run script compares the output of the two programs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### vowels.py
            sample = "donatus taught the boys"
            count = sum(1 for ch in sample if ch in "aeiou")
            print("vowels in the sample:", count)
            ### vowels.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const char *sample = "donatus taught the boys";
                int count = 0;
                for (size_t i = 0; i < strlen(sample); i++) {
                    if (strchr("aeiou", sample[i]) != NULL) {
                        count++;
                    }
                }
                printf("vowels in the sample: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 vowels.py > python.txt
            cc -o vowels_c vowels.c
            ./vowels_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.CROSS,
        section = "Printing shop",
        title = "A line of type read backwards in Python and C",
        originalTitle = "Forme",
        author = "The compositors of Mainz",
        year = 1450,
        summary = "A line set in reverse is read back by reversing its letters, and a Python program and a C program both print the reversed name Gutenberg as grebnetug.",
        details = "Compositors set each line backwards so that the impression read true, and the reversal of the letters is the same operation in both languages. The run script checks that the two programs print the same line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### mirror.py
            line = "gutenberg"
            print(line[::-1])
            ### mirror.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                char line[] = "gutenberg";
                size_t n = strlen(line);
                char out[10];
                for (size_t i = 0; i < n; i++) {
                    out[i] = line[n - 1 - i];
                }
                out[n] = '\0';
                printf("%s\n", out);
                return 0;
            }
            ### run.sh
            python3 mirror.py > python.txt
            cc -o mirror_c mirror.c
            ./mirror_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
