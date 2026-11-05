package com.myles.cijian;

import android.content.*;
import android.database.*;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;

/** 仅提供测试应用内的样本文件，不提供正式学习记录。 / Exposes only test fixture files in the TEST app, never production records. */
public final class PreviewDocumentProvider extends DocumentsProvider {
    private static final String[] ROOT={DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_MIME_TYPES};
    private static final String[] DOC={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){MatrixCursor cursor=new MatrixCursor(projection==null?ROOT:projection);MatrixCursor.RowBuilder row=cursor.newRow();row.add(DocumentsContract.Root.COLUMN_ROOT_ID,"preview-test").add(DocumentsContract.Root.COLUMN_DOCUMENT_ID,"root").add(DocumentsContract.Root.COLUMN_TITLE,"Cijian TEST fixtures").add(DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.FLAG_LOCAL_ONLY).add(DocumentsContract.Root.COLUMN_MIME_TYPES,"*/*");return cursor;}
    private void row(MatrixCursor cursor,String id)throws FileNotFoundException{File file=resolve(id);MatrixCursor.RowBuilder row=cursor.newRow();row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID,id).add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,id.equals("root")?"Cijian TEST fixtures":id).add(DocumentsContract.Document.COLUMN_MIME_TYPE,id.equals("root")?DocumentsContract.Document.MIME_TYPE_DIR:"application/json").add(DocumentsContract.Document.COLUMN_SIZE,id.equals("root")?0:file.length()).add(DocumentsContract.Document.COLUMN_FLAGS,0);}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor cursor=new MatrixCursor(projection==null?DOC:projection);row(cursor,id);return cursor;}
    @Override public Cursor queryChildDocuments(String id,String[] projection,String sort)throws FileNotFoundException{MatrixCursor cursor=new MatrixCursor(projection==null?DOC:projection);for(String name:new String[]{"valid-preview.json","legacy-preview.json","invalid-preview.json"})if(resolve(name).exists())row(cursor,name);return cursor;}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{if(!mode.equals("r")||id.equals("root"))throw new FileNotFoundException();return ParcelFileDescriptor.open(resolve(id),ParcelFileDescriptor.MODE_READ_ONLY);}
    private File resolve(String id)throws FileNotFoundException{if(!id.equals("root")&&!id.equals("valid-preview.json")&&!id.equals("legacy-preview.json")&&!id.equals("invalid-preview.json"))throw new FileNotFoundException();return new File(getContext().getFilesDir(),id);}
}
