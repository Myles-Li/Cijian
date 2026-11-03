package com.myles.kotobafloat;

import java.util.*;

final class StudySession {
    final List<Word> words;
    final String scope,label;
    final StudyGroup group;
    private StudySession(List<Word> words,String scope,String label,StudyGroup group){this.words=words;this.scope=scope;this.label=label;this.group=group;}
    static String baseScope(Prefs prefs){
        boolean custom=prefs.groupMode().equals("CUSTOM");String level=custom?"ALL":prefs.level(),frequency=custom?"ALL":Frequency.effective(level,prefs.frequency());
        return level+"|"+frequency+"|"+prefs.studyMode()+"|"+prefs.effectiveHideFamiliar()+"|"+prefs.groupMode()+"|"+prefs.groupSize()+(custom?"|"+fingerprint(prefs.customWords()):"");
    }
    static StudySession load(WordStore store,Prefs prefs) {
        ScopeMemory.migrate(prefs);
        boolean custom=prefs.groupMode().equals("CUSTOM");
        String level=custom?"ALL":prefs.level(),frequency=custom?"ALL":Frequency.effective(level,prefs.frequency());
        String mode=prefs.studyMode();boolean hide=prefs.effectiveHideFamiliar();
        List<Word> pool=store.words(level,frequency,"",mode,hide,false,0);
        String scope=baseScope(prefs);
        String label="全部范围";StudyGroup group=null;
        if(custom){
            List<String> ids=prefs.customWords();Map<String,Word> indexed=new HashMap<>();for(Word w:pool)indexed.put(w.id,w);
            pool=new ArrayList<>();for(String id:ids)if(indexed.containsKey(id))pool.add(indexed.get(id));
            label="自选组 · "+pool.size()+" 词";
        }else if(prefs.groupMode().equals("SIZE")){
            Map<String,Word> indexed=new LinkedHashMap<>();for(Word w:pool)indexed.put(w.id,w);
            group=new StudyGroup(indexed.keySet(),ScopeMemory.group(prefs,scope),scope,prefs.groupSize());
            ScopeMemory.group(prefs,scope,group.save());pool=new ArrayList<>();
            for(String id:group.members(indexed.keySet()))pool.add(indexed.get(id));
            label="第 "+(group.page+1)+" / "+group.count()+" 组 · "+pool.size()+" 词";
            scope+="|page="+group.page;
        }
        return new StudySession(pool,scope,label,group);
    }
    private static String fingerprint(List<String> ids){
        try{return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n",ids).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static List<Word> range(WordStore store,Prefs prefs){
        boolean custom=prefs.groupMode().equals("CUSTOM");
        List<Word> pool=store.words(custom?"ALL":prefs.level(),custom?"ALL":prefs.frequency(),"",prefs.studyMode(),false,false,0);
        if(!custom)return pool;
        Set<String> ids=new HashSet<>(prefs.customWords());pool.removeIf(w->!ids.contains(w.id));return pool;
    }
    static void next(WordStore store,Prefs prefs){
        StudySession current=load(store,prefs);
        if(current.group!=null){current.group.next();ScopeMemory.group(prefs,baseScope(prefs),current.group.save());prefs.put("groupRevision",prefs.data.getInt("groupRevision",0)+1);}
    }
}
