package com.myles.cijian;

final class Frequency {
    static final String[] VALUES={"ALL","HIGH","HIGH_MID","MID","MID_LOW","LOW","NONE"};
    static final String[] LABELS={"全部词频","高频","高频＋中频","中频","中频＋低频","低频","未分档（N4/N5）"};
    static String fromTags(String tags) {
        if(tags==null)return "NONE";
        if(tags.contains("高频"))return "HIGH";
        if(tags.contains("中频"))return "MID";
        if(tags.contains("低频"))return "LOW";
        return "NONE";
    }
    static boolean supported(String level) {return !"N5".equals(level)&&!"N4".equals(level);}
    static String[] values(String level){
        if(!supported(level))return new String[]{"ALL"};
        return "ALL".equals(level)?VALUES:java.util.Arrays.copyOf(VALUES,VALUES.length-1);
    }
    static String effective(String level,String value) {
        for(String allowed:values(level))if(allowed.equals(value))return value;
        return "ALL";
    }
    static String label(String value) {
        for(int i=0;i<VALUES.length;i++)if(VALUES[i].equals(value))return LABELS[i];
        return LABELS[0];
    }
    static String badge(Word word) {return word.level+("NONE".equals(word.frequency)?"":" · "+label(word.frequency));}
    private Frequency(){}
}
