package tp2;

import java.io.*;
import java.net.Socket;

/**
 *
 * @author Asclébio Ildefonso
 */

public class Client {
    private static BufferedReader in;
    private static PrintWriter out;
    private static Audio audio;
    private static Socket s;
    private static boolean true_;
    
    private static class ListenThread extends Thread {
        boolean listen = true;
        String s;
        
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
        
        audio = new Audio();
        audio.connect();

        try{
            true_ = true;
            s = new Socket("localhost", 6063);
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
                Thread.sleep(1000);
            }

        }catch(Exception e){
            in.close();
            out.close();
            s.close();
            audio.close();
        }
    }
}
