package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.net.*;

public class MainActivity extends Activity {
    EditText folder; TextView status, info, log; Button start, stop, check;
    BroadcastReceiver receiver;

    @Override public void onCreate(Bundle b){super.onCreate(b); build(); registerStatus();}

    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        TextView title=new TextView(this); title.setText("PS2 UDPFS Server — Diagnostics"); title.setTextSize(24); root.addView(title);
        folder=new EditText(this); folder.setHint("Папка с играми"); folder.setText("/storage/emulated/0/PS2/GAMES"); folder.setInputType(InputType.TYPE_CLASS_TEXT); root.addView(folder);
        Button access=new Button(this); access.setText("Дать доступ к файлам"); access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}}); root.addView(access);
        status=new TextView(this); status.setText("Статус: остановлен"); status.setTextSize(18); root.addView(status);
        info=new TextView(this); root.addView(info); refreshInfo();
        start=new Button(this); start.setText("▶ Запустить сервер"); start.setOnClickListener(v->startServer()); root.addView(start);
        stop=new Button(this); stop.setText("■ Остановить"); stop.setOnClickListener(v->stopServer()); root.addView(stop);
        check=new Button(this); check.setText("Проверить сеть"); check.setOnClickListener(v->checkNetwork()); root.addView(check);
        TextView lt=new TextView(this); lt.setText("Диагностика udpfsd:"); lt.setTextSize(18); root.addView(lt);
        log=new TextView(this); log.setText("Нажмите «Запустить сервер»"); log.setTextIsSelectable(true); log.setPadding(12,12,12,12); root.addView(log);
        ScrollView sv=new ScrollView(this); sv.addView(root); setContentView(sv);
    }

    void registerStatus(){
        receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String t=i.getStringExtra("text"); if(t!=null){log.setText(t); status.setText(t.contains("ЗАПУЩЕН")||t.contains("работает")?"Статус: работает":t.startsWith("ОШИБКА")||t.contains("ЗАВЕРШИЛСЯ")?"Статус: ошибка":"Статус: запуск...");}}};
        IntentFilter f=new IntentFilter(ServerService.ACTION_STATUS);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f);
    }

    @Override protected void onDestroy(){if(receiver!=null)try{unregisterReceiver(receiver);}catch(Exception ignored){} super.onDestroy();}
    void refreshInfo(){info.setText("LAN IP: "+getIp()+"\nUDPFS discovery: 62966\nИгр: "+countGames(folder==null?"/storage/emulated/0/PS2/GAMES":folder.getText().toString()));}
    String getIp(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE); int x=w.getConnectionInfo().getIpAddress(); if(x==0)return "не определён"; return ((x)&255)+"."+((x>>8)&255)+"."+((x>>16)&255)+"."+((x>>24)&255);}catch(Exception e){return "не определён";}}
    int countGames(String p){File d=new File(p); File[] f=d.listFiles(); if(f==null)return 0; int n=0; for(File x:f)if(x.isFile()&&(x.getName().toLowerCase().endsWith(".iso")||x.getName().toLowerCase().endsWith(".cso")||x.getName().toLowerCase().endsWith(".zso")))n++; return n;}
    void startServer(){refreshInfo(); log.setText("Запускаю..."); Intent i=new Intent(this,ServerService.class); i.putExtra("root",folder.getText().toString()); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i); status.setText("Статус: запуск...");}
    void stopServer(){stopService(new Intent(this,ServerService.class));status.setText("Статус: остановлен");log.setText("Сервер остановлен");}
    void checkNetwork(){String ip=getIp(); Toast.makeText(this,ip.equals("не определён")?"Wi-Fi/LAN IP пока не определён":"Сеть OK. IP сервера: "+ip,Toast.LENGTH_LONG).show();}
}
