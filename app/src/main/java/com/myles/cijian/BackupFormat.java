package com.myles.cijian;

/** 新导出使用Cijian标识，导入继续识别原词间备份。 / Export Cijian backups; retain legacy import support. */
final class BackupFormat {
    static final String CURRENT="cijian-progress-v2";
    static boolean supported(String format){return CURRENT.equals(format)||"kotoba-float-progress-v1".equals(format)||"kotoba-float-progress-v2".equals(format);}
}
