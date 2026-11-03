package com.myles.kotobafloat;

public final class FeatureLogicTests {
    static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        String[][] words={{"taberu","たべる"},{"gakkou","がっこう"},{"shinbun","しんぶん"},{"kan'i","かんい"},{"konnichiha","こんにちは"},{"matcha","まっちゃ"},{"ko-hi-","こーひー"},{"ryokou","りょこう"},{"n","ん"},{"onna","おんな"}};
        for(String[] pair:words)check(pair[1].equals(TextSearch.romaji(pair[0])),pair[0]);
        check(TextSearch.normalize("ｽｰﾊﾟｰｺﾝﾋﾟｭｰﾀｰ").equals("すーぱーこんぴゅーたー"),"Halfwidth folding");
        check(TextSearch.romaji("school")==null,"Unsupported romaji does not invent kana");
        check(new TextSearch.Query("TABERU").matches(TextSearch.normalize("食べる\u001fたべる")),"Search checks both literal text and kana");
        check(!new TextSearch.Query("%").matches("食べる"),"Punctuation remains literal");
        WeakWords.State p=WeakWords.rate(null,0,"2026-10-01",1);check(!p.active(),"A first forgotten date is insufficient");
        for(int i=0;i<100;i++)p=WeakWords.rate(p,0,"2026-10-01",i+2);check(p.misses==1,"Same-day repetition deduplicates");
        p=WeakWords.rate(p,0,"2026-10-02",200);check(p.active(),"Two dates qualify");p=WeakWords.rate(p,2,"2026-10-02",201);check(p.wins==0,"Immediate recall is not stable evidence");
        p=WeakWords.rate(p,2,"2026-10-03",202);p=WeakWords.rate(p,2,"2026-10-03",203);check(p.wins==1,"Successful dates deduplicate");p=WeakWords.rate(p,1,"2026-10-04",204);check(p.wins==0&&p.active(),"Blur interrupts the successful streak");
        for(int i=5;i<8;i++)p=WeakWords.rate(p,2,"2026-10-0"+i,200+i);check(!p.active(),"Three successful dates graduate");
        WeakWords.State before=p;check(WeakWords.rate(p,0,"2026-09-01",1)==before,"Older clocks do not overwrite newer evidence");
        for(String value:new String[]{"a","nn","xtsu","tya","kya","!","   ","123","中文"}){TextSearch.Query q=new TextSearch.Query(value);check(q.literal!=null,"All query kinds terminate safely");}
        try{new WeakWords.State(0,1,"2026-10-01","2026-10-02",1);throw new AssertionError("Impossible weak streak accepted");}catch(IllegalArgumentException expected){}check(true,"Impossible restored weak streak rejected");
        System.out.println("PASS: "+checks+" search and weak-word logic checks.");
    }
}
