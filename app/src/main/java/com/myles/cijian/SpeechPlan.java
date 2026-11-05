package com.myles.cijian;

import java.util.ArrayList;
import java.util.List;

final class SpeechPlan {
    static final class Segment {
        final String text,language;
        Segment(String text,String language){this.text=text;this.language=language;}
    }
    static List<String> wordThenExample(String word,String example,int wordRepeats,int exampleRepeats,boolean includeExample){
        ArrayList<String> plan=new ArrayList<>();
        for(Segment part:bilingual(word,"",example,"",wordRepeats,exampleRepeats,includeExample,false))plan.add(part.text);
        return plan;
    }
    static List<Segment> bilingual(String word,String meaning,String example,String exampleMeaning,int wordRepeats,int exampleRepeats,boolean includeExample,boolean includeChinese){
        ArrayList<Segment> plan=new ArrayList<>();
        if(present(word))for(int i=0;i<clamp(wordRepeats);i++){
            plan.add(new Segment(word,"ja"));
            if(includeChinese&&present(meaning))plan.add(new Segment(meaning,"zh"));
        }
        if(includeExample&&present(example))for(int i=0;i<clamp(exampleRepeats);i++){
            plan.add(new Segment(example,"ja"));
            if(includeChinese&&present(exampleMeaning))plan.add(new Segment(exampleMeaning,"zh"));
        }
        return plan;
    }
    private static boolean present(String text){return text!=null&&!text.trim().isEmpty();}
    static int clamp(int value){return Math.max(1,Math.min(5,value));}
    private SpeechPlan(){}
}
