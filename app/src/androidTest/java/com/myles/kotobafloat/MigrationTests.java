package com.myles.kotobafloat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import org.json.JSONObject;

final class MigrationTests {
    private int checks;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void copy(Context c,File to)throws IOException{try(InputStream in=c.getAssets().open("vocabulary.db");FileOutputStream out=new FileOutputStream(to)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    private static String scalar(SQLiteDatabase db,String sql){try(android.database.Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getString(0):"";}}
    static int run(Context context)throws Exception{return new MigrationTests().test(context);}
    private int test(Context context)throws Exception{
        File liveFile=new File(context.getCacheDir(),"migration-live.db"),nextFile=new File(context.getCacheDir(),"migration-next.db");copy(context,liveFile);copy(context,nextFile);
        try(SQLiteDatabase live=SQLiteDatabase.openDatabase(liveFile.getPath(),null,SQLiteDatabase.OPEN_READWRITE);SQLiteDatabase next=SQLiteDatabase.openDatabase(nextFile.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
            String first=scalar(live,"SELECT id FROM words ORDER BY id LIMIT 1"),removed=scalar(live,"SELECT id FROM words ORDER BY id LIMIT 1 OFFSET 1");
            live.execSQL("INSERT INTO progress(id,favorite,familiar,seen,last_seen) VALUES (?,1,1,7,1234)",new Object[]{first});
            live.execSQL("INSERT INTO progress(id,favorite,seen) VALUES (?,1,3)",new Object[]{removed});
            live.execSQL("CREATE TABLE reviews(id TEXT PRIMARY KEY,due_at INTEGER)");live.execSQL("INSERT INTO reviews VALUES (?,999)",new Object[]{first});
            next.execSQL("UPDATE words SET meaning='更新后的释义' WHERE id=?",new Object[]{first});
            next.execSQL("DELETE FROM words WHERE id=?",new Object[]{removed});
            next.execSQL("INSERT INTO words(id,word,reading,meaning,level,rank,category) VALUES ('fixture-added','追加','ついか','新增','N5',0,'')");
            JSONObject version=new JSONObject(scalar(next,"SELECT value FROM meta WHERE key='provenance'"));version.put("commit","fixture-update").put("source_sha256","fixture-hash");
            next.execSQL("UPDATE meta SET value=? WHERE key='provenance'",new Object[]{version.toString()});
            DeckUpdater.apply(live,nextFile,version.toString());
            check(scalar(live,"SELECT meaning FROM words WHERE id='"+first+"'").equals("更新后的释义"),"Dictionary corrections applied");
            check(scalar(live,"SELECT count(*) FROM words WHERE id='fixture-added'").equals("1"),"New dictionary card imported");
            check(scalar(live,"SELECT count(*) FROM words WHERE id='"+removed+"'").equals("0"),"Removed source card hidden");
            check(scalar(live,"SELECT seen FROM progress WHERE id='"+first+"'").equals("7"),"Seen count survives dictionary update");
            check(scalar(live,"SELECT favorite FROM progress WHERE id='"+removed+"'").equals("1"),"Removed card keeps its historical progress");
            check(scalar(live,"SELECT due_at FROM reviews WHERE id='"+first+"'").equals("999"),"Review schedule survives dictionary update");
            String before=scalar(live,"SELECT value FROM meta WHERE key='provenance'");
            next.execSQL("UPDATE words SET word='' WHERE id='fixture-added'");
            try{DeckUpdater.apply(live,nextFile,version.toString());throw new AssertionError("Bad source accepted");}catch(IOException expected){}
            check(scalar(live,"SELECT word FROM words WHERE id='fixture-added'").equals("追加"),"Invalid source leaves previous words untouched");
            next.execSQL("UPDATE words SET word='追加' WHERE id='fixture-added'");
            next.execSQL("ALTER TABLE words RENAME TO complete_words");next.execSQL("CREATE TABLE words AS SELECT id,word,reading,meaning,traditional,example,example_meaning,note,pitch,pos,level,rank FROM complete_words");
            try{DeckUpdater.apply(live,nextFile,version.toString());throw new AssertionError("Missing column accepted");}catch(android.database.sqlite.SQLiteException expected){}
            check(scalar(live,"SELECT count(*) FROM words").equals("10641")&&scalar(live,"SELECT value FROM meta WHERE key='provenance'").equals(before),"Failed dictionary transaction rolls back words and version");
        }finally{liveFile.delete();nextFile.delete();}
        return checks;
    }
}
