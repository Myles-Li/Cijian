package com.myles.cijian;

/** 统计接触，不将显示过单词等同于掌握。 / Counts exposure, never claims that displaying a word means it has been mastered. */
final class FirstPass {
    final int total,encountered,today,target;
    FirstPass(int total,int encountered,int today,int target){this.total=total;this.encountered=encountered;this.today=today;this.target=Math.max(1,target);}
    int remaining(){return Math.max(0,total-encountered);}
    int days(){return (remaining()+target-1)/target;}
    int percent(){return total==0?0:(int)(encountered*100L/total);}
}
