package com.myles.cijian;

/** 单个交互学习界面拥有音频和评分权。 / One interactive surface owns audio and rating actions. */
final class StudySurface {
    private static Runnable release;
    private static final java.util.Set<Runnable> observers=new java.util.LinkedHashSet<>();
    static void observe(Runnable observer){observers.add(observer);}
    static void forget(Runnable observer){observers.remove(observer);}
    static void changed(){for(Runnable observer:new java.util.ArrayList<>(observers))observer.run();}
    static void claim(Runnable next){Runnable old=release;release=next;if(old!=null&&old!=next)old.run();}
    static void leave(Runnable current){if(release==current)release=null;}
}
