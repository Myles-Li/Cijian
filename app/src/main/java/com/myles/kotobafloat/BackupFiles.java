package com.myles.kotobafloat;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BackupFiles {
    static File create(Context c,String json)throws IOException{
        File dir=new File(c.getFilesDir(),"shared-backups");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建备份目录");
        File f=new File(dir,"kotoba-backup-"+java.time.LocalDate.now()+"-"+UUID.randomUUID().toString().substring(0,8)+".json");
        try(FileOutputStream out=new FileOutputStream(f)){out.write(json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}return f;
    }
    static Intent share(Context c,File f){
        Uri uri=new Uri.Builder().scheme("content").authority(c.getPackageName()+".backups").appendPath(f.getName()).build();
        Intent intent=new Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TITLE,"词间完整备份").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newUri(c.getContentResolver(),"词间完整备份",uri));return intent;
    }
    static Uri downloads(Context c,String json)throws IOException{
        if(Build.VERSION.SDK_INT<29)throw new IOException("请使用系统文件选择器保存");
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"kotoba-backup-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".json");values.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/Kotoba");values.put(MediaStore.MediaColumns.IS_PENDING,1);
        ContentResolver resolver=c.getContentResolver();Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法保存到下载");
        try{try(OutputStream out=resolver.openOutputStream(uri)){if(out==null)throw new IOException();out.write(json.getBytes(StandardCharsets.UTF_8));}ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);resolver.update(uri,ready,null,null);return uri;}
        catch(IOException|RuntimeException failed){resolver.delete(uri,null,null);throw failed;}
    }
}
