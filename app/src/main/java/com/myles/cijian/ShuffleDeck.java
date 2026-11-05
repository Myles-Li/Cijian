package com.myles.cijian;

import java.io.*;
import java.util.*;

/** 随机整轮持久保存，每张卡访问一次后才开始新轮。 / A persisted shuffled cycle. Every card is visited once before a fresh cycle starts. */
final class ShuffleDeck {
    private static final int MAGIC=0x4b465132, RECENT_LIMIT=20, MAX_ITEMS=50000;
    private final Map<String,String> identities;
    private final ArrayList<String> order=new ArrayList<>();
    private final ArrayDeque<String> recent=new ArrayDeque<>();
    private final Random random;
    private final String scope;
    private int position, furthest, recentLimit;

    ShuffleDeck(Map<String,String> candidates,String saved,String scope) {
        this(candidates,saved,scope,new Random());
    }
    ShuffleDeck(Map<String,String> candidates,String saved,String scope,Random random) {
        identities=new LinkedHashMap<>(candidates);this.scope=scope;this.random=random;
        recentLimit=Math.min(RECENT_LIMIT,Math.max(0,new HashSet<>(identities.values()).size()-1));
        boolean restored=restore(saved);
        if(!restored) {
            order.clear();order.addAll(identities.keySet());Collections.shuffle(order,random);
            position=0;furthest=0;avoidRecent(0);
        }
        remember();
    }
    private boolean restore(String saved) {
        if(saved==null||saved.isEmpty()||saved.length()>4000000)return false;
        ArrayList<String> old=new ArrayList<>();ArrayList<String> oldRecent=new ArrayList<>();
        String oldScope;int oldPosition,oldFurthest;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(saved)))) {
            if(in.readInt()!=MAGIC)return false;
            oldScope=in.readUTF();oldPosition=in.readInt();oldFurthest=in.readInt();
            int count=in.readInt();if(count<0||count>MAX_ITEMS)return false;
            HashSet<String> unique=new HashSet<>();
            for(int i=0;i<count;i++){String id=in.readUTF();if(!unique.add(id))return false;old.add(id);}
            int recentCount=in.readInt();if(recentCount<0||recentCount>RECENT_LIMIT)return false;
            for(int i=0;i<recentCount;i++)oldRecent.add(in.readUTF());
            if(oldPosition<0||oldPosition>Math.max(0,count-1)||oldFurthest<oldPosition||oldFurthest>Math.max(0,count-1))return false;
        }catch(Exception invalid){return false;}
        recent.addAll(oldRecent);trimRecent();
        if(!scope.equals(oldScope))return false;
        int survivingBefore=0,survivingThroughFurthest=0;
        boolean currentExists=oldPosition<old.size()&&identities.containsKey(old.get(oldPosition));
        for(int i=0;i<old.size();i++)if(identities.containsKey(old.get(i))) {
            order.add(old.get(i));
            if(i<oldPosition)survivingBefore++;
            if(i<=oldFurthest)survivingThroughFurthest++;
        }
        HashSet<String> known=new HashSet<>(old);
        ArrayList<String> added=new ArrayList<>();
        for(String id:identities.keySet())if(!known.contains(id))added.add(id);
        Collections.shuffle(added,random);order.addAll(added);
        if(order.isEmpty()){position=0;furthest=0;return true;}
        if(!currentExists&&survivingBefore>=order.size()) {
            Collections.shuffle(order,random);position=0;furthest=0;avoidRecent(0);
        }else{
            position=Math.min(survivingBefore,order.size()-1);
            furthest=Math.max(position,Math.min(order.size()-1,survivingThroughFurthest-1));
            if(!currentExists)avoidRecent(position);
        }
        return true;
    }
    String current() {return order.isEmpty()?null:order.get(position);}
    String next() {
        if(order.isEmpty())return null;
        if(position+1<order.size()) {
            position++;
            if(position>furthest){avoidRecent(position);furthest=position;}
        }else{
            Collections.shuffle(order,random);position=0;furthest=0;avoidRecent(0);
        }
        remember();return current();
    }
    String previous() {
        if(!order.isEmpty()){position=Math.max(0,position-1);remember();}
        return current();
    }
    private void avoidRecent(int start) {
        if(start>=order.size())return;
        for(int i=start;i<order.size();i++)if(!recent.contains(identities.get(order.get(i)))) {
            Collections.swap(order,start,i);return;
        }
        // 候选极少或均重复时，优先选择最久未遇见的词条。 / For very small/duplicate-only pools, choose the least recently encountered identity.
        int best=start,bestAge=Integer.MAX_VALUE;
        ArrayList<String> history=new ArrayList<>(recent);
        for(int i=start;i<order.size();i++){
            int age=history.indexOf(identities.get(order.get(i)));
            if(age<bestAge){best=i;bestAge=age;}
        }
        Collections.swap(order,start,best);
    }
    private void remember() {
        String id=current();if(id==null)return;
        String identity=identities.get(id);
        recent.remove(identity);recent.addLast(identity);trimRecent();
    }
    private void trimRecent(){while(recent.size()>recentLimit)recent.removeFirst();}
    String save() {
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(bytes)){
                out.writeInt(MAGIC);out.writeUTF(scope);out.writeInt(position);out.writeInt(furthest);
                out.writeInt(order.size());for(String id:order)out.writeUTF(id);
                out.writeInt(recent.size());for(String key:recent)out.writeUTF(key);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
}
