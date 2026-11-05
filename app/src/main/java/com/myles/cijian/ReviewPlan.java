package com.myles.cijian;

/** 采用简单透明的复习计划，仅在主动回忆时收集评分。 / A small, transparent review schedule; ratings are only collected during voluntary recall. */
final class ReviewPlan {
    static final long DAY=86400000L;
    static int nextStage(int stage,int rating){return rating==0?0:rating==1?Math.max(0,stage-1):Math.min(5,stage+1);}
    static long delay(int stage,int rating){
        if(rating==0)return 60000L;
        if(rating==1)return 10*60000L;
        return new long[]{1,1,3,7,14,30}[Math.max(0,Math.min(5,stage))]*DAY;
    }
}
