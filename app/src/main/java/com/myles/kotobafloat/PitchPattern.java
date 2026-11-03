package com.myles.kotobafloat;

import java.util.*;

/** 严格按音拍分割，无法支持的读音保留原标记，不猜测绘图。 / Strict mora segmentation: unsupported readings keep their source label, without a guessed plot. */
final class PitchPattern {
    static List<String> morae(String reading){
        String kana=TextSearch.normalize(reading.trim());List<String> out=new ArrayList<>();
        for(int i=0;i<kana.length();i++){char c=kana.charAt(i);if(!(c>='ぁ'&&c<='ん'||c=='ゔ'||c=='ー'))return Collections.emptyList();
            if("ゃゅょぁぃぅぇぉゎ".indexOf(c)>=0){if(out.isEmpty())return Collections.emptyList();int last=out.size()-1;String previous=out.get(last);if(previous.length()!=1||"っんー".contains(previous))return Collections.emptyList();out.set(last,previous+c);}else out.add(String.valueOf(c));
        }return out;
    }
    static List<Integer> accents(String pitch,int length){
        List<Integer> out=new ArrayList<>();if(pitch.trim().isEmpty()||length==0)return out;
        for(int i=0;i<pitch.length();i++){char c=pitch.charAt(i);if(c==' '||c=='　')continue;int n=Character.getNumericValue(c);if(!(c=='⓪'||c>='①'&&c<='⑳')||n<0||n>length)return Collections.emptyList();if(!out.contains(n))out.add(n);}return out;
    }
    static boolean high(int mora,int accent){return accent==1?mora==0:mora>0&&(accent==0||mora<accent);}
}
