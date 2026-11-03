package com.myles.kotobafloat;

import java.time.LocalDate;

/** 两个不同日期遗忘后入列，之后三个不同日期稳定答对再退出。 / Two different forgotten dates enter; three later successful dates graduate. */
final class WeakWords {
    static final class State {
        final int misses,wins;final String missed,won;final long updated;
        State(int misses,int wins,String missed,String won,long updated){this.misses=misses;this.wins=wins;this.missed=missed;this.won=won;this.updated=updated;validate(this);}
        boolean active(){return misses>=2;}
    }
    static void validate(State s){
        if(s.misses<0||s.misses>5||s.wins<0||s.wins>2||s.wins>0&&s.misses==0||s.updated<0)throw new IllegalArgumentException("无效易忘词记录");
        if(s.misses>0&&s.missed.isEmpty()||s.wins>0&&(s.won.isEmpty()||s.won.compareTo(s.missed)<=0))throw new IllegalArgumentException("无效易忘词日期");
        if(!s.missed.isEmpty())LocalDate.parse(s.missed);if(!s.won.isEmpty())LocalDate.parse(s.won);
    }
    static State rate(State previous,int rating,String day,long now){
        if(rating<0||rating>2)throw new IllegalArgumentException("rating");LocalDate.parse(day);
        State p=previous==null?new State(0,0,"","",0):previous;
        if(now<p.updated)return p;int misses=p.misses,wins=p.wins;String missed=p.missed,won=p.won;
        if(rating==0){if(day.compareTo(missed)>0){misses=Math.min(5,misses+1);missed=day;}wins=0;}
        else if(rating==1)wins=0;
        else if(misses>0&&day.compareTo(missed)>0&&day.compareTo(won)>0){
            wins++;won=day;if(wins>=3){misses=0;wins=0;}
        }
        return new State(misses,wins,missed,won,now);
    }
}
