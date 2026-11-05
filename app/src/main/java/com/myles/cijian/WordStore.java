package com.myles.cijian;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class WordStore {
    private static WordStore instance;
    private final SQLiteDatabase db;
    private final java.util.Map<String,String> searchIndex=new java.util.HashMap<>();
    private final android.content.SharedPreferences settings;
    private static final String SELECT = "SELECT w.id,w.word,w.reading,w.meaning,w.traditional,w.example,w.example_meaning,w.note,w.pitch,w.pos,w.level,COALESCE(p.favorite,0),COALESCE(p.familiar,0),w.category,w.rank FROM words w LEFT JOIN progress p ON w.id=p.id ";

    static synchronized WordStore get(Context context) {
        if (instance == null) instance = new WordStore(context.getApplicationContext());
        return instance;
    }

    private WordStore(Context context) {
        settings=new Prefs(context).data;
        File file = context.getDatabasePath("cijian.db");
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建词库目录");
            File temporary = new File(file.getPath() + ".tmp");
            try (InputStream input = context.getAssets().open("vocabulary.db"); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            } catch (Exception e) { throw new IllegalStateException("无法安装本地词库", e); }
            if (!temporary.renameTo(file)) throw new IllegalStateException("无法保存本地词库");
        }
        db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        db.enableWriteAheadLogging();
        db.execSQL("CREATE INDEX IF NOT EXISTS encounters_id_day ON encounters(id,day)");
        db.execSQL("CREATE TABLE IF NOT EXISTS exposures(id TEXT PRIMARY KEY,first_day TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reviews(id TEXT PRIMARY KEY,due_at INTEGER NOT NULL,stage INTEGER NOT NULL DEFAULT 0,rating INTEGER NOT NULL DEFAULT 0,reviewed_at INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO exposures(id,first_day) SELECT id,MIN(day) FROM encounters GROUP BY id");
        db.execSQL("INSERT OR IGNORE INTO exposures(id,first_day) SELECT id,COALESCE(date(last_seen/1000,'unixepoch','localtime'),'1970-01-01') FROM progress WHERE seen>0");
        db.execSQL("CREATE TABLE IF NOT EXISTS review_started(id TEXT PRIMARY KEY,day TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS review_days(day TEXT NOT NULL,id TEXT NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,remembered INTEGER NOT NULL DEFAULT 0,forgotten INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,id))");
        db.execSQL("INSERT OR IGNORE INTO review_started(id,day) SELECT id,date(reviewed_at/1000,'unixepoch','localtime') FROM reviews");
        db.execSQL("CREATE TABLE IF NOT EXISTS weak_words(id TEXT PRIMARY KEY,misses INTEGER NOT NULL,wins INTEGER NOT NULL,missed TEXT NOT NULL,won TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS mark_changes(id TEXT NOT NULL,field TEXT NOT NULL,value INTEGER NOT NULL,changed_at INTEGER NOT NULL,PRIMARY KEY(id,field))");
        db.execSQL("CREATE TABLE IF NOT EXISTS review_drafts(mode TEXT PRIMARY KEY,state TEXT NOT NULL)");
        RestoreSafety.recover(context,this);
        DeckUpdater.install(context,db);
    }

    synchronized List<Word> words(String level, String query, String mode, boolean hideFamiliar, boolean shuffle, int limit) {
        return words(level,"ALL",query,mode,hideFamiliar,shuffle,limit);
    }
    synchronized List<Word> words(String level, String frequency, String query, String mode, boolean hideFamiliar, boolean shuffle, int limit) {
        ArrayList<String> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        if (!"ALL".equals(level)) { sql.append(" AND w.level=?"); args.add(level); }
        String selectedFrequency=Frequency.effective(level,frequency);
        switch(selectedFrequency) {
            case "HIGH":sql.append(" AND w.category LIKE ?");args.add("%高频%");break;
            case "MID":sql.append(" AND w.category LIKE ?");args.add("%中频%");break;
            case "LOW":sql.append(" AND w.category LIKE ?");args.add("%低频%");break;
            case "HIGH_MID":sql.append(" AND (w.category LIKE ? OR w.category LIKE ?)");args.add("%高频%");args.add("%中频%");break;
            case "MID_LOW":sql.append(" AND (w.category LIKE ? OR w.category LIKE ?)");args.add("%中频%");args.add("%低频%");break;
            case "NONE":sql.append(" AND w.category NOT LIKE ? AND w.category NOT LIKE ? AND w.category NOT LIKE ?");args.add("%高频%");args.add("%中频%");args.add("%低频%");break;
            default:break;
        }
        if (hideFamiliar) sql.append(" AND COALESCE(p.familiar,0)=0");
        if ("favorites".equals(mode)) sql.append(" AND p.favorite=1");
        if ("familiar".equals(mode)) sql.append(" AND p.familiar=1");
        boolean searching=query!=null&&!query.trim().isEmpty();TextSearch.Query search=searching?new TextSearch.Query(query):null;
        sql.append(shuffle ? " ORDER BY RANDOM()" : " ORDER BY w.level DESC,w.rank,w.id");
        if (!searching&&limit > 0) { sql.append(" LIMIT ?"); args.add(Integer.toString(limit)); }
        ArrayList<Word> words = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                if(searching){String id=cursor.getString(0),indexed=searchIndex.get(id);
                    if(indexed==null){indexed=TextSearch.normalize(cursor.getString(1)+"\u001f"+cursor.getString(2)+"\u001f"+cursor.getString(3)+"\u001f"+cursor.getString(4));searchIndex.put(id,indexed);}
                    if(!search.matches(indexed))continue;
                }
                words.add(new Word(cursor));if(searching&&limit>0&&words.size()>=limit)break;
            }
        }
        return words;
    }

    synchronized void seen(Word word) {
        String day=LocalDate.now().toString();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO progress(id) VALUES (?)", new Object[]{word.id});
            db.execSQL("UPDATE progress SET seen=seen+1,last_seen=? WHERE id=?", new Object[]{System.currentTimeMillis(), word.id});
            db.execSQL("INSERT OR IGNORE INTO exposures(id,first_day) VALUES (?,?)",new Object[]{word.id,day});
            db.execSQL("INSERT OR IGNORE INTO encounters(day,id) VALUES (?,?)", new Object[]{day, word.id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    synchronized void favorite(Word word, boolean value) { update(word.id, "favorite", value); word.favorite = value; }
    synchronized void familiar(Word word, boolean value) { update(word.id, "familiar", value); word.familiar = value; }
    private void update(String id,String field,boolean value){
        if(!field.equals("favorite")&&!field.equals("familiar"))throw new IllegalArgumentException("field");
        db.beginTransaction();try{
            db.execSQL("INSERT OR IGNORE INTO progress(id) VALUES (?)",new Object[]{id});
            long previous=0;try(Cursor c=db.rawQuery("SELECT changed_at FROM mark_changes WHERE id=? AND field=?",new String[]{id,field})){if(c.moveToFirst())previous=c.getLong(0);}
            db.execSQL("UPDATE progress SET "+field+"=? WHERE id=?",new Object[]{value?1:0,id});
            db.execSQL("INSERT OR REPLACE INTO mark_changes VALUES (?,?,?,?)",new Object[]{id,field,value?1:0,Math.max(System.currentTimeMillis(),previous+1)});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized int scalar(String sql, String... args) {
        try (Cursor c = db.rawQuery(sql, args)) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }
    synchronized int[] stats() {return stats(LocalDate.now().toString());}
    private int[] stats(String day) {
        return new int[]{scalar("SELECT count(*) FROM encounters WHERE day=?", day),
            scalar("SELECT count(*) FROM progress WHERE familiar=1 AND id IN (SELECT id FROM words)"), scalar("SELECT count(*) FROM progress WHERE favorite=1 AND id IN (SELECT id FROM words)"),
            scalar("SELECT count(*) FROM words")};
    }
    static final class HomeProgress {
        final int[] all;final FirstPass pass;final int rangeToday;
        HomeProgress(int[] all,FirstPass pass,int rangeToday){this.all=all;this.pass=pass;this.rangeToday=rangeToday;}
    }
    synchronized HomeProgress homeProgress(List<Word> range,int target){
        java.util.Set<String> ids=new java.util.HashSet<>();for(Word word:range)ids.add(word.id);
        String day=LocalDate.now().toString();int today=0;try(Cursor c=db.rawQuery("SELECT id FROM encounters WHERE day=?",new String[]{day})){while(c.moveToNext())if(ids.contains(c.getString(0)))today++;}
        return new HomeProgress(stats(day),firstPass(range,target,day),today);
    }
    synchronized long[] weekly(List<Word> range,LocalDate end){
        java.util.Set<String> ids=new java.util.HashSet<>(),reviewed=new java.util.HashSet<>();for(Word w:range)ids.add(w.id);String since=end.minusDays(6).toString(),until=end.toString();long fresh=0,attempts=0;
        try(Cursor c=db.rawQuery("SELECT id FROM exposures WHERE first_day>=? AND first_day<=?",new String[]{since,until})){while(c.moveToNext())if(ids.contains(c.getString(0)))fresh++;}
        try(Cursor c=db.rawQuery("SELECT id,attempts FROM review_days WHERE day>=? AND day<=?",new String[]{since,until})){while(c.moveToNext())if(ids.contains(c.getString(0))){reviewed.add(c.getString(0));attempts+=c.getLong(1);}}
        return new long[]{fresh,reviewed.size(),attempts,weak(range,0).size()};
    }
    synchronized int[] evidence(List<Word> range,long now){
        java.util.Set<String> seen=new java.util.HashSet<>();try(Cursor c=db.rawQuery("SELECT id FROM exposures",null)){while(c.moveToNext())seen.add(c.getString(0));}
        int encountered=0,marked=0;for(Word word:range){if(seen.contains(word.id))encountered++;if(word.familiar)marked++;}
        return new int[]{encountered,marked,tasks(range,now).stable};
    }
    synchronized String provenance() {
        try (Cursor c = db.rawQuery("SELECT value FROM meta WHERE key='provenance'", null)) { return c.moveToFirst() ? c.getString(0) : ""; }
    }
    synchronized FirstPass firstPass(java.util.List<Word> range,int target){return firstPass(range,target,LocalDate.now().toString());}
    private FirstPass firstPass(java.util.List<Word> range,int target,String day){
        java.util.Set<String> ids=new java.util.HashSet<>();for(Word w:range)ids.add(w.id);
        int encountered=0,today=0;
        try(Cursor c=db.rawQuery("SELECT id,first_day FROM exposures",null)){while(c.moveToNext())if(ids.contains(c.getString(0))){encountered++;if(day.equals(c.getString(1)))today++;}}
        return new FirstPass(ids.size(),encountered,today,target);
    }
    synchronized java.util.List<Word> recall(java.util.List<Word> range,int limit,long now){
        java.util.Map<String,Long> due=new java.util.HashMap<>();
        try(Cursor c=db.rawQuery("SELECT e.id,COALESCE(r.due_at,0) FROM exposures e LEFT JOIN reviews r ON e.id=r.id",null)){while(c.moveToNext())if(c.getLong(1)<=now)due.put(c.getString(0),c.getLong(1));}
        java.util.List<Word> out=new java.util.ArrayList<>();for(Word w:range)if(due.containsKey(w.id))out.add(w);
        out.sort((a,b)->{long da=due.get(a.id),db=due.get(b.id);int d=da==0&&db!=0?1:da!=0&&db==0?-1:Long.compare(da,db);return d!=0?d:a.id.compareTo(b.id);});
        return new java.util.ArrayList<>(out.subList(0,Math.min(limit,out.size())));
    }
    synchronized ReviewDraft draft(String mode){
        try(Cursor c=db.rawQuery("SELECT state FROM review_drafts WHERE mode=?",new String[]{mode})){if(!c.moveToFirst())return null;try{return ReviewDraft.parse(c.getString(0));}catch(Exception invalid){return null;}}
    }
    synchronized void startDraft(ReviewDraft d){db.execSQL("INSERT OR REPLACE INTO review_drafts VALUES (?,?)",new Object[]{d.mode,d.json()});}
    synchronized ReviewDraft saveDraft(ReviewDraft before,ReviewDraft after,Word word,int rating){
        db.beginTransaction();try{
            ReviewDraft actual=draft(before.mode);
            if(actual==null||!actual.token.equals(before.token)||actual.revision!=before.revision){db.setTransactionSuccessful();return actual;}
            if(rating>=0){if(word==null||!word.id.equals(before.session.current()))throw new IllegalArgumentException("复习卡片已改变");if(before.mode.equals("WEAK"))rateWeak(word,rating,System.currentTimeMillis());else if(!before.mode.equals("PRACTICE"))rate(word,rating,System.currentTimeMillis());}
            after.revision=before.revision+1;startDraft(after);db.setTransactionSuccessful();return after;
        }finally{db.endTransaction();}
    }
    synchronized Word byId(String id){try(Cursor c=db.rawQuery(SELECT+" WHERE w.id=?",new String[]{id})){return c.moveToFirst()?new Word(c):null;}}
    synchronized StudyTasks tasks(java.util.List<Word> range,long now){
        java.util.List<String> ids=new java.util.ArrayList<>();for(Word w:range)ids.add(w.id);
        java.util.Map<String,StudyTasks.State> states=new java.util.HashMap<>();try(Cursor c=db.rawQuery("SELECT id,due_at,stage,rating FROM reviews",null)){while(c.moveToNext())states.put(c.getString(0),new StudyTasks.State(c.getLong(1),c.getInt(2),c.getInt(3)));}
        java.util.Set<String> started=new java.util.HashSet<>(),done=new java.util.HashSet<>();String day=LocalDate.now().toString();
        try(Cursor c=db.rawQuery("SELECT id FROM review_started WHERE day=?",new String[]{day})){while(c.moveToNext())started.add(c.getString(0));}
        try(Cursor c=db.rawQuery("SELECT id FROM review_days WHERE day=?",new String[]{day})){while(c.moveToNext())done.add(c.getString(0));}
        return new StudyTasks(ids,states,started,done,now);
    }
    synchronized java.util.List<String> practice(java.util.List<Word> range,int limit){
        java.util.Set<String> seen=new java.util.HashSet<>();try(Cursor c=db.rawQuery("SELECT id FROM exposures",null)){while(c.moveToNext())seen.add(c.getString(0));}
        java.util.List<String> ids=new java.util.ArrayList<>();for(Word w:range)if(seen.contains(w.id))ids.add(w.id);java.util.Collections.shuffle(ids);return new java.util.ArrayList<>(ids.subList(0,Math.min(limit,ids.size())));
    }
    private WeakWords.State weakState(String id){try(Cursor c=db.rawQuery("SELECT misses,wins,missed,won,updated_at FROM weak_words WHERE id=?",new String[]{id})){return c.moveToFirst()?new WeakWords.State(c.getInt(0),c.getInt(1),c.getString(2),c.getString(3),c.getLong(4)):null;}}
    private void rateWeak(String id,int rating,long now){String day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();WeakWords.State next=WeakWords.rate(weakState(id),rating,day,now);db.execSQL("INSERT OR REPLACE INTO weak_words VALUES (?,?,?,?,?,?)",new Object[]{id,next.misses,next.wins,next.missed,next.won,next.updated});}
    synchronized void rateWeak(Word word,int rating,long now){db.beginTransaction();try{rateWeak(word.id,rating,now);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    synchronized List<String> weak(List<Word> range,int limit){
        java.util.Set<String> ids=new java.util.HashSet<>();for(Word w:range)ids.add(w.id);List<String> out=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id FROM weak_words WHERE misses>=2 ORDER BY misses DESC,updated_at ASC,id",null)){while(c.moveToNext())if(ids.contains(c.getString(0))){out.add(c.getString(0));if(limit>0&&out.size()>=limit)break;}}return out;
    }
    synchronized void rate(Word word,int rating,long now){
        if(rating<0||rating>2)throw new IllegalArgumentException("rating");
        db.beginTransaction();try{
            int stage=0,previousRating=-1;long previous=0;try(Cursor c=db.rawQuery("SELECT stage,reviewed_at,rating FROM reviews WHERE id=?",new String[]{word.id})){if(c.moveToFirst()){stage=c.getInt(0);previous=c.getLong(1);previousRating=c.getInt(2);}}
            int next=ReviewPlan.nextStage(stage,rating);long delay=ReviewPlan.delay(next,rating);
            if(rating==2&&previous>0&&(stage==0||previousRating<2)){if(now-previous<10*60000L){next=0;delay=10*60000L;}else{next=1;delay=ReviewPlan.DAY;}}
            db.execSQL("INSERT OR REPLACE INTO reviews(id,due_at,stage,rating,reviewed_at) VALUES (?,?,?,?,?)",new Object[]{word.id,now+delay,next,rating,now});
            String day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
            db.execSQL("INSERT OR IGNORE INTO review_started(id,day) VALUES (?,?)",new Object[]{word.id,day});
            db.execSQL("INSERT OR IGNORE INTO review_days(day,id) VALUES (?,?)",new Object[]{day,word.id});
            db.execSQL("UPDATE review_days SET attempts=attempts+1,remembered=remembered+?,forgotten=forgotten+? WHERE day=? AND id=?",new Object[]{rating==2?1:0,rating==0?1:0,day,word.id});
            rateWeak(word.id,rating,now);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized String backup() throws org.json.JSONException {
        org.json.JSONObject result = new org.json.JSONObject();
        result.put("format", BackupFormat.CURRENT);
        result.put("exported", java.time.Instant.now().toString());
        org.json.JSONArray progress = new org.json.JSONArray();
        try (Cursor c = db.rawQuery("SELECT id,favorite,familiar,seen,last_seen FROM progress", null)) {
            while (c.moveToNext()) progress.put(new org.json.JSONObject().put("id",c.getString(0)).put("favorite",c.getInt(1)).put("familiar",c.getInt(2)).put("seen",c.getInt(3)).put("last_seen",c.getLong(4)));
        }
        org.json.JSONArray encounters = new org.json.JSONArray();
        try (Cursor c = db.rawQuery("SELECT day,id FROM encounters", null)) {
            while (c.moveToNext()) encounters.put(new org.json.JSONObject().put("day",c.getString(0)).put("id",c.getString(1)));
        }
        org.json.JSONArray exposures=new org.json.JSONArray(),reviews=new org.json.JSONArray();
        try(Cursor c=db.rawQuery("SELECT id,first_day FROM exposures",null)){while(c.moveToNext())exposures.put(new org.json.JSONObject().put("id",c.getString(0)).put("first_day",c.getString(1)));}
        try(Cursor c=db.rawQuery("SELECT id,due_at,stage,rating,reviewed_at FROM reviews",null)){while(c.moveToNext())reviews.put(new org.json.JSONObject().put("id",c.getString(0)).put("due_at",c.getLong(1)).put("stage",c.getInt(2)).put("rating",c.getInt(3)).put("reviewed_at",c.getLong(4)));}
        org.json.JSONArray started=new org.json.JSONArray(),days=new org.json.JSONArray();
        try(Cursor c=db.rawQuery("SELECT id,day FROM review_started",null)){while(c.moveToNext())started.put(new org.json.JSONObject().put("id",c.getString(0)).put("day",c.getString(1)));}
        try(Cursor c=db.rawQuery("SELECT day,id,attempts,remembered,forgotten FROM review_days",null)){while(c.moveToNext())days.put(new org.json.JSONObject().put("day",c.getString(0)).put("id",c.getString(1)).put("attempts",c.getInt(2)).put("remembered",c.getInt(3)).put("forgotten",c.getInt(4)));}
        result.put("review_started",started).put("review_days",days);
        org.json.JSONArray marks=new org.json.JSONArray();try(Cursor c=db.rawQuery("SELECT id,field,value,changed_at FROM mark_changes ORDER BY id,field",null)){while(c.moveToNext())marks.put(new org.json.JSONObject().put("id",c.getString(0)).put("field",c.getString(1)).put("value",c.getInt(2)).put("changed_at",c.getLong(3)));}result.put("mark_changes",marks);
        org.json.JSONArray weak=new org.json.JSONArray();try(Cursor c=db.rawQuery("SELECT id,misses,wins,missed,won,updated_at FROM weak_words",null)){while(c.moveToNext())weak.put(new org.json.JSONObject().put("id",c.getString(0)).put("misses",c.getInt(1)).put("wins",c.getInt(2)).put("missed",c.getString(3)).put("won",c.getString(4)).put("updated_at",c.getLong(5)));}result.put("weak_words",weak);
        return result.put("progress",progress).put("encounters",encounters).put("exposures",exposures).put("reviews",reviews).put("preferences",BackupSettings.export(settings)).put("deck",new org.json.JSONObject(provenance())).toString(2);
    }
    synchronized void restore(String json) throws Exception {restore(json,true,false);}
    synchronized void restore(String json,boolean includeSettings,boolean keepLegacyLocal) throws Exception {
        BackupPreview.inspect(json);
        org.json.JSONObject result = new org.json.JSONObject(json);
        if (!BackupFormat.supported(result.getString("format"))) throw new IllegalArgumentException("这不是词间的备份文件");
        org.json.JSONArray progress = result.getJSONArray("progress"), encounters = result.getJSONArray("encounters");
        if (progress.length() > 50000 || encounters.length() > 1000000) throw new IllegalArgumentException("备份文件过大");
        org.json.JSONObject preferences=result.optJSONObject("preferences");if(preferences!=null)BackupSettings.validate(preferences);
        org.json.JSONArray exposures=result.optJSONArray("exposures"),reviews=result.optJSONArray("reviews");
        if(exposures!=null&&exposures.length()>50000||reviews!=null&&reviews.length()>50000)throw new IllegalArgumentException("备份文件过大");
        org.json.JSONArray incomingMarks=result.optJSONArray("mark_changes");java.util.Set<String> timed=new java.util.HashSet<>();if(incomingMarks!=null)for(int k=0;k<incomingMarks.length();k++){org.json.JSONObject m=incomingMarks.getJSONObject(k);timed.add(m.getString("id")+"|"+m.getString("field"));}
        db.beginTransaction();
        try {
            for (int i=0; i<progress.length(); i++) {
                org.json.JSONObject p = progress.getJSONObject(i);
                String id = p.getString("id");
                validId(id);
                boolean existed=scalar("SELECT count(*) FROM progress WHERE id=?",id)>0;
                db.execSQL("INSERT OR IGNORE INTO progress(id) VALUES (?)", new Object[]{id});
                for(String field:new String[]{"favorite","familiar"})if(!timed.contains(id+"|"+field)&&!(keepLegacyLocal&&existed)&&scalar("SELECT count(*) FROM mark_changes WHERE id=? AND field=?",id,field)==0)db.execSQL("UPDATE progress SET "+field+"=MAX("+field+",?) WHERE id=?",new Object[]{p.getInt(field)==1?1:0,id});
                db.execSQL("UPDATE progress SET seen=MAX(seen,?),last_seen=MAX(last_seen,?) WHERE id=?",new Object[]{Math.max(0,p.getInt("seen")),Math.max(0,p.getLong("last_seen")),id});
            }
            if(incomingMarks!=null)for(int k=0;k<incomingMarks.length();k++){
                org.json.JSONObject m=incomingMarks.getJSONObject(k);String id=m.getString("id"),field=m.getString("field");long stamp=m.getLong("changed_at"),local=-1;
                try(Cursor c=db.rawQuery("SELECT changed_at FROM mark_changes WHERE id=? AND field=?",new String[]{id,field})){if(c.moveToFirst())local=c.getLong(0);}
                if(stamp>local){db.execSQL("INSERT OR IGNORE INTO progress(id) VALUES (?)",new Object[]{id});db.execSQL("UPDATE progress SET "+field+"=? WHERE id=?",new Object[]{m.getInt("value"),id});db.execSQL("INSERT OR REPLACE INTO mark_changes VALUES (?,?,?,?)",new Object[]{id,field,m.getInt("value"),stamp});}
            }
            for (int i=0; i<encounters.length(); i++) {
                org.json.JSONObject e=encounters.getJSONObject(i);
                String day=LocalDate.parse(e.getString("day")).toString(), id=e.getString("id");
                validId(id);db.execSQL("INSERT OR IGNORE INTO encounters(day,id) VALUES (?,?)",new Object[]{day,id});
            }
            if(exposures!=null)for(int i=0;i<exposures.length();i++){
                org.json.JSONObject e=exposures.getJSONObject(i);String id=e.getString("id"),day=LocalDate.parse(e.getString("first_day")).toString();validId(id);
                db.execSQL("INSERT OR IGNORE INTO exposures(id,first_day) VALUES (?,?)",new Object[]{id,day});
                db.execSQL("UPDATE exposures SET first_day=MIN(first_day,?) WHERE id=?",new Object[]{day,id});
            }
            db.execSQL("INSERT OR IGNORE INTO exposures(id,first_day) SELECT id,MIN(day) FROM encounters GROUP BY id");
            db.execSQL("UPDATE exposures SET first_day=MIN(first_day,COALESCE((SELECT MIN(day) FROM encounters WHERE encounters.id=exposures.id),first_day))");
            if(reviews!=null)for(int i=0;i<reviews.length();i++){
                org.json.JSONObject e=reviews.getJSONObject(i);String id=e.getString("id");validId(id);int stage=e.getInt("stage"),rating=e.getInt("rating");long time=e.getLong("reviewed_at"),due=e.getLong("due_at");
                if(stage<0||stage>5||rating<0||rating>2||time<0||due<time)throw new IllegalArgumentException("无效复习记录");
                db.execSQL("INSERT OR IGNORE INTO reviews(id,due_at,stage,rating,reviewed_at) VALUES (?,?,?,?,?)",new Object[]{id,due,stage,rating,time});
                db.execSQL("UPDATE reviews SET due_at=?,stage=?,rating=?,reviewed_at=? WHERE id=? AND reviewed_at<?",new Object[]{due,stage,rating,time,id,time});
            }
            org.json.JSONArray started=result.optJSONArray("review_started"),days=result.optJSONArray("review_days");
            if(started!=null&&started.length()>50000||days!=null&&days.length()>1000000)throw new IllegalArgumentException("复习记录过大");
            if(started!=null)for(int i=0;i<started.length();i++){org.json.JSONObject e=started.getJSONObject(i);String id=e.getString("id"),day=LocalDate.parse(e.getString("day")).toString();validId(id);db.execSQL("INSERT OR IGNORE INTO review_started(id,day) VALUES (?,?)",new Object[]{id,day});db.execSQL("UPDATE review_started SET day=MIN(day,?) WHERE id=?",new Object[]{day,id});}
            if(days!=null)for(int i=0;i<days.length();i++){org.json.JSONObject e=days.getJSONObject(i);String id=e.getString("id"),day=LocalDate.parse(e.getString("day")).toString();validId(id);int attempts=e.getInt("attempts"),remembered=e.getInt("remembered"),forgotten=e.getInt("forgotten");if(attempts<0||remembered<0||forgotten<0||remembered>attempts||forgotten>attempts)throw new IllegalArgumentException("无效复习次数");db.execSQL("INSERT OR IGNORE INTO review_days(day,id) VALUES (?,?)",new Object[]{day,id});db.execSQL("UPDATE review_days SET attempts=MAX(attempts,?),remembered=MAX(remembered,?),forgotten=MAX(forgotten,?) WHERE day=? AND id=?",new Object[]{attempts,remembered,forgotten,day,id});}
            org.json.JSONArray weak=result.optJSONArray("weak_words");
            if(weak!=null)for(int i=0;i<weak.length();i++){org.json.JSONObject e=weak.getJSONObject(i);String id=e.getString("id");WeakWords.State incoming=new WeakWords.State(e.getInt("misses"),e.getInt("wins"),e.getString("missed"),e.getString("won"),e.getLong("updated_at"));WeakWords.State current=weakState(id);if(current==null||incoming.updated>current.updated)db.execSQL("INSERT OR REPLACE INTO weak_words VALUES (?,?,?,?,?,?)",new Object[]{id,incoming.misses,incoming.wins,incoming.missed,incoming.won,incoming.updated});}
            db.execSQL("INSERT OR IGNORE INTO review_started(id,day) SELECT id,date(reviewed_at/1000,'unixepoch','localtime') FROM reviews");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if(includeSettings&&preferences!=null)BackupSettings.restore(settings,preferences);
    }
    synchronized String localSnapshot()throws Exception{
        org.json.JSONObject root=new org.json.JSONObject().put("backup",new org.json.JSONObject(backup())).put("preferences",RestoreSafety.encodePreferences(settings));
        org.json.JSONArray drafts=new org.json.JSONArray();try(Cursor c=db.rawQuery("SELECT mode,state FROM review_drafts",null)){while(c.moveToNext())drafts.put(new org.json.JSONObject().put("mode",c.getString(0)).put("state",c.getString(1)));}root.put("drafts",drafts);return root.toString();
    }
    synchronized void exactRestore(String snapshot)throws Exception{
        org.json.JSONObject root=new org.json.JSONObject(snapshot);String backup=root.getJSONObject("backup").toString();BackupPreview.inspect(backup);org.json.JSONObject prefs=root.getJSONObject("preferences");RestoreSafety.validatePreferences(prefs);org.json.JSONArray drafts=root.getJSONArray("drafts");
        for(int i=0;i<drafts.length();i++)ReviewDraft.parse(drafts.getJSONObject(i).getString("state"));
        db.beginTransaction();try{for(String table:new String[]{"mark_changes","progress","encounters","exposures","reviews","review_started","review_days","weak_words","review_drafts"})db.delete(table,null,null);restore(backup,false,false);for(int i=0;i<drafts.length();i++){org.json.JSONObject d=drafts.getJSONObject(i);db.execSQL("INSERT INTO review_drafts VALUES (?,?)",new Object[]{d.getString("mode"),d.getString("state")});}db.setTransactionSuccessful();}finally{db.endTransaction();}
        RestoreSafety.restorePreferences(settings,prefs);
    }
    synchronized void discardDrafts(){db.delete("review_drafts",null,null);}
    static void validId(String id){if(id.isEmpty()||id.length()>256)throw new IllegalArgumentException("词条标识无效");}
}
