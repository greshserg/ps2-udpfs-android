package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    static final int PICK_FOLDER=1001;
    EditText folder; TextView status, info, log; Button start, stop, check;
    BroadcastReceiver receiver;
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(Color.rgb(8,67,151)); getWindow().setNavigationBarColor(Color.rgb(5,64,139)); build(); registerStatus();}

    GradientDrawable panel(int alpha,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(alpha,180,230,255),Color.argb(alpha,28,116,205)});
        g.setCornerRadius(dp(radius)); g.setStroke(dp(1),Color.argb(190,220,245,255)); return g;
    }
    GradientDrawable fieldBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(75,0,55,130));g.setCornerRadius(dp(8));g.setStroke(dp(1),Color.argb(180,210,240,255));return g;}
    void styleButton(Button b){b.setTextColor(Color.WHITE);b.setTextSize(16);b.setAllCaps(false);b.setBackground(panel(145,8));b.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(6),0,dp(6));b.setLayoutParams(p);}
    TextView text(String s,float size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setShadowLayer(3,0,1,Color.argb(130,0,45,100));return v;}

    void build(){
        FrameLayout frame=new FrameLayout(this);
        GradientDrawable sky=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(7,67,158),Color.rgb(18,133,219),Color.rgb(75,196,232),Color.rgb(8,101,177)}); frame.setBackground(sky);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        // Safe area for camera cutouts/status bar + comfortable spacing.
        root.setPadding(dp(16),dp(28),dp(16),dp(28));

        TextView title=text("PS2 UDPFS Server — Diagnostics",25);title.setPadding(0,dp(10),0,dp(14));root.addView(title);
        folder=new EditText(this);folder.setHint("Папка с играми");folder.setHintTextColor(Color.argb(180,255,255,255));folder.setTextColor(Color.WHITE);folder.setTextSize(18);folder.setSingleLine(true);folder.setBackground(fieldBg());folder.setPadding(dp(12),0,dp(12),0);folder.setText(getPreferences(MODE_PRIVATE).getString("game_folder","/storage/emulated/0/PS2/GAMES"));folder.setInputType(InputType.TYPE_CLASS_TEXT);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.setMargins(0,0,0,dp(7));root.addView(folder,fp);

        Button choose=new Button(this);choose.setText("📁  ВЫБРАТЬ ПАПКУ С ИГРАМИ");choose.setOnClickListener(v->pickFolder());styleButton(choose);root.addView(choose);
        Button access=new Button(this);access.setText("ДАТЬ ДОСТУП КО ВСЕМ ФАЙЛАМ");access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}});styleButton(access);root.addView(access);

        status=text("Статус: остановлен",20);status.setPadding(0,dp(12),0,dp(4));root.addView(status);
        info=text("",16);info.setPadding(0,0,0,dp(8));root.addView(info);refreshInfo();
        start=new Button(this);start.setText("▶  ЗАПУСТИТЬ СЕРВЕР");start.setOnClickListener(v->startServer());styleButton(start);root.addView(start);
        stop=new Button(this);stop.setText("■  ОСТАНОВИТЬ");stop.setOnClickListener(v->stopServer());styleButton(stop);root.addView(stop);
        check=new Button(this);check.setText("ПРОВЕРИТЬ СЕТЬ");check.setOnClickListener(v->checkNetwork());styleButton(check);root.addView(check);

        TextView lt=text("Диагностика udpfsd:",20);lt.setPadding(0,dp(12),0,dp(5));root.addView(lt);
        log=text("Нажмите «Запустить сервер»",15);log.setTextIsSelectable(true);log.setPadding(dp(10),dp(10),dp(10),dp(10));log.setBackground(panel(65,8));root.addView(log,new LinearLayout.LayoutParams(-1,-2));

        Space spacer=new Space(this);root.addView(spacer,new LinearLayout.LayoutParams(1,dp(240)));
        TextView brand=text("PS2  •  UDPFS\nGAMES  •  NETWORK  •  ALWAYS ON",13);brand.setGravity(Gravity.RIGHT);brand.setAlpha(.72f);brand.setPadding(0,dp(12),dp(5),dp(20));root.addView(brand);
        sv.addView(root);frame.addView(sv,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
    }

    void pickFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_FOLDER);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_FOLDER&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}String path=treeUriToPath(uri);if(path!=null){folder.setText(path);getPreferences(MODE_PRIVATE).edit().putString("game_folder",path).apply();refreshInfo();Toast.makeText(this,"Папка выбрана: "+path,Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Не удалось получить обычный путь к этой папке. Выберите папку во внутренней памяти.",Toast.LENGTH_LONG).show();}}
    String treeUriToPath(Uri uri){try{if(!DocumentsContract.isTreeUri(uri))return null;String docId=DocumentsContract.getTreeDocumentId(uri);String[] parts=docId.split(":",2);if(parts.length==0)return null;String volume=parts[0],relative=parts.length>1?parts[1]:"";if("primary".equalsIgnoreCase(volume))return relative.isEmpty()?"/storage/emulated/0":"/storage/emulated/0/"+relative;File storage=new File("/storage/"+volume);return relative.isEmpty()?storage.getAbsolutePath():new File(storage,relative).getAbsolutePath();}catch(Exception e){return null;}}
    void registerStatus(){receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String t=i.getStringExtra("text");if(t!=null){log.setText(t);status.setText(t.contains("ЗАПУЩЕН")||t.contains("работает")?"Статус: работает":t.startsWith("ОШИБКА")||t.contains("ЗАВЕРШИЛСЯ")?"Статус: ошибка":"Статус: запуск...");}}};IntentFilter f=new IntentFilter(ServerService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}
    @Override protected void onDestroy(){if(receiver!=null)try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
    void refreshInfo(){info.setText("LAN IP: "+getIp()+"\nUDPFS discovery: 62966\nИгр: "+countGames(folder==null?"/storage/emulated/0/PS2/GAMES":folder.getText().toString()));}
    String getIp(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);int x=w.getConnectionInfo().getIpAddress();if(x==0)return "не определён";return (x&255)+"."+((x>>8)&255)+"."+((x>>16)&255)+"."+((x>>24)&255);}catch(Exception e){return "не определён";}}
    int countGames(String p){File d=new File(p);File[] f=d.listFiles();if(f==null)return 0;int n=0;for(File x:f)if(x.isFile()&&(x.getName().toLowerCase().endsWith(".iso")||x.getName().toLowerCase().endsWith(".cso")||x.getName().toLowerCase().endsWith(".zso")))n++;return n;}
    void startServer(){String p=folder.getText().toString().trim();if(!p.startsWith("/")){Toast.makeText(this,"Сначала выберите папку кнопкой 📁",Toast.LENGTH_LONG).show();return;}getPreferences(MODE_PRIVATE).edit().putString("game_folder",p).apply();refreshInfo();log.setText("Запускаю...");Intent i=new Intent(this,ServerService.class);i.putExtra("root",p);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Статус: запуск...");}
    void stopServer(){stopService(new Intent(this,ServerService.class));status.setText("Статус: остановлен");log.setText("Сервер остановлен");}
    void checkNetwork(){String ip=getIp();Toast.makeText(this,ip.equals("не определён")?"Wi-Fi/LAN IP пока не определён":"Сеть OK. IP сервера: "+ip,Toast.LENGTH_LONG).show();}
}
