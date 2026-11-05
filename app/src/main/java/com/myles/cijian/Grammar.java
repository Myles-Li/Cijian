package com.myles.cijian;

final class Grammar {
    static String describe(String pos) {
        if(pos==null||pos.isEmpty())return "原词库未标注";
        return pos.replace("自他動1","自他动词／五段").replace("自他動2","自他动词／一段").replace("自他動3","自他动词／不规则")
            .replace("自動1","自动词／五段").replace("他動1","他动词／五段")
            .replace("自動2","自动词／一段").replace("他動2","他动词／一段")
            .replace("自動3","自动词／不规则").replace("他動3","他动词／不规则")
            .replace("イ形","い形容词").replace("ナ形","な形容词").replace("形動","形容动词").replace("副","副词").replace("連体","连体词")
            .replace("接続","接续词").replace("感動","感叹词").replace("名","名词");
    }
    private Grammar() {}
}
