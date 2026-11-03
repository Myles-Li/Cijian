package com.myles.kotobafloat;

import java.util.*;

/** 只用已有释义，排除重复与明显歧义。 / Use existing meanings and reject duplicates or obvious ambiguity. */
final class QuizOptions {
    static final class Entry {final String id,head,reading,meaning;Entry(String id,String head,String reading,String meaning){this.id=id;this.head=head;this.reading=reading;this.meaning=meaning;}}
    static String normalize(String text){return text.replaceAll("[\\p{P}\\p{Z}\\s]","").toLowerCase(Locale.ROOT);}
    static boolean conflicts(Entry a,Entry b){
        if(a.head.equals(b.head)||a.reading.equals(b.reading))return true;
        String x=normalize(a.meaning),y=normalize(b.meaning);if(x.isEmpty()||y.isEmpty()||x.contains(y)||y.contains(x))return true;
        for(String one:a.meaning.split("[；;，,、\\n]"))for(String two:b.meaning.split("[；;，,、\\n]"))if(!normalize(one).isEmpty()&&normalize(one).equals(normalize(two)))return true;
        return false;
    }
    static List<Entry> make(Entry answer,List<Entry> candidates){
        ArrayList<Entry> choices=new ArrayList<>();choices.add(answer);ArrayList<Entry> pool=new ArrayList<>(candidates);Random random=new Random(answer.id.hashCode());Collections.shuffle(pool,random);
        for(Entry item:pool){if(item.id.equals(answer.id))continue;boolean valid=true;for(Entry old:choices)if(conflicts(old,item)){valid=false;break;}if(valid)choices.add(item);if(choices.size()==4)break;}
        if(choices.size()!=4)return Collections.emptyList();Collections.shuffle(choices,random);return choices;
    }
}
