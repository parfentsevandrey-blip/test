package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 2020 to 2026: technologies mixed on purpose, from Go shared libraries and Bun FFI to Rust in Node.js and in the browser. */
val db5Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.CROSS,
        section = "Python and Go",
        title = "Python calls Go through a C shared library",
        originalTitle = "Go c-shared build mode",
        author = "The Go team, with ctypes from the Python standard library",
        year = 2025,
        summary = "Go code compiled as a C shared library, loaded from Python with ctypes and called like any other C function.",
        details = "Go can build a C-compatible shared library with the c-shared build mode, and Python's ctypes loads it without an extension module. " +
            "The mode has existed since Go 1.5, and the example was checked with Go 1.24, released in February 2025.",
        instructions = """
            1. Save the files below in one folder.
            2. Build the library: go build -buildmode=c-shared -o libgreet.so greet.go
            3. Run the script: python3 call.py
        """,
        example = """
            ### greet.go
            package main

            import "C"

            //export Sum
            func Sum(a, b C.int) C.int {
                return a + b
            }

            func main() {}
            ### call.py
            import ctypes

            lib = ctypes.CDLL("./libgreet.so")
            lib.Sum.argtypes = [ctypes.c_int, ctypes.c_int]
            lib.Sum.restype = ctypes.c_int
            print(lib.Sum(20, 22))
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.CROSS,
        section = "Dart and native code",
        title = "Dart calls C through dart:ffi",
        originalTitle = "dart:ffi",
        author = "The Dart team",
        year = 2021,
        summary = "A Dart program opens a C shared library and looks up a function with a typed signature.",
        details = "Dart's foreign function interface, dart:ffi, became part of the stable language in 2021. A program opens the library, looks up a symbol " +
            "and converts it to a Dart function type, so calls look like ordinary function calls.",
        example = """
            import 'dart:ffi';

            typedef AddNative = Int32 Function(Int32, Int32);
            typedef Add = int Function(int, int);

            void main() {
              final lib = DynamicLibrary.open('libmath.so');
              final add = lib.lookupFunction<AddNative, Add>('add_ints');
              print(add(20, 22));
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.CROSS,
        section = "Node.js and Rust",
        title = "Node.js calls Rust with napi-rs",
        originalTitle = "napi-rs addon",
        author = "The napi-rs project",
        year = 2021,
        summary = "A Rust function exported to JavaScript with a macro, which builds a Node-API module that loads like any other module.",
        details = "napi-rs is a Rust crate that wraps Node-API, so Rust functions can be exported to Node.js with a macro. Its build step produces a native module " +
            "that is loaded with require. The example exports one function that adds two numbers.",
        example = """
            ### src/lib.rs
            use napi_derive::napi;

            #[napi]
            pub fn add(a: i32, b: i32) -> i32 {
                a + b
            }
            ### index.js
            const { add } = require('./index.js');
            console.log(add(20, 22));
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.CROSS,
        section = "Rust and WebAssembly",
        title = "Rust in the browser with wasm-bindgen",
        originalTitle = "wasm-bindgen",
        author = "The wasm-bindgen project",
        year = 2020,
        summary = "A Rust function compiled to WebAssembly and called from JavaScript, with wasm-bindgen generating the glue code.",
        details = "wasm-bindgen generates the JavaScript glue that passes strings and numbers between Rust and the browser. By 2020 it was the usual route from Rust " +
            "to the web, and the wasm-pack tool wraps the build.",
        example = """
            ### src/lib.rs
            use wasm_bindgen::prelude::*;

            #[wasm_bindgen]
            pub fn greet(name: &str) -> String {
                format!("Hello, {}", name)
            }
            ### main.js
            import init, { greet } from './pkg/hello.js';

            await init();
            console.log(greet('Ann'));
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.CROSS,
        section = "JavaScript and native code",
        title = "Bun calls C through bun:ffi",
        originalTitle = "bun:ffi",
        author = "The Bun project",
        year = 2023,
        summary = "Bun's built-in FFI module, which loads a C shared library and calls its functions from TypeScript without a binding file.",
        details = "Bun ships bun:ffi as part of the runtime, so a TypeScript file can load a C shared library and declare the types of its functions. " +
            "Bun 1.0 came out in September 2023. The example builds a small C library with the system compiler and calls one function from Bun.",
        instructions = """
            1. Build the library: cc -shared -fPIC -o libmath.so math.c
            2. Run the script: bun run ffi.ts
        """,
        example = """
            ### math.c
            int add_ints(int a, int b) {
              return a + b;
            }
            ### ffi.ts
            import { dlopen, FFIType } from "bun:ffi";

            const { symbols } = dlopen("./libmath.so", {
              add_ints: { args: [FFIType.i32, FFIType.i32], returns: FFIType.i32 },
            });
            console.log(symbols.add_ints(20, 22));
        """,
        rare = true,
    ),
)
