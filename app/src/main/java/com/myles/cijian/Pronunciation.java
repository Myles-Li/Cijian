package com.myles.cijian;

/** 保留原始标记，朗读假名而非词源或词缀占位符。 / Preserve source labels; pronounce kana rather than etymology or affix placeholders. */
final class Pronunciation {
    private static final java.util.regex.Pattern LATIN=java.util.regex.Pattern.compile("[A-Za-z]");
    static boolean origin(String reading){return reading!=null&&LATIN.matcher(TextSearch.normalize(reading)).find();}
    static boolean placeholder(String reading){return reading!=null&&(reading.indexOf('〜')>=0||reading.indexOf('～')>=0);}
    static String spoken(String word,String reading){if(origin(reading))return PitchPattern.morae(word).isEmpty()?"":word;return reading==null?"":reading.replace("〜","").replace("～","");}
}
