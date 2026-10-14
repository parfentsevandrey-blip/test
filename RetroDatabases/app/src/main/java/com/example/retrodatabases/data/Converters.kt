package com.example.retrodatabases.data

import androidx.room.TypeConverter

/** Stores the record kind as text (the enum constant name) and reads it back. */
class Converters {

    @TypeConverter
    fun kindToString(kind: Kind): String = kind.name

    @TypeConverter
    fun stringToKind(name: String): Kind = Kind.valueOf(name)
}
