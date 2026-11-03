package com.myles.kotobafloat;

import java.util.*;

public final class LogicTests {
    static long checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static LinkedHashMap<String,String> pool(int count){
        LinkedHashMap<String,String> p=new LinkedHashMap<>();for(int i=0;i<count;i++)p.put("id-"+i,"word-"+i);return p;
    }
    public static void main(String[] args){
        long backupNow=30*ReviewPlan.DAY;
        check(BackupReminder.message(backupNow,16*ReviewPlan.DAY,0,true).contains("14 天"),"A saved backup becomes gently stale at fourteen days");
        check(!BackupReminder.message(backupNow,17*ReviewPlan.DAY,0,true).contains("已过"),"Recent backups do not trigger a stale reminder");
        check(BackupReminder.message(backupNow,16*ReviewPlan.DAY,29*ReviewPlan.DAY,true).contains("不代表发送成功"),"Generating a newer shared file never claims external delivery");
        check(BackupReminder.message(backupNow,0,0,true).contains("卸载"),"A device with records but no known saved backup gets a passive reminder");
        check(!BackupReminder.message(backupNow,0,0,false).contains("卸载"),"Empty installs are not warned about nonexistent learning records");
        check(!BackupReminder.message(backupNow,31*ReviewPlan.DAY,0,true).contains("已过"),"Clock changes never produce negative-age stale messages");
        Map<String,StudyTasks.State> backlog=new LinkedHashMap<>();List<String> backlogRange=new ArrayList<>();
        for(int i=0;i<35;i++){String id="old-"+i;backlogRange.add(id);backlog.put(id,new StudyTasks.State(i,2));}backlogRange.add("new");
        StudyTasks overdue=new StudyTasks(backlogRange,backlog,Collections.emptySet(),Collections.emptySet(),100);
        List<String> light=overdue.plan(10,10,true);check(light.size()==10&&!light.contains("new"),"Light review contains at most ten due words and never introduces a new one");
        ReviewSession shortSession=new ReviewSession(light,0);for(int i=0;i<10;i++)shortSession.answer(0,false);check(shortSession.current()==null&&shortSession.queue.size()==10,"Light sessions finish in ten responses even when every card is forgotten");
        check(overdue.due==35&&overdue.availableNew==1,"Selecting a light batch does not remove or postpone the rest of the backlog");
        check(!StudyTasks.stable(null)&&!StudyTasks.stable(new StudyTasks.State(0,2,2))&&!StudyTasks.stable(new StudyTasks.State(0,3,0))&&StudyTasks.stable(new StudyTasks.State(0,3,2)),"Stable recall requires a sufficiently spaced stage and a remembered latest rating");
        Map<String,StudyTasks.State> taskStates=new LinkedHashMap<>();taskStates.put("due",new StudyTasks.State(0,2));taskStates.put("future",new StudyTasks.State(9999,3));
        StudyTasks tasks=new StudyTasks(Arrays.asList("new-a","due","new-b","future"),taskStates,Collections.singleton("due"),Collections.emptySet(),100);
        check(tasks.plan(2,20,false).equals(Arrays.asList("due","new-a")),"Due cards precede the remaining daily-new quota");
        check(tasks.plan(1,20,false).equals(Collections.singletonList("due")),"Daily new quota never limits due review");
        check(tasks.plan(50,1,false).equals(Collections.singletonList("due")),"Review batch is bounded");
        check(tasks.stable==1&&tasks.nextDue==9999,"Future due and stable stages are separate from first-pass exposure");
        ReviewSession retry=new ReviewSession(Arrays.asList("a","b","c","d"),0);retry.answer(0);check(retry.queue.equals(Arrays.asList("a","b","c","d","a")),"Missed card returns after other cards");retry.answer(2);retry.answer(2);retry.answer(2);retry.answer(0);check(retry.current()==null,"Retries are bounded to avoid endless same-card loops");
        check(Accent.parse("#2f80ED")==0xff2f80ed&&Accent.hex(0xff000000).equals("#000000"),"Six-digit custom colors normalize");
        try{Accent.parse("#GG0000");throw new AssertionError("Bad color accepted");}catch(IllegalArgumentException expected){}
        for(int colorSeed=0;colorSeed<1000;colorSeed++){
            int color=new Random(colorSeed).nextInt(0x1000000)|0xff000000;
            for(boolean dark:new boolean[]{false,true}){
                int[] colors=Accent.palette("CUSTOM",Accent.hex(color),dark);
                check(Accent.contrast(colors[0],dark?0xff15202b:0xffffffff)>=4.5,"Custom button foreground remains legible");
                check(Accent.contrast(colors[0],colors[3])>=4.5,"Custom accent text remains legible on soft background");
            }
        }
        check(new FirstPass(807,7,7,20).days()==40,"First pass uses remaining words and ceiling days");
        check(new FirstPass(807,807,20,20).days()==0,"Finished range has zero remaining days");
        check(new FirstPass(0,0,0,20).percent()==0,"Empty range avoids division by zero");
        check(ReviewPlan.nextStage(4,0)==0&&ReviewPlan.delay(0,0)==60000L,"Forgotten rating resets and returns in one minute");
        check(ReviewPlan.nextStage(1,2)==2&&ReviewPlan.delay(2,2)==3*ReviewPlan.DAY,"Remembered rating advances to three days");
        check(ReviewPlan.nextStage(5,2)==5&&ReviewPlan.delay(5,2)==30*ReviewPlan.DAY,"Review interval caps transparently");
        List<String> groupPool=new ArrayList<>(pool(23).keySet());
        StudyGroup group=new StudyGroup(groupPool,"","scope|5",5);
        check(group.count()==5&&group.members(groupPool).size()==5,"Fixed size and final partial group");
        List<String> first=new ArrayList<>(group.members(groupPool));
        StudyGroup restarted=new StudyGroup(groupPool,group.save(),"scope|5",5);
        check(restarted.members(groupPool).equals(first),"Group members survive restart");
        List<String> without=new ArrayList<>(groupPool);without.remove(first.get(0));
        StudyGroup reduced=new StudyGroup(without,group.save(),"scope|5",5);
        check(reduced.members(without).size()==4&&!reduced.members(without).contains("id-5"),"Marking familiar never backfills fixed group");
        without.removeAll(first);check(reduced.members(without).isEmpty(),"Completed group remains empty until explicit next");
        reduced.next();check(reduced.members(without).equals(groupPool.subList(5,10)),"Next group preserves original slots");
        Set<String> allGroups=new HashSet<>();
        for(int n=0;n<group.count();n++){
            for(String id:group.members(groupPool))check(allGroups.add(id),"A card appears in only one group");group.next();
        }
        check(allGroups.size()==23&&group.members(groupPool).equals(first),"All groups covered and wrap to first");
        StudyGroup changedGroup=new StudyGroup(groupPool,reduced.save(),"different scope",7);
        check(changedGroup.page==0&&changedGroup.members(groupPool).size()==7,"Filter change starts a fresh partition");
        StudyGroup emptyGroup=new StudyGroup(Collections.emptyList(),"","empty",20);emptyGroup.next();
        check(emptyGroup.count()==1&&emptyGroup.members(Collections.emptyList()).isEmpty(),"Empty pool safely cycles");
        StudyGroup corruptGroup=new StudyGroup(groupPool,"scope|5\n999999\nid-0","scope|5",5);
        check(corruptGroup.page==0&&corruptGroup.members(groupPool).size()==5,"Invalid persisted group recovers");
        for(int seed=0;seed<200;seed++){
            Map<String,String> p=pool(83);ShuffleDeck deck=new ShuffleDeck(p,"","N3|HIGH",new Random(seed));
            ArrayDeque<String> recent=new ArrayDeque<>();
            for(int round=0;round<8;round++){
                Set<String> unique=new HashSet<>();
                for(int i=0;i<p.size();i++){
                    String id=deck.current();
                    check(unique.add(id),"Repeated card inside a cycle");
                    check(!recent.contains(id),"Recent card repeated at a cycle boundary");
                    recent.addLast(id);while(recent.size()>20)recent.removeFirst();
                    if(i<82)deck.next();
                }
                check(unique.equals(p.keySet()),"Cycle skipped cards");deck.next();
            }
        }
        Map<String,String> p=pool(65);
        ShuffleDeck original=new ShuffleDeck(p,"","same",new Random(7));
        for(int i=0;i<27;i++)original.next();
        ShuffleDeck restored=new ShuffleDeck(p,original.save(),"same",new Random(99));
        check(original.current().equals(restored.current()),"Current position not restored");
        for(int i=0;i<30;i++)check(original.next().equals(restored.next()),"Remaining cycle changed after restart");
        String visited=restored.current();restored.previous();check(restored.next().equals(visited),"Manual back/forward did not preserve history");
        String removed=restored.current(),upcoming=restored.next();
        Map<String,String> filtered=new LinkedHashMap<>(p);filtered.remove(removed);
        ShuffleDeck beforeRemoval=new ShuffleDeck(p,original.save(),"same",new Random(2));
        String removedCurrent=beforeRemoval.current();String expected=beforeRemoval.next();
        beforeRemoval=new ShuffleDeck(p,original.save(),"same",new Random(2));
        filtered=new LinkedHashMap<>(p);filtered.remove(removedCurrent);
        ShuffleDeck afterRemoval=new ShuffleDeck(filtered,beforeRemoval.save(),"same",new Random(2));
        check(afterRemoval.current().equals(expected),"Removing current familiar word restarted the cycle");
        String prior=original.current();
        ShuffleDeck changed=new ShuffleDeck(p,original.save(),"new-filter",new Random(4));
        check(!changed.current().equals(prior),"Filter change immediately repeated the current word");
        for(int n=0;n<=3;n++){
            ShuffleDeck small=new ShuffleDeck(pool(n),"","small",new Random(3));
            for(int i=0;i<30;i++){
                String current=small.current(),next=small.next();
                check(n>0||current==null&&next==null,"Empty deck");
                if(n>1)check(!current.equals(next),"Small deck repeated adjacent cards");
                small=new ShuffleDeck(pool(n),small.save(),"small",new Random(i));
            }
        }
        ShuffleDeck corrupt=new ShuffleDeck(p,"invalid-base64","scope",new Random(1));
        check(p.containsKey(corrupt.current()),"Corrupt state did not recover");
        Map<String,String> duplicate=pool(100);duplicate.put("id-5","word-6");
        ShuffleDeck copies=new ShuffleDeck(duplicate,"","duplicate",new Random(8));Set<String> ids=new HashSet<>();
        for(int i=0;i<100;i++){check(ids.add(copies.current()),"Homograph duplicated a card");copies.next();}
        check(SpeechPlan.wordThenExample("ことば","例文",2,3,true).equals(Arrays.asList("ことば","ことば","例文","例文","例文")),"Read sequence/order/count");
        check(SpeechPlan.wordThenExample("ことば","例文",3,5,false).equals(Arrays.asList("ことば","ことば","ことば")),"Example opt-out");
        check(SpeechPlan.wordThenExample("ことば","",1,5,true).equals(Collections.singletonList("ことば")),"Missing example");
        check(SpeechPlan.wordThenExample("ことば","例文",-1,100,true).size()==6,"Repeat limits");
        List<SpeechPlan.Segment> bilingual=SpeechPlan.bilingual("単語","词义","例文","例句译文",2,2,true,true);
        check(bilingual.size()==8,"Bilingual repeat count");
        check(bilingual.get(0).text.equals("単語")&&bilingual.get(1).text.equals("词义")&&bilingual.get(4).text.equals("例文")&&bilingual.get(5).text.equals("例句译文"),"Japanese/Chinese order");
        for(int i=0;i<bilingual.size();i++)check(bilingual.get(i).language.equals(i%2==0?"ja":"zh"),"Per-utterance language");
        check(SpeechPlan.bilingual("単語","","例文","",2,1,true,true).size()==3,"Missing translations skipped");
        check(Interval.parseMillis("0.5")==500&&Interval.parseMillis("1.25")==1250,"Fractional interval");
        check(Interval.parseMillis(".125")==125&&Interval.format(15000).equals("15"),"Interval precision and canonical format");
        for(String invalid:new String[]{"0","-1","NaN","0.01","3601","1.1234"}){boolean rejected=false;try{Interval.parseMillis(invalid);}catch(IllegalArgumentException expectedError){rejected=true;}check(rejected,"Invalid interval: "+invalid);}
        System.out.println("PASS: "+checks+" logic assertions; 200 seeds x 8 shuffled cycles, persistence, filters, small decks and speech plans.");
    }
}
