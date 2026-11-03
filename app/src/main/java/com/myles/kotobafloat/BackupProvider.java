package com.myles.kotobafloat;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

public final class BackupProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri)throws FileNotFoundException{
        if(!"content".equals(uri.getScheme())||!uri.getAuthority().equals(getContext().getPackageName()+".backups")||uri.getPathSegments().size()!=1)throw new FileNotFoundException();
        String name=uri.getLastPathSegment();if(name==null||!name.matches("kotoba-backup-[0-9A-Za-z_-]+\\.json"))throw new FileNotFoundException();
        try{File dir=new File(getContext().getFilesDir(),"shared-backups").getCanonicalFile(),file=new File(dir,name).getCanonicalFile();if(!dir.equals(file.getParentFile())||!file.isFile())throw new FileNotFoundException();return file;}catch(IOException e){throw new FileNotFoundException();}
    }
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try{File f=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor out=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;out.addRow(row);return out;}catch(FileNotFoundException e){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("只读备份");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
