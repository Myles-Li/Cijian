package com.myles.cijian;

import java.util.*;

final class StudyTasks {
    static final class State{final long due;final int stage,rating;State(long due,int stage){this(due,stage,2);}State(long due,int stage,int rating){this.due=due;this.stage=stage;this.rating=rating;}}
    final int total,introduced,todayNew,todayReviewed,due,stable,availableNew;
    final List<String> dueIds,newIds;
    final long nextDue;
    final int learningSoon;
    StudyTasks(List<String> range,Map<String,State> states,Set<String> newToday,Set<String> reviewedToday,long now){
        total=range.size();dueIds=new ArrayList<>();newIds=new ArrayList<>();int started=0,n=0,r=0,s=0,soon=0;long next=Long.MAX_VALUE;
        for(String id:range){State state=states.get(id);if(state==null)newIds.add(id);else{started++;if(state.due<=now)dueIds.add(id);if(stable(state))s++;if(state.due>now){next=Math.min(next,state.due);if(state.stage==0)soon++;}}if(state!=null&&newToday.contains(id))n++;if(reviewedToday.contains(id))r++;}
        dueIds.sort(Comparator.comparingLong(id->states.get(id).due));introduced=started;todayNew=n;todayReviewed=r;due=dueIds.size();stable=s;availableNew=newIds.size();nextDue=next;learningSoon=soon;
    }
    static boolean stable(State state){return state!=null&&state.stage>=3&&state.rating==2;}
    List<String> plan(int dailyNew,int batch,boolean onlyDue){List<String> out=new ArrayList<>();for(String id:dueIds){if(out.size()>=batch)break;out.add(id);}if(!onlyDue)for(String id:newIds){if(out.size()>=batch||out.size()-Math.min(dueIds.size(),batch)>=Math.max(0,dailyNew-todayNew))break;out.add(id);}return out;}
}
