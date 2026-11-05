package com.myles.cijian;

import java.util.*;

/** 采用固定分区，移除熟悉卡片后保留空位，不补入新卡。 / A fixed partition: removing learned cards leaves their slots empty, never pulls in new cards. */
final class StudyGroup {
    final List<String> plan=new ArrayList<>();
    final String scope;
    final int size;
    int page;
    StudyGroup(Collection<String> candidates,String saved,String scope,int size) {
        this.scope=scope;this.size=Math.max(1,size);
        String[] fields=saved==null?new String[0]:saved.split("\n",-1);
        boolean valid=fields.length>=3&&fields[0].equals(scope);
        if(valid)try{
            page=Integer.parseInt(fields[1]);
            Set<String> unique=new HashSet<>();
            for(int i=2;i<fields.length;i++)if(!fields[i].isEmpty()&&unique.add(fields[i]))plan.add(fields[i]);
            if(page<0||page>=Math.max(1,(plan.size()+this.size-1)/this.size))valid=false;
        }catch(NumberFormatException invalid){valid=false;}
        if(!valid){plan.clear();page=0;}
        Set<String> known=new HashSet<>(plan);
        for(String id:candidates)if(known.add(id))plan.add(id);
    }
    int count(){return Math.max(1,(plan.size()+size-1)/size);}
    List<String> members(Collection<String> available){
        Set<String> active=new HashSet<>(available);List<String> out=new ArrayList<>();
        for(int i=page*size;i<Math.min(plan.size(),(page+1)*size);i++)if(active.contains(plan.get(i)))out.add(plan.get(i));
        return out;
    }
    void next(){page=(page+1)%count();}
    String save(){return scope+"\n"+page+"\n"+String.join("\n",plan);}
}
