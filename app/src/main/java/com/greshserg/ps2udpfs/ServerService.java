package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;

public class ServerService extends Service {
    Process process; PowerManager.WakeLock wake;
    @Override public void onCreate(){super.onCreate();
        String ch="ps2_udpfs"; if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(ch,"PS2 UDPFS",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this); n.setContentTitle("PS2 UDPFS Server").setContentText("Сервер работает").setSmallIcon(android.R.drawable.stat_sys_upload); startForeground(1001,n.build());
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE); wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PS2Udpfs:Server"); wake.acquire();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){String root=intent.getStringExtra("root"); new Thread(()->runServer(root)).start(); return START_STICKY;}
    void runServer(String root){try{
        File exe=new File(getFilesDir(),"udpfsd");
        if(!exe.exists()){ InputStream in=getAssets().open("udpfsd"); FileOutputStream out=new FileOutputStream(exe); byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)out.write(b,0,n); in.close();out.close(); exe.setExecutable(true); }
        ProcessBuilder pb=new ProcessBuilder(exe.getAbsolutePath(),"-fsroot",root,"-ro","-verbose"); pb.redirectErrorStream(true); process=pb.start();
        BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream())); while(r.readLine()!=null){}
    }catch(Exception ignored){}
    }
    @Override public void onDestroy(){if(process!=null)process.destroy();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
