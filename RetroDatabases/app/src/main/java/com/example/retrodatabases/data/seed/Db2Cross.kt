package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of the 1990s: technologies mixed on purpose, to see what happens. */
val db2Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 2,
        kind = Kind.CROSS,
        section = "Java and native code",
        title = "Java calls C through JNI",
        originalTitle = "Java Native Interface",
        author = "Sun Microsystems and the JNI team",
        year = 1997,
        summary = "A Java program calls a function written in C, which is how Java programs reused old libraries.",
        details = "The Java Native Interface (JNI), part of JDK 1.1 in 1997, lets a class declare a native method. " +
            "The C side implements a function with a generated name, and System.loadLibrary loads the shared library at start-up. " +
            "The Java code stays portable; only the library is built for each platform.",
        instructions = """
            1. Save the two files below in one folder. Compile the Java part and generate the C header: javac -h . Native.java
            2. Build the shared library from the C file: cc -shared -fPIC -I"<DOLLAR>JAVA_HOME/include" -I"<DOLLAR>JAVA_HOME/include/linux" native.c -o libnative.so
            3. Run the Java program and point it at the library: java -Djava.library.path=. Native
        """,
        example = """
            ### Native.java
            public class Native {
                static native int add(int a, int b);

                static {
                    System.loadLibrary("native");
                }

                public static void main(String[] args) {
                    System.out.println("2 + 3 = " + add(2, 3));
                }
            }
            ### native.c
            #include <jni.h>
            #include "Native.h"

            JNIEXPORT jint JNICALL Java_Native_add(JNIEnv *env, jclass cls, jint a, jint b)
            {
                return a + b;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.CROSS,
        section = "Web and processes",
        title = "PHP runs a C program",
        originalTitle = "exec() from a PHP page",
        author = "CGI and PHP practice",
        year = 1997,
        summary = "A PHP page calls a compiled C program through the shell and shows its output.",
        details = "Server-side scripts of the time could start other programs with exec(). " +
            "The page passes an argument to a small C program, which does the arithmetic, and PHP puts the result into the HTML. " +
            "The trade-off was speed against safety: anyone who could influence the arguments could run commands.",
        instructions = """
            1. Save the two files below in one folder and compile the C program: cc -o square square.c
            2. Run the page from the command line: php -f page.php
            3. On a web server the same code works if the server allows the script to run the program.
        """,
        example = """
            ### square.c
            #include <stdio.h>
            #include <stdlib.h>

            int main(int argc, char *argv[])
            {
                int n = argc > 1 ? atoi(argv[1]) : 10;
                printf("%d\n", n * n);
                return 0;
            }
            ### page.php
            <?php
            <DOLLAR>n = 7;
            exec("./square <DOLLAR>n", <DOLLAR>output);
            echo "The square of <DOLLAR>n is <DOLLAR>output[0]\n";
            ?>
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.CROSS,
        section = "Build-time generation",
        title = "Perl writes a C table at build time",
        originalTitle = "Generated header",
        author = "Makefile and Perl practice",
        year = 1995,
        summary = "A Perl script writes a C header with a table of squares, and the C program includes it when it is built.",
        details = "Code generators were common in the 90s: a script produced a header or a table from a specification, and the build compiled the result. " +
            "Here Perl writes the array and the C program only reads it. Changing the script changes the table without touching the C code.",
        instructions = """
            1. Save the two files below in one folder.
            2. Generate the header and build the program: perl gen.pl > table.h, then cc -o main main.c
            3. Run it: ./main prints 1, 4, 9, 16 and 25.
        """,
        example = """
            ### gen.pl
            #!/usr/bin/perl
            # Writes a C array of squares to standard output
            print "static const int squares[] = {\n";
            for my <DOLLAR>i (1 .. 5) {
                print "    <DOLLAR>i * <DOLLAR>i,\n";
            }
            print "};\n";
            print "static const int count = 5;\n";
            ### main.c
            #include <stdio.h>
            #include "table.h"

            int main(void)
            {
                int i;
                for (i = 0; i < count; i++)
                    printf("%d\n", squares[i]);
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.CROSS,
        section = "Embedding",
        title = "Lua embedded in a C program",
        originalTitle = "Embedding Lua",
        author = "PUC-Rio Lua team",
        year = 1996,
        summary = "A C host creates a Lua state and runs a script, so the game logic can change without recompiling the engine.",
        details = "Lua was designed to live inside other programs. The host creates an interpreter state, opens the standard libraries and runs script text or files. " +
            "Game engines of the late 90s used this pattern to keep level and weapon logic in scripts. " +
            "The example uses the function names of later Lua versions, but the idea is the one the 90s engines used.",
        instructions = """
            1. Install Lua with its development headers.
            2. Build the host against the Lua library: cc -o host host.c -llua -lm
            3. Run ./host: the embedded script prints one line.
        """,
        example = """
            #include <stdio.h>
            #include "lua.h"
            #include "lauxlib.h"
            #include "lualib.h"

            int main(void)
            {
                lua_State *L = luaL_newstate();
                luaL_openlibs(L);
                if (luaL_dostring(L, "print('the game logic runs in Lua')") != 0)
                    fprintf(stderr, "%s\n", lua_tostring(L, -1));
                lua_close(L);
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.CROSS,
        section = "Windows API",
        title = "Visual Basic calls a Windows DLL",
        originalTitle = "Declare statement",
        author = "Microsoft Visual Basic practice",
        year = 1995,
        summary = "A Visual Basic form calls MessageBox from user32.dll through a Declare statement.",
        details = "Visual Basic could reach any function in a Windows DLL with a Declare statement, which described the function's parameters. " +
            "The program was a form in Visual Basic, and the Windows API did the rest. " +
            "This was how VB programs of the decade used system services that the language itself did not offer.",
        instructions = """
            1. Create a form with a button named cmdHello.
            2. Paste the declaration and the handler into the form's code window.
            3. Run the program and click the button: a Windows message box appears.
        """,
        example = """
            Private Declare Function MessageBox Lib "user32" Alias "MessageBoxA" _
                (ByVal hWnd As Long, ByVal lpText As String, ByVal lpCaption As String, ByVal wType As Long) As Long

            Private Sub cmdHello_Click()
                MessageBox 0, "Hello from a Visual Basic form", "Cross-code", 0
            End Sub
        """,
    ),
)
