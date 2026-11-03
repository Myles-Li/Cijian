package com.myles.kotobafloat;

import java.util.ArrayDeque;

/** 最近标记可逐条撤销；新操作和撤销都会延长整个短期窗口。 / Recent marks undo in reverse order; each action renews the short undo window. */
final class MarkHistory<T> {
    private final ArrayDeque<T> entries=new ArrayDeque<>();private long expires;
    void push(T entry,long now){expire(now);entries.push(entry);while(entries.size()>32)entries.removeLast();expires=now+8000;}
    T peek(long now){expire(now);return entries.peek();}
    T pop(long now){expire(now);T value=entries.poll();if(value!=null)expires=now+8000;return value;}
    int size(long now){expire(now);return entries.size();}
    void clear(){entries.clear();expires=0;}
    private void expire(long now){if(now>=expires)entries.clear();}
}
