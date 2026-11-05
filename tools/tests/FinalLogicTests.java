package com.myles.cijian;
import java.util.*;
public final class FinalLogicTests {
 static int checks;static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 public static void main(String[] args){
  for(String[] p:new String[][]{{"きゃ","1"},{"きゃっとーん","5"},{"ｷｬｯﾄｰﾝ","5"},{"ティッシュ","3"},{"コーヒー","4"},{"にほんご","4"}})check(PitchPattern.morae(p[0]).size()==Integer.parseInt(p[1]),p[0]);
  for(String p:new String[]{"communication","〜にくい","あい／あう","ゃ","っゃ","あい①",""})check(PitchPattern.morae(p).isEmpty(),"Uncertain reading must not plot: "+p);
  for(int length=1;length<=30;length++)for(int accent=0;accent<=length;accent++){
   int falls=0;boolean previous=PitchPattern.high(0,accent);
   for(int i=1;i<=length;i++){boolean next=i==length?accent==0:PitchPattern.high(i,accent);if(previous&&!next)falls++;previous=next;}
   check(falls==(accent==0?0:1),"Exactly the supplied fall, including final mora/particle distinction");
  }
  check(PitchPattern.accents("⓪③⓪",3).equals(Arrays.asList(0,3)),"Alternatives deduplicate without inventing a preferred one");
  for(String p:new String[]{"④","1","⓪?","①・⓪"})check(PitchPattern.accents(p,3).isEmpty(),"Unsupported annotation preserves raw source without plot");
  check(Pronunciation.spoken("エネルギー","(ド) Energie").equals("エネルギー"),"Foreign origin is not spoken as Japanese");
  check(Pronunciation.spoken("漢字","English").isEmpty(),"Ambiguous kanji with origin has no guessed reading");
  check(Pronunciation.spoken("〜にくい","〜にくい").equals("にくい"),"Affix placeholder is not pronounced");
  check(Pronunciation.spoken("高校","こうこう").equals("こうこう"),"Regular source reading remains unchanged");
  System.out.println("PASS: "+checks+" final pitch and pronunciation logic checks.");
 }
}
