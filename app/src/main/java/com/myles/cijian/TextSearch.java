package com.myles.cijian;

import java.text.Normalizer;
import java.util.*;

/** 按字面搜索并统一假名，仅对查询执行严格罗马字转换。 / Literal search with kana folding and strict, query-only romaji conversion. */
final class TextSearch {
    private static final Map<String,String> ROMAJI=new HashMap<>();
    static {
        String[] rows={"a:あ i:い u:う e:え o:お", "ka:か ki:き ku:く ke:け ko:こ", "sa:さ shi:し si:し su:す se:せ so:そ", "ta:た chi:ち ti:ち tsu:つ tu:つ te:て to:と", "na:な ni:に nu:ぬ ne:ね no:の", "ha:は hi:ひ fu:ふ hu:ふ he:へ ho:ほ", "ma:ま mi:み mu:む me:め mo:も", "ya:や yu:ゆ yo:よ", "ra:ら ri:り ru:る re:れ ro:ろ", "wa:わ wi:うぃ we:うぇ wo:を", "ga:が gi:ぎ gu:ぐ ge:げ go:ご", "za:ざ ji:じ zi:じ zu:ず ze:ぜ zo:ぞ", "da:だ di:ぢ du:づ de:で do:ど", "ba:ば bi:び bu:ぶ be:べ bo:ぼ", "pa:ぱ pi:ぴ pu:ぷ pe:ぺ po:ぽ", "va:ゔぁ vi:ゔぃ vu:ゔ ve:ゔぇ vo:ゔぉ", "sha:しゃ shu:しゅ sho:しょ cha:ちゃ chu:ちゅ cho:ちょ ja:じゃ ju:じゅ jo:じょ", "fa:ふぁ fi:ふぃ fe:ふぇ fo:ふぉ", "xa:ぁ xi:ぃ xu:ぅ xe:ぇ xo:ぉ la:ぁ li:ぃ lu:ぅ le:ぇ lo:ぉ xtsu:っ ltsu:っ xya:ゃ xyu:ゅ xyo:ょ"};
        for(String row:rows)for(String pair:row.split(" ")){String[] p=pair.split(":");ROMAJI.put(p[0],p[1]);}
        String[] starts={"k","g","s","z","t","d","n","h","b","p","m","r"};String[] kana={"き","ぎ","し","じ","ち","ぢ","に","ひ","び","ぴ","み","り"};
        for(int i=0;i<starts.length;i++)for(String[] end:new String[][]{{"a","ゃ"},{"u","ゅ"},{"o","ょ"}})ROMAJI.put(starts[i]+"y"+end[0],kana[i]+end[1]);
    }
    static String normalize(String value){
        String text=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);StringBuilder out=new StringBuilder();
        for(char c:text.toCharArray())out.append(c>='ァ'&&c<='ヶ'?(char)(c-0x60):c);return out.toString();
    }
    static String romaji(String value){
        String s=normalize(value).trim();if(!s.matches("[a-z' -]+"))return null;StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();){char c=s.charAt(i);
            if(c==' '){out.append(c);i++;continue;}if(c=='-'){out.append('ー');i++;continue;}
            if(c=='n'){
                if(i+1==s.length()){out.append('ん');i++;continue;}
                char next=s.charAt(i+1);if(next=='\''){out.append('ん');i+=2;continue;}
                if(next=='n'){out.append('ん');i+=(i+2==s.length()||"aiueoy".indexOf(s.charAt(i+2))<0)?2:1;continue;}
                if("aiueoy".indexOf(next)<0){out.append('ん');i++;continue;}
            }
            if(c=='t'&&s.startsWith("ch",i+1)){out.append('っ');i++;continue;}
            if(i+1<s.length()&&c==s.charAt(i+1)&&"bcdfghjkpqrstvwxyz".indexOf(c)>=0){out.append('っ');i++;continue;}
            boolean found=false;for(int length=Math.min(4,s.length()-i);length>0;length--){String kana=ROMAJI.get(s.substring(i,i+length));if(kana!=null){out.append(kana);i+=length;found=true;break;}}
            if(!found)return null;
        }return out.toString();
    }
    static final class Query {
        final String literal,kana;
        Query(String value){literal=normalize(value).trim();kana=romaji(literal);}
        boolean matches(String indexed){return indexed.contains(literal)||kana!=null&&indexed.contains(kana);}
    }
}
