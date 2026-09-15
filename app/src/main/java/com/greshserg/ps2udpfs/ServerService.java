package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.regex.*;

public class ServerService extends Service {
    public static final String ACTION_STATUS="com.greshserg.ps2udpfs.STATUS";
    volatile java.lang.Process process;
    volatile boolean stopping=false;
    PowerManager.WakeLock wake;
    static final Pattern PEER=Pattern.compile("\\[([0-9]{1,3}(?:\\.[0-9]{1,3}){3})(?::[0-9]+)?\\]");

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
        stopping=false;
        String root=intent==null?null:intent.getStringExtra("root");
        new Thread(()->runServer(root)).start();
        return START_NOT_STICKY;
    }

    void sendStatus(String text){
        Intent i=new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("text",text);
        sendBroadcast(i);
    }

    void sendPeerActivity(String line){
        Matcher m=PEER.matcher(line);
        if(!m.find())return;
        Intent i=new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("peer_activity",true);
        i.putExtra("peer_ip",m.group(1));
        sendBroadcast(i);
    }

    void runServer(String root){
        BufferedReader reader=null;
        try{
            sendStatus("Подготовка udpfsd...");
            File exe=new File(getApplicationInfo().nativeLibraryDir,"libudpfsd.so");
            sendStatus("udpfsd native: "+exe.getAbsolutePath()+"\nExists: "+exe.exists()+"\nSize: "+exe.length()+" байт\nExecutable: "+exe.canExecute());
            if(!exe.exists()) throw new IOException("libudpfsd.so не найден в nativeLibraryDir: "+exe.getAbsolutePath());
            if(!exe.canExecute()) throw new IOException("libudpfsd.so не исполняемый: "+exe.getAbsolutePath());
            if(root==null || root.trim().isEmpty()) throw new IOException("Папка игр не указана");
            File gameRoot=new File(root);
            if(!gameRoot.exists()) throw new IOException("Папка не существует: "+root);
            if(!gameRoot.isDirectory()) throw new IOException("Это не папка: "+root);

            ProcessBuilder pb=new ProcessBuilder(exe.getAbsolutePath(),"-fsroot",root,"-ro","-verbose");
            pb.redirectErrorStream(true);
            process=pb.start();
            sendStatus("udpfsd ЗАПУЩЕН\nRoot: "+root+"\nDiscovery UDP: 62966\nBinary: "+exe.getAbsolutePath());

            reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line; StringBuilder tail=new StringBuilder();
            while(!stopping && (line=reader.readLine())!=null){
                tail.append(line).append('\n');
                if(tail.length()>1800)tail.delete(0,tail.length()-1800);
                sendPeerActivity(line);
                sendStatus("udpfsd работает\n--- log ---\n"+tail.toString());
            }
            if(!stopping){
                int code=process.waitFor();
                sendStatus("udpfsd ЗАВЕРШИЛСЯ\nКод: "+code+"\n--- log ---\n"+tail.toString());
            }
        }catch(Throwable e){
            if(!stopping){
                StringWriter sw=new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                sendStatus("ОШИБКА ЗАПУСКА udpfsd\n"+sw.toString());
            }
        }finally{
            if(reader!=null)try{reader.close();}catch(Exception ignored){}
        }
    }

    @Override public void onDestroy(){
        stopping=true;
        java.lang.Process p=process;
        process=null;
        if(p!=null){
            try{p.getInputStream().close();}catch(Exception ignored){}
            try{p.getErrorStream().close();}catch(Exception ignored){}
            try{p.getOutputStream().close();}catch(Exception ignored){}
            try{p.destroy();}catch(Exception ignored){}
            try{if(Build.VERSION.SDK_INT>=26 && p.isAlive())p.destroyForcibly();}catch(Exception ignored){}
        }
        if(wake!=null&&wake.isHeld())wake.release();
        sendStatus("Сервер остановлен");
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
