package com.myles.cijian;

final class Accent {
    static final String[] IDS={"GREEN","BLUE","TEAL","PURPLE","ORANGE","ROSE","BLACK","GRAY","INDIGO","AMBER","RED","YELLOW","CUSTOM"};
    static final String[] LABELS={"苔绿","晴蓝","青碧","藤紫","暖橙","玫粉","黑白 · 默认","石墨灰","靛蓝","琥珀金","朱红","柠黄","自定义色号…"};
    // 依次为浅色强调色、深色强调色、浅色背景、深色背景、深色表面及浅深柔和底色。 / Light accent, dark accent, light background, dark background, dark surface, light soft, dark soft.
    static final String[][] PALETTES={
        {"32715F","99D5B8","F7F8F4","111A16","1C2A22","E7EFE6","263D31"},
        {"3066A3","9AC4F2","F5F7FB","111923","1B2839","E5EDF8","253E5A"},
        {"246F73","88D0CE","F4F9F8","0E1B1B","1A2D2E","E0F0ED","234345"},
        {"725294","CBB1EA","F8F6FA","1A1522","2B2237","EEE5F6","423252"},
        {"985518","EDBD85","FBF7F2","211910","33271B","F7E9D9","4E3925"},
        {"A04368","EBB0C7","FBF6F7","211519","34232A","F7E3EB","52313F"},
        {"202124","D4D7DB","F6F6F6","101010","242424","E8E8E8","343434"},
        {"586471","C0CAD5","F6F7F8","15191E","252D36","E8EDF1","36424F"},
        {"4C56A2","B6BEFA","F6F6FC","151727","242843","E7E9FA","343C60"},
        {"83611A","E5CC85","FBF9F1","1F1B0E","322D1A","F5EDD5","4B4125"},
        {"B43A3D","FFABA8","FFF6F5","250F11","3A2023","FCE3E1","553033"},
        {"806400","E8D771","FCFAF0","201D0D","322E18","FFF3BB","4C4526"}
    };
    static int parse(String text){String s=text.trim();if(s.startsWith("#"))s=s.substring(1);if(!s.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("请输入六位色号，例如 #2F80ED");return 0xff000000|Integer.parseInt(s,16);}
    static String hex(int color){return String.format(java.util.Locale.ROOT,"#%06X",color&0xffffff);}
    static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 0;}
    static int mix(int a,int b,double amount){int r=(int)(((a>>16)&255)*(1-amount)+((b>>16)&255)*amount),g=(int)(((a>>8)&255)*(1-amount)+((b>>8)&255)*amount),bl=(int)((a&255)*(1-amount)+(b&255)*amount);return 0xff000000|(r<<16)|(g<<8)|bl;}
    static double luminance(int c){double[] v={(c>>16)&255,(c>>8)&255,c&255};for(int i=0;i<3;i++){v[i]/=255;v[i]=v[i]<=.04045?v[i]/12.92:Math.pow((v[i]+.055)/1.055,2.4);}return .2126*v[0]+.7152*v[1]+.0722*v[2];}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static int readable(int color,boolean dark){int text=dark?0xff15202b:0xffffffff;for(int i=0;i<=40;i++){int c=mix(color,dark?0xffffffff:0xff000000,i/40.0);if(contrast(c,text)>=4.5)return c;}return dark?0xffffffff:0xff000000;}
    static int nearest(String id,String custom){int n=index(id);if(n<PALETTES.length)return n;int c;try{c=parse(custom);}catch(IllegalArgumentException bad){return 0;}int best=0;long distance=Long.MAX_VALUE;for(int i=0;i<PALETTES.length;i++){int p=parse(PALETTES[i][0]);long d=0;for(int shift:new int[]{0,8,16}){long v=((c>>shift)&255)-((p>>shift)&255);d+=v*v;}if(d<distance){best=i;distance=d;}}return best;}
    static int[] palette(String id,String custom,boolean dark){
        int i=index(id);if(i<PALETTES.length){String[] p=PALETTES[i];return new int[]{parse(p[dark?1:0]),parse(p[dark?3:2]),dark?parse(p[4]):0xffffffff,parse(p[dark?6:5])};}
        int color;try{color=parse(custom);}catch(IllegalArgumentException bad){color=parse("32715F");}
        int pale=mix(color,dark?0xff000000:0xffffffff,dark?.78:.90),accent=readable(color,dark);
        for(int step=0;step<=40;step++){int candidate=mix(accent,dark?0xffffffff:0xff000000,step/40.0);if(contrast(candidate,pale)>=4.5&&contrast(candidate,dark?0xff15202b:0xffffffff)>=4.5){accent=candidate;break;}}
        return new int[]{accent,mix(color,dark?0xff000000:0xffffffff,dark?.94:.97),dark?mix(color,0xff000000,.86):0xffffffff,pale};
    }
}
