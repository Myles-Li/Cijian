package com.myles.cijian;

final class BackupReminder {
    static String message(long now,long saved,long generated,boolean hasRecords){
        if(!hasRecords)return "备份可以把学习记录转存到电脑或其他位置。";
        if(generated>saved)return "最近生成过分享备份，请确认已在接收应用中发送，并存到手机之外。生成文件不代表发送成功。";
        if(saved<=0)return "学习记录保存在本机，方便时可以备份一份。卸载会清除本机记录。";
        long days=Math.max(0,(now-saved)/86400000L);
        return days>=14?"距上次保存备份已过 "+days+" 天，方便时可以再备份一份。":"已有保存记录；也可以把备份再传到电脑或云盘。";
    }
}
