package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText folder; TextView status, info; Button start, stop, check;
    static final int REQ=10;
    @Override public void onCreate(Bundle b){super.onCreate(b); build();}
    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        TextView title=new TextView(this); title.setText("PS2 UDPFS Server"); title.setTextSize(26); root.addView(title);
        folder=new EditText(this); folder.setHint("Папка с играми"); folder.setText("/storage/emulated/0/PS2/GAMES"); folder.setInputType(InputType.TYPE_CLASS_TEXT); root.addView(folder);
        Button access=new Button(this); access.setText("Дать доступ к файлам"); access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}}); root.addView(access);
        status=new TextView(this); status.setText("Статус: остановлен"); status.setTextSize(18); root.addView(status);
        info=new TextView(this); root.addView(info); refreshInfo();
        start=new Button(this); start.setText("▶ Запустить сервер"); start.setOnClickListener(v->startServer()); root.addView(start);
        stop=new Button(this); stop.setText("■ Остановить"); stop.setOnClickListener(v->stopServer()); root.addView(stop);
        check=new Button(this); check.setText("Проверить PS2"); check.setOnClickListener(v->checkNetwork()); root.addView(check);
        setContentView(root);
    }
    void refreshInfo(){info.setText("LAN IP: "+getIp()+"\nUDPFS discovery: 62966\nИгр: "+countGames(folder==null?"/storage/emulated/0/PS2/GAMES":folder.getText().toString()));}
    String getIp(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE); int x=w.getConnectionInfo().getIpAddress(); return ((x)&255)+"."+((x>>8)&255)+"."+((x>>16)&255)+"."+((x>>24)&255);}catch(Exception e){return "не определён";}}
    int countGames(String p){File d=new File(p); File[] f=d.listFiles(); if(f==null)return 0; int n=0; for(File x:f)if(x.isFile()&&(x.getName().toLowerCase().endsWith(".iso")||x.getName().toLowerCase().endsWith(".cso")||x.getName().toLowerCase().endsWith(".zso")||x.getName().toLowerCase().endsWith(".chd")))n++; return n;}
    void startServer(){refreshInfo(); Intent i=new Intent(this,ServerService.class); i.putExtra("root",folder.getText().toString()); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i); status.setText("Статус: запуск...");}
    void stopServer(){stopService(new Intent(this,ServerService.class));status.setText("Статус: остановлен");}
    void checkNetwork(){new Thread(()->{String msg="UDP 62966 доступен локально"; try(DatagramSocket s=new DatagramSocket()){s.setSoTimeout(1000);byte[] b=new byte[1]; DatagramPacket p=new DatagramPacket(b,1,InetAddress.getByName(getIp()),62966); /* only verifies socket can bind */ msg="Сеть OK. IP сервера: "+getIp();}catch(Exception e){msg="Ошибка сети: "+e.getMessage();} final String m=msg;runOnUiThread(()->Toast.makeText(this,m,Toast.LENGTH_LONG).show());}).start();}
}
