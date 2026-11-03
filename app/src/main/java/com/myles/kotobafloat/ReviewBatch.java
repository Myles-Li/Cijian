package com.myles.kotobafloat;
/** 每轮最多50个原始词，追加一次遗忘题后仍不超过100题。 / At most 50 original words; one retry per word keeps durable queues within 100 items. */
final class ReviewBatch {
    static final int MIN=1,MAX=50,DEFAULT=20;
    static final int[] PRESETS={5,10,20,30,50};
    static boolean valid(int n){return n>=MIN&&n<=MAX;}
    static int parse(String text){
        String value=text==null?"":text.trim();
        if(!value.matches("[0-9]+"))throw new IllegalArgumentException("请输入 1～50 的整数");
        try{int n=Integer.parseInt(value);if(valid(n))return n;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("请输入 1～50 的整数");
    }
    private ReviewBatch(){}
}
