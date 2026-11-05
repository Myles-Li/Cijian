package com.myles.cijian;
import java.util.Locale;
/** 无明确选择时使用系统语言，不匹配时回退英文。 / Use the system language until explicitly selected; unsupported languages fall back to English. */
final class UiLanguage {
    static final String[] IDS={"ZH","EN","JA"};
    static final String[] NAMES={"简体中文 / Chinese / 中国語","English / 英语 / 英語","日本語 / 日语 / Japanese"};
    static String resolve(String selected,Locale system){
        for(String id:IDS)if(id.equals(selected))return id;
        String code=system==null?"":system.getLanguage();
        return code.equals("zh")?"ZH":code.equals("ja")?"JA":"EN";
    }
    private UiLanguage(){}
}
