package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;

public class MainActivity extends Activity {
    static final int PICK_FOLDER=1001;
    EditText folder; TextView status, ps2Status, info; Button start, stop, logs;
    BroadcastReceiver receiver;
    volatile boolean stopRequested=false;
    volatile boolean serverRunning=false;
    String latestLog="Сервер остановлен";
    Handler handler=new Handler(Looper.getMainLooper());
    long lastPeerActivity=0;
    String peerIp="";
    final Runnable peerTimeout=new Runnable(){@Override public void run(){if(lastPeerActivity>0 && System.currentTimeMillis()-lastPeerActivity>=15000){ps2Status.setText("PS2: ⚪ нет активности");lastPeerActivity=0;}else if(lastPeerActivity>0)handler.postDelayed(this,1000);}};
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(87,159,211));getWindow().setNavigationBarColor(Color.rgb(76,151,207));build();registerStatus();}
    GradientDrawable glass(int alpha,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(alpha,238,250,255),Color.argb(alpha,112,191,232)});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(150,245,253,255));return g;}
    GradientDrawable fieldBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(30,25,110,180));g.setCornerRadius(dp(4));g.setStroke(dp(1),Color.argb(145,245,253,255));return g;}
    void styleButton(Button b){b.setTextColor(Color.WHITE);b.setTextSize(16);b.setAllCaps(false);b.setBackground(glass(72,7));b.setPadding(dp(8),dp(7),dp(8),dp(7));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(p);}
    TextView text(String s,float size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setShadowLayer(2,0,1,Color.argb(90,0,55,110));return v;}
    void build(){
        FrameLayout frame=new FrameLayout(this);
        GradientDrawable sky=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(103,176,224),Color.rgb(120,199,234),Color.rgb(150,220,239),Color.rgb(91,178,221)});frame.setBackground(sky);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(66),dp(16),dp(28));
        TextView title=text("PS2 UDPFS Server",27);title.setGravity(Gravity.CENTER);title.setLetterSpacing(.04f);title.setPadding(0,dp(8),0,dp(28));root.addView(title);
        folder=new EditText(this);folder.setHint("Папка с играми");folder.setHintTextColor(Color.argb(185,255,255,255));folder.setTextColor(Color.WHITE);folder.setTextSize(18);folder.setSingleLine(true);folder.setBackground(fieldBg());folder.setPadding(dp(12),0,dp(12),0);folder.setText(getPreferences(MODE_PRIVATE).getString("game_folder","/storage/emulated/0/PS2/GAMES"));folder.setInputType(InputType.TYPE_CLASS_TEXT);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(50));fp.setMargins(0,0,0,dp(6));root.addView(folder,fp);
        Button choose=new Button(this);choose.setText("📁  ВЫБРАТЬ ПАПКУ С ИГРАМИ");choose.setOnClickListener(v->pickFolder());styleButton(choose);root.addView(choose);
        Button access=new Button(this);access.setText("ДАТЬ ДОСТУП КО ВСЕМ ФАЙЛАМ");access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}});styleButton(access);root.addView(access);
        Button battery=new Button(this);battery.setText("РАБОТА БЕЗ ОГРАНИЧЕНИЙ БАТАРЕИ");battery.setOnClickListener(v->requestBatteryExemption());styleButton(battery);root.addView(battery);
        status=text("Статус: остановлен",20);status.setPadding(dp(6),dp(10),0,dp(3));root.addView(status);
        ps2Status=text("PS2: ⚪ нет активности",17);ps2Status.setPadding(dp(6),0,0,dp(4));root.addView(ps2Status);
        info=text("",16);info.setPadding(dp(6),0,0,dp(7));root.addView(info);refreshInfo();
        start=new Button(this);start.setText("▶  ЗАПУСТИТЬ СЕРВЕР");start.setOnClickListener(v->startServer());styleButton(start);root.addView(start);
        stop=new Button(this);stop.setText("■  ОСТАНОВИТЬ");stop.setOnClickListener(v->stopServer());styleButton(stop);root.addView(stop);
        logs=new Button(this);logs.setText("ЛОГИ");logs.setOnClickListener(v->showLogs());styleButton(logs);root.addView(logs);
        Button share=new Button(this);share.setText("Поделиться логом");share.setOnClickListener(v->shareLog());styleButton(share);root.addView(share);
        updateButtons();
        Space spacer=new Space(this);root.addView(spacer,new LinearLayout.LayoutParams(1,dp(170)));
        TextView brand=text("PS2  •  UDPFS\nGames  •  Network  •  Always On",13);brand.setGravity(Gravity.RIGHT);brand.setAlpha(.58f);brand.setPadding(0,dp(10),dp(6),dp(20));root.addView(brand);
        sv.addView(root);frame.addView(sv,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
    }
    void pickFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_FOLDER);}
    void requestBatteryExemption(){
        if(Build.VERSION.SDK_INT<23)return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName())){Toast.makeText(this,"Ограничения батареи уже отключены",Toast.LENGTH_LONG).show();return;}
        try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_FOLDER&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}String path=treeUriToPath(uri);if(path!=null){folder.setText(path);getPreferences(MODE_PRIVATE).edit().putString("game_folder",path).apply();refreshInfo();Toast.makeText(this,"Папка выбрана: "+path,Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Не удалось получить обычный путь к этой папке. Выберите папку во внутренней памяти.",Toast.LENGTH_LONG).show();}}
    String treeUriToPath(Uri uri){try{if(!DocumentsContract.isTreeUri(uri))return null;String docId=DocumentsContract.getTreeDocumentId(uri);String[] parts=docId.split(":",2);if(parts.length==0)return null;String volume=parts[0],relative=parts.length>1?parts[1]:"";if("primary".equalsIgnoreCase(volume))return relative.isEmpty()?"/storage/emulated/0":"/storage/emulated/0/"+relative;File storage=new File("/storage/"+volume);return relative.isEmpty()?storage.getAbsolutePath():new File(storage,relative).getAbsolutePath();}catch(Exception e){return null;}}
    void registerStatus(){receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(i.getBooleanExtra("peer_activity",false)){peerIp=i.getStringExtra("peer_ip");lastPeerActivity=System.currentTimeMillis();ps2Status.setText("PS2: 🟢 подключена"+(peerIp==null||peerIp.isEmpty()?"":"  •  "+peerIp));handler.removeCallbacks(peerTimeout);handler.postDelayed(peerTimeout,15000);return;}String t=i.getStringExtra("text");if(t!=null){latestLog=t;if(stopRequested && !t.contains("Сервер остановлен"))return;if(t.contains("Сервер остановлен")){stopRequested=false;serverRunning=false;status.setText("Статус: остановлен");resetPeer();updateButtons();return;}serverRunning=t.contains("ЗАПУЩЕН")||t.contains("работает")||t.contains("уже работает");status.setText(serverRunning?"Статус: работает":t.startsWith("ОШИБКА")||t.contains("ЗАВЕРШИЛСЯ")?"Статус: ошибка":"Статус: запуск...");updateButtons();}}};IntentFilter f=new IntentFilter(ServerService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}
    void resetPeer(){lastPeerActivity=0;peerIp="";handler.removeCallbacks(peerTimeout);if(ps2Status!=null)ps2Status.setText("PS2: ⚪ нет активности");}
    @Override protected void onDestroy(){handler.removeCallbacks(peerTimeout);if(receiver!=null)try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
    void refreshInfo(){info.setText("LAN IP: "+getIp()+"\nUDPFS discovery: 62966\nИгр: "+countGames(folder==null?"/storage/emulated/0/PS2/GAMES":folder.getText().toString()));}
    String getIp(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return "не определён";Network active=cm.getActiveNetwork();String ip=wifiIp(cm,active);if(ip!=null)return ip;for(Network n:cm.getAllNetworks()){ip=wifiIp(cm,n);if(ip!=null)return ip;}return "не определён";}catch(Exception e){return "не определён";}}
    String wifiIp(ConnectivityManager cm,Network n){if(n==null)return null;NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return null;LinkProperties lp=cm.getLinkProperties(n);if(lp==null)return null;for(LinkAddress la:lp.getLinkAddresses()){InetAddress a=la.getAddress();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress())return a.getHostAddress();}return null;}
    int countGames(String p){File d=new File(p);File[] f=d.listFiles();if(f==null)return 0;int n=0;for(File x:f)if(x.isFile()&&(x.getName().toLowerCase().endsWith(".iso")||x.getName().toLowerCase().endsWith(".cso")||x.getName().toLowerCase().endsWith(".zso")))n++;return n;}
    void startServer(){stopRequested=false;resetPeer();String p=folder.getText().toString().trim();if(!p.startsWith("/")){Toast.makeText(this,"Сначала выберите папку кнопкой 📁",Toast.LENGTH_LONG).show();return;}getPreferences(MODE_PRIVATE).edit().putString("game_folder",p).apply();refreshInfo();latestLog="Запускаю...";Intent i=new Intent(this,ServerService.class);i.setAction(ServerService.ACTION_START);i.putExtra("root",p);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Статус: запуск...");start.setEnabled(false);stop.setEnabled(true);}
    void stopServer(){if(stopRequested||!serverRunning)return;stopRequested=true;resetPeer();status.setText("Статус: остановка...");latestLog="Останавливаю сервер...";updateButtons();Intent i=new Intent(this,ServerService.class);i.setAction(ServerService.ACTION_STOP);startService(i);}
    void updateButtons(){if(start==null||stop==null)return;start.setEnabled(!serverRunning&&!stopRequested);stop.setEnabled(serverRunning&&!stopRequested);}
    void shareLog() {
        Toast.makeText(this,"Подготовка файла лога...",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File file=LogStore.snapshot(getApplicationContext());
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed()) return;
                    try {
                        Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".logs",file);
                        Intent share=new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_STREAM,uri);
                        share.setClipData(ClipData.newRawUri("Лог UDPFS",uri));
                        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(share,"Поделиться логом"));
                    } catch(Exception e) { Toast.makeText(this,"Не удалось отправить лог: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
                });
            } catch(Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Не удалось сохранить лог: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"udpfs-share-log").start();
    }
    void showLogs(){TextView content=text(latestLog,15);content.setTextIsSelectable(true);content.setTextColor(Color.rgb(30,55,75));content.setShadowLayer(0,0,0,Color.TRANSPARENT);content.setPadding(dp(18),dp(14),dp(18),dp(14));ScrollView scroll=new ScrollView(this);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Логи udpfsd").setView(scroll).setPositiveButton("Закрыть",null).show();}
}
