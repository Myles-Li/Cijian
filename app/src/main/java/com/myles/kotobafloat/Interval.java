package com.myles.kotobafloat;

import java.math.BigDecimal;

final class Interval {
    static long parseMillis(String input){
        if(input==null)throw new IllegalArgumentException("请输入切词间隔");
        String value=input.trim().replace(',','.');
        if(value.length()>16||!value.matches("(?:\\d+(?:\\.\\d{1,3})?|\\.\\d{1,3})"))throw new IllegalArgumentException("请输入秒数，最多三位小数，例如 1.25");
        try{
            long millis=new BigDecimal(value).movePointRight(3).longValueExact();
            if(millis<100||millis>3600000)throw new IllegalArgumentException("请输入 0.1～3600 秒");
            return millis;
        }catch(ArithmeticException invalid){throw new IllegalArgumentException("请输入 0.1～3600 秒");}
    }
    static String format(long millis){return BigDecimal.valueOf(millis,3).stripTrailingZeros().toPlainString();}
    private Interval(){}
}
