package com.myles.cijian;

import android.database.Cursor;

final class Word {
    final String id, word, reading, meaning, traditional, example, exampleMeaning, note, pitch, pos, level, frequency;
    final double rank;
    boolean favorite, familiar;
    String spokenForm(){return Pronunciation.spoken(word,reading);}
    Word(Cursor c) {
        id = c.getString(0); word = c.getString(1); reading = c.getString(2);
        meaning = c.getString(3); traditional = c.getString(4); example = c.getString(5);
        exampleMeaning = c.getString(6); note = c.getString(7); pitch = c.getString(8);
        pos = c.getString(9); level = c.getString(10);
        favorite = c.getInt(11) != 0; familiar = c.getInt(12) != 0;
        frequency=Frequency.fromTags(c.getString(13));rank=c.getDouble(14);
    }
}
