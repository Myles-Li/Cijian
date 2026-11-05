package com.myles.cijian;

import android.content.Context;
import android.content.res.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** 仅界面文案查表；词条、释义、例句和语音内容不做自动翻译。 / UI-only lookup; dictionary fields and speech content are never automatically translated. */
final class L {
    private static volatile String language="ZH";
    private static volatile Map<String,String> entries=Collections.emptyMap();
    static Context context(Context base){
        init(base);Configuration c=new Configuration();
        c.setLocale(language.equals("EN")?Locale.ENGLISH:language.equals("JA")?Locale.JAPANESE:Locale.SIMPLIFIED_CHINESE);c.setLayoutDirection(Locale.ENGLISH);
        return base.createConfigurationContext(c);
    }
    static synchronized void init(Context c){
        String choice=UiLanguage.resolve(new Prefs(c).data.getString("uiLanguage",null),android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0));
        if(choice.equals(language)&&(!entries.isEmpty()||choice.equals("ZH")))return;
        Map<String,String> next=new HashMap<>();
        if(!choice.equals("ZH"))try(java.io.InputStream in=c.getAssets().open(choice.equals("JA")?"ui-ja.json":"ui-en.json")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            JSONObject data=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();next.put(key,data.getString(key));}
        }catch(Exception error){android.util.Log.e("CijianLocale","UI catalog unavailable",error);choice="ZH";}
        entries=Collections.unmodifiableMap(next);language=choice;
    }
    static String t(String value){return value==null?null:entries.getOrDefault(value,value);}
    static String[] labels(String[] source){String[] copy=source.clone();for(int i=0;i<copy.length;i++)copy[i]=t(copy[i]);return copy;}
    static String status(String value){
        if(value==null||language.equals("ZH"))return value;
        String exact=entries.get(value);if(exact!=null)return exact;
        for(String prefix:new String[]{"上次生成分享备份：","上次保存到下载：","上次保存文件："})if(value.startsWith(prefix))return t(prefix)+value.substring(prefix.length());
        if(value.startsWith("距上次保存备份已过 ")){int end=value.indexOf(" 天，");if(end>0)return t("距上次保存备份已过 ")+value.substring("距上次保存备份已过 ".length(),end)+t(" 天，方便时可以再备份一份。");}
        return value;
    }
    static String group(String value){if(language.equals("ZH"))return value;if(value.equals("全部范围"))return t(value);if(value.startsWith("自选组 · "))return t("自选组 · ")+value.substring("自选组 · ".length()).replace(" 词",t(" 词"));return value.replace("第 ",t("第 ")).replace(" 组 · ",t(" 组 · ")).replace(" 词",t(" 词"));}
    static String grammar(String value){
        if(language.equals("ZH"))return value;
        String[] raw={"自他动词／不规则","自他动词／五段","自他动词／一段","自动词／不规则","他动词／不规则","自动词／五段","他动词／五段","自动词／一段","他动词／一段","原词库未标注","形容动词","い形容词","な形容词","连体词","接续词","感叹词","名词","副词"};
        String[] en={"intransitive/transitive · irregular","intransitive/transitive · godan","intransitive/transitive · ichidan","intransitive · irregular","transitive · irregular","intransitive · godan","transitive · godan","intransitive · ichidan","transitive · ichidan","Not provided by source","na-adjective","i-adjective","na-adjective","prenominal","conjunction","interjection","noun","adverb"};
        String[] ja={"自他動詞／不規則","自他動詞／五段","自他動詞／一段","自動詞／不規則","他動詞／不規則","自動詞／五段","他動詞／五段","自動詞／一段","他動詞／一段","元データに記載なし","形容動詞","い形容詞","な形容詞","連体詞","接続詞","感動詞","名詞","副詞"};
        for(int i=0;i<raw.length;i++)value=value.replace(raw[i],language.equals("JA")?ja[i]:en[i]);return value;
    }
    private L(){}
}
