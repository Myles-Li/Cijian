package com.myles.kotobafloat;

import java.util.*;

final class ReviewSession {
    final ArrayList<String> queue;
    final Map<String,Integer> attempts=new HashMap<>();
    int position;
    ReviewSession(Collection<String> ids,int position){queue=new ArrayList<>(ids);this.position=Math.max(0,Math.min(position,queue.size()));}
    String current(){return position>=queue.size()?null:queue.get(position);}
    void answer(int rating){answer(rating,true);}
    void answer(int rating,boolean retry){String id=current();if(id==null)return;int n=attempts.getOrDefault(id,0)+1;attempts.put(id,n);position++;if(retry&&rating==0&&n<2&&queue.size()-position>=2)queue.add(id);}
}
