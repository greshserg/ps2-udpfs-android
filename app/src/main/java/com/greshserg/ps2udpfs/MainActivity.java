package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.content.IntentFilter;
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

    @Override public void onCreate(Bundle b){super.onCreate(b); build(); registerStatus();}

    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        TextView title=new TextView(this); title.setText("PS2 UDPFS Server — Diagnostics"); title.setTextSize(24); root.addView(title);
        folder=new EditText(this); folder.setHint("Папка с играми"); folder.setText(getPreferences(MODE_PRIVATE).getString("game_folder","/storage/emulated/0/PS2/GAMES")); folder.setInputType(InputType.TYPE_CLASS_TEXT); root.addView(folder);

        Button choose=new Button(this); choose.setText("📁 Выбрать папку с играми"); choose.setOnClickListener(v->pickFolder()); root.addView(choose);
        Button access=new Button(this); access.setText("Дать доступ ко всем файлам"); access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}}); root.addView(access);

        status=new TextView(this); status.setText("Статус: остановлен"); status.setTextSize(18); root.addView(status);
        info=new TextView(this); root.addView(info); refreshInfo();
        start=new Button(this); start.setText("▶ Запустить сервер"); start.setOnClickListener(v->startServer()); root.addView(start);
        stop=new Button(this); stop.setText("■ Остановить"); stop.setOnClickListener(v->stopServer()); root.addView(stop);
        check=new Button(this); check.setText("Проверить сеть"); check.setOnClickListener(v->checkNetwork()); root.addView(check);
        TextView lt=new TextView(this); lt.setText("Диагностика udpfsd:"); lt.setTextSize(18); root.addView(lt);
        log=new TextView(this); log.setText("Нажмите «Запустить сервер»"); log.setTextIsSelectable(true); log.setPadding(12,12,12,12); root.addView(log);
        ScrollView sv=new ScrollView(this); sv.addView(root); setContentView(sv);
    }

    void pickFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,PICK_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_FOLDER && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}
            String path=treeUriToPath(uri);
            if(path!=null){
                folder.setText(path);
                getPreferences(MODE_PRIVATE).edit().putString("game_folder",path).apply();
                refreshInfo();
                Toast.makeText(this,"Папка выбрана: "+path,Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"Не удалось получить обычный путь к этой папке. Выберите папку во внутренней памяти.",Toast.LENGTH_LONG).show();
            }
        }
    }

    String treeUriToPath(Uri uri){
        try{
            if(!DocumentsContract.isTreeUri(uri))return null;
            String docId=DocumentsContract.getTreeDocumentId(uri);
            String[] parts=docId.split(":",2);
            if(parts.length==0)return null;
            String volume=parts[0];
            String relative=parts.length>1?parts[1]:"";
            if("primary".equalsIgnoreCase(volume))return relative.isEmpty()?"/storage/emulated/0":"/storage/emulated/0/"+relative;
            File storage=new File("/storage/"+volume);
            return relative.isEmpty()?storage.getAbsolutePath():new File(storage,relative).getAbsolutePath();
        }catch(Exception e){return null;}
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
    void startServer(){String p=folder.getText().toString().trim(); if(!p.startsWith("/")){Toast.makeText(this,"Сначала выберите папку кнопкой 📁",Toast.LENGTH_LONG).show();return;} getPreferences(MODE_PRIVATE).edit().putString("game_folder",p).apply(); refreshInfo(); log.setText("Запускаю..."); Intent i=new Intent(this,ServerService.class); i.putExtra("root",p); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i); status.setText("Статус: запуск...");}
    void stopServer(){stopService(new Intent(this,ServerService.class));status.setText("Статус: остановлен");log.setText("Сервер остановлен");}
    void checkNetwork(){String ip=getIp(); Toast.makeText(this,ip.equals("не определён")?"Wi-Fi/LAN IP пока не определён":"Сеть OK. IP сервера: "+ip,Toast.LENGTH_LONG).show();}
}
