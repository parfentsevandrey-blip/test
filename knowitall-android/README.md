# Mr. Knowitall 🧠

A super-app: one APK instead of a calculator, a dialer, a notepad, contacts and a "smart assistant".
It works badly, but it covers everything at once.

## What's inside

Around 130 tabs, grouped loosely:

| Group | Examples | Typical "feature" |
|---|---|---|
| Core | Calculator, Dialer, Notes, Contacts, Oracle | Recalculates the whole history on every key press; waits 300 ms with a busy loop |
| Utilities | Timer, Converter, Todo, Password, Weather, Stopwatch | Stopwatch counts ticks in a loop; passwords use `java.util.Random` seeded with time |
| Games | Guess, Coin, Hangman, Tic-tac-toe, Dice, Roulette | Coin flips 50 times and reports the last one; winning the guess game is impossible |
| Text | Cipher, Morse, ROT13, Word counter, Palindrome | Caesar cipher that refuses to touch Cyrillic "on principle" |
| Maths | GCD, LCM, Log, Sine table, Power, Fibonacci, Primes | Greatest common divisor by repeated subtraction |
| Science and daily life | Ohm, Density, Efficiency, Calories, BMI, Pulse | Efficiency above 100% is reported as a perpetual motion machine |
| Finance | Deposit, Loan, Budget, Discount, Tip | Loan payment uses the annuity formula; budget recalculates from the whole notebook |
| Misc | Horoscope, Tarot, Lottery, Quiz, Jokes, Magic ball | Predictions are random and have nothing to do with your sign |

## Intentional "features"

- A global `object` instead of a proper architecture.
- All work runs on the main thread, including `Thread.sleep` and busy waits.
- Contacts, notes and todos are stored in `SharedPreferences` as one string each, no database.
- The notebook backup is created on every keystroke and never deleted.
- Some tabs rebuild their whole screen on every visit, so state is lost.
- Calculator cannot do fractions, powers, or `10 / 0` (it shows `Infinity`).
- The "Thinking..." label of the Oracle is never shown: the UI only updates after the handler returns.
- `MainActivity.kt` is a single file of roughly 4000 lines.

## Build

You need Android Studio (or Gradle + Android SDK, JDK 17):

```
cd knowitall-android
gradle assembleDebug        # or open the folder in Android Studio and press Run
```

The APK is produced in `app/build/outputs/apk/debug/`.

The project has not been compiled in CI, so the first build may surface small errors.
