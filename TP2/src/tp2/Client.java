package tp2;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 *
 * @author Asclébio Ildefonso
 */

public class Client {
    private static final int INTERVAL = 2000;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Audio audio;
    private static Socket s;
    private static boolean true_;
    
    private static class ListenThread extends Thread {
        boolean listen = true;
        String s;
        
        @Override
        public void run () {
            
            while(listen) {
                
               try {
                   s = in.readLine();
                   if (s.equals("kill")) {
                       true_=false;
                       listen=false;
                   }
               }catch (Exception e) {
                   out.println("over");
                   System.out.println("Server probably closed.");
                   true_=false;
                   listen=false;
               }
            }
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        double val;
        int times=0;
        String conec;
        Scanner sc = new Scanner(System.in);
        
        audio = new Audio();
        
        if(args.length > 0) { conec = args[0]; }
        else {
            conec = sc.nextLine();
        }
        
        if(conec.equals("")) { conec = "localhost"; }
        
        try{
            true_ = true;
            s = new Socket(conec, 6063);
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(s.getOutputStream(), true);
            

            audio.connect();
            
            System.out.println(in.readLine());           
            ListenThread lt = new ListenThread();

            lt.start();
            
            while(true_){
                val = audio.capture();
                System.out.println(++times + ": This place has " + val + " db.");
                out.println(val + ";" +  System.currentTimeMillis());
                Thread.sleep(INTERVAL);
            }

        }catch(Exception e){
            System.out.println(e.getMessage());
            in.close();
            out.close();
            s.close();
            audio.close();
        }
    }
}
