package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;

public class ServerService extends Service {
    public static final String ACTION_STATUS="com.greshserg.ps2udpfs.STATUS";
    java.lang.Process process;
    PowerManager.WakeLock wake;

    @Override public void onCreate(){
        super.onCreate();
        String ch="ps2_udpfs";
        if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(ch,"PS2 UDPFS",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);
        n.setContentTitle("PS2 UDPFS Server").setContentText("Запуск udpfsd...").setSmallIcon(android.R.drawable.stat_sys_upload);
        startForeground(1001,n.build());
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PS2Udpfs:Server");
        wake.acquire();
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        String root=intent==null?null:intent.getStringExtra("root");
        new Thread(()->runServer(root)).start();
        return START_STICKY;
    }

    void sendStatus(String text){
        Intent i=new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("text",text);
        sendBroadcast(i);
    }

    void runServer(String root){
        try{
            sendStatus("Подготовка udpfsd...");
            File exe=new File(getFilesDir(),"udpfsd");
            if(!exe.exists() || exe.length()==0){
                try(InputStream in=getAssets().open("udpfsd"); FileOutputStream out=new FileOutputStream(exe)){
                    byte[] b=new byte[8192]; int n;
                    while((n=in.read(b))>0)out.write(b,0,n);
                }
            }
            boolean chmod=exe.setExecutable(true,false);
            sendStatus("udpfsd найден: "+exe.length()+" байт\nExecutable: "+exe.canExecute()+" (chmod="+chmod+")\nПуть: "+exe.getAbsolutePath());
            if(root==null || root.trim().isEmpty()) throw new IOException("Папка игр не указана");
            File gameRoot=new File(root);
            if(!gameRoot.exists()) throw new IOException("Папка не существует: "+root);
            if(!gameRoot.isDirectory()) throw new IOException("Это не папка: "+root);

            ProcessBuilder pb=new ProcessBuilder(exe.getAbsolutePath(),"-fsroot",root,"-ro","-verbose");
            pb.redirectErrorStream(true);
            process=pb.start();
            long pid=-1;
            if(Build.VERSION.SDK_INT>=26) try{pid=process.pid();}catch(Exception ignored){}
            sendStatus("udpfsd ЗАПУЩЕН\nPID: "+pid+"\nRoot: "+root+"\nDiscovery UDP: 62966");

            BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line; StringBuilder tail=new StringBuilder();
            while((line=r.readLine())!=null){
                tail.append(line).append('\n');
                if(tail.length()>1800)tail.delete(0,tail.length()-1800);
                sendStatus("udpfsd работает\nPID: "+pid+"\n--- log ---\n"+tail.toString());
            }
            int code=process.waitFor();
            sendStatus("udpfsd ЗАВЕРШИЛСЯ\nКод: "+code+"\n--- log ---\n"+tail.toString());
        }catch(Throwable e){
            StringWriter sw=new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sendStatus("ОШИБКА ЗАПУСКА udpfsd\n"+sw.toString());
        }
    }

    @Override public void onDestroy(){
        if(process!=null)process.destroy();
        if(wake!=null&&wake.isHeld())wake.release();
        sendStatus("Сервер остановлен");
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
