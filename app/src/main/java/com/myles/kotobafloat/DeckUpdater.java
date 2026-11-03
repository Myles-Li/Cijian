package com.myles.kotobafloat;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class DeckUpdater {
    static String stamp(String json)throws Exception{JSONObject o=new JSONObject(json);return o.getString("commit")+"|"+o.getString("source_sha256")+"|"+o.optInt("format_revision",0);}
    static void install(Context context,SQLiteDatabase db){
        File temporary=new File(context.getCacheDir(),"deck-update.db");
        try{
            String incoming;try(InputStream in=context.getAssets().open("deck-provenance.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);incoming=bytes.toString(StandardCharsets.UTF_8.name());}
            String installed="";try(Cursor c=db.rawQuery("SELECT value FROM meta WHERE key='provenance'",null)){if(c.moveToFirst())installed=c.getString(0);}
            if(!installed.isEmpty()&&stamp(incoming).equals(stamp(installed)))return;
            try(InputStream in=context.getAssets().open("vocabulary.db");FileOutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
            apply(db,temporary,incoming);new Prefs(context).put("deck.status","词库内容已更新，学习记录保留。");
        }catch(Exception error){new Prefs(context).put("deck.status","新词库未能安全更新，继续使用原词库。请保留记录并联系维护者。");}
        finally{if(temporary.exists())temporary.delete();}
    }
    static void apply(SQLiteDatabase db,File incoming,String expected)throws Exception{
        db.execSQL("ATTACH DATABASE ? AS incoming",new Object[]{incoming.getAbsolutePath()});
        try{
            try(Cursor c=db.rawQuery("PRAGMA incoming.integrity_check",null)){if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("词库完整性错误");}
            String provenance;try(Cursor c=db.rawQuery("SELECT value FROM incoming.meta WHERE key='provenance'",null)){if(!c.moveToFirst())throw new IOException("缺少来源");provenance=c.getString(0);}
            if(!stamp(provenance).equals(stamp(expected)))throw new IOException("来源版本不匹配");
            try(Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN id='' OR word='' OR meaning='' OR level NOT IN ('N1','N2','N3','N4','N5') THEN 1 ELSE 0 END) FROM incoming.words",null)){c.moveToFirst();if(c.getInt(0)==0||c.getInt(0)>50000||c.getInt(1)>0)throw new IOException("无效词条");}
            try(Cursor c=db.rawQuery("SELECT COUNT(*),(SELECT MIN((SELECT count(*) FROM words),(SELECT count(*) FROM incoming.words))) FROM words w JOIN incoming.words n ON w.id=n.id",null)){c.moveToFirst();if(c.getInt(0)==0||c.getInt(0)*2<c.getInt(1))throw new IOException("大量词条标识变化，需要人工迁移");}
            db.beginTransaction();try{
                db.execSQL("INSERT OR REPLACE INTO words(id,word,reading,meaning,traditional,example,example_meaning,note,pitch,pos,level,rank,category) SELECT id,word,reading,meaning,traditional,example,example_meaning,note,pitch,pos,level,rank,category FROM incoming.words");
                db.execSQL("DELETE FROM words WHERE id NOT IN (SELECT id FROM incoming.words)");
                db.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES ('provenance',?)",new Object[]{provenance});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }finally{db.execSQL("DETACH DATABASE incoming");}
    }
}
