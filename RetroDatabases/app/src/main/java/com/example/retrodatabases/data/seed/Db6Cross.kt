package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1950 to 1960: an assembly routine called from C, and a C loop driven from Python. */
val db6Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.CROSS,
        section = "C and assembly",
        title = "C calls a NASM routine for 36-bit addition",
        originalTitle = "System V calling convention on x86-64",
        author = "Written as a teaching example",
        year = 1954,
        summary = "An assembly routine written in NASM, called from a C program, adds two numbers and keeps only the low 36 bits, as the words of the IBM 704 did.",
        details = "The System V calling convention passes the first two arguments in registers, so the routine reads them from rdi and rsi and returns the result in rax. The mask in the routine keeps the same 36-bit width as the words of the IBM 704.",
        instructions = """
            1. Save the files below in one folder.
            2. Assemble the routine: nasm -f elf64 add36.asm -o add36.o
            3. Compile and link the program: gcc main.c add36.o -o add36
            4. Run it: ./add36
        """,
        example = """
            ### add36.asm
            section .text
            global add36
            add36:
                lea rax, [rdi + rsi]    ; the first two arguments arrive in rdi and rsi
                mov rdx, 0xFFFFFFFFF    ; 36 one bits
                and rax, rdx
                ret
            ### main.c
            #include <stdint.h>
            #include <stdio.h>

            uint64_t add36(uint64_t a, uint64_t b);

            int main(void) {
                printf("%llu\n", (unsigned long long)add36(5, 7));
                printf("%llu\n", (unsigned long long)add36(0xFFFFFFFFFULL, 1));
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.CROSS,
        section = "Python and C",
        title = "Python calls a C Monte Carlo loop through ctypes",
        originalTitle = "ctypes with a shared library built from C",
        author = "Written as a teaching example",
        year = 1953,
        summary = "A C function counts random points inside a quarter circle, and a Python script calls it through ctypes and prints an estimate of pi.",
        details = "The loop runs in C, where the random numbers come from the C library's rand function. Python passes in the number of samples and the seed, then reads the count. " +
            "It gives the same estimate as the Monte Carlo example in this database, with the inner loop moved into C.",
        instructions = """
            1. Save the files below in one folder.
            2. Build the library: cc -shared -fPIC -o libpi.so pi.c
            3. Run the script: python3 call.py
        """,
        example = """
            ### pi.c
            #include <stdlib.h>

            /* Counts how many random points fall inside the quarter circle. */
            long count_inside(long samples, unsigned int seed) {
                srand(seed);
                long inside = 0;
                for (long i = 0; i < samples; i++) {
                    double x = rand() / (double)RAND_MAX;
                    double y = rand() / (double)RAND_MAX;
                    if (x * x + y * y <= 1.0) {
                        inside++;
                    }
                }
                return inside;
            }
            ### call.py
            import ctypes

            lib = ctypes.CDLL("./libpi.so")
            lib.count_inside.argtypes = [ctypes.c_long, ctypes.c_uint]
            lib.count_inside.restype = ctypes.c_long

            samples = 200000
            inside = lib.count_inside(samples, 1953)
            print(f"pi is about {4 * inside / samples:.2f}")
        """,
    ),
)
