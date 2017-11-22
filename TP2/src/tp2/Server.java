/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tp2;

import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Gervásio Palha
 */
public class Server {
    private static final int PORT = 6063;
    private static int clientCount;
    private static List<List<Packet>> clientInfos;
    private static ReentrantLock stdsLock = new ReentrantLock(), ciLock = new ReentrantLock();
    private static ServerThread [] stds = new ServerThread[1000];
    
    private static class Packet {
        long timestamp;
        double db;
        
        public Packet (double db, long timestamp) {
            this.db=db;
            this.timestamp=timestamp;
        }
        
        public long getTimestamp(){
            return timestamp;
        }
        
        public void setTimestamp(long timetamp){
            this.timestamp = timestamp;
        }
        
        public double getDB(){
            return db;
        }
        
        public void setDB(double db){
            this.db = db;
        }
    }
    
    private static class WorkerThread extends Thread {
        
        public WorkerThread () {
            
        }
        
        public void run() {
            System.out.println("Server is running on port " + PORT);
            //BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
            
            
        }
        
        
        
        //retorna lista com média de DB por cada cliente desde o momento da conexão
        //a lista vem ordenada exatamente como a clientInfos, e os indíces vêm exatamente como o clientInfos
        public List<Double> averageByClient () {
            ciLock.lock();
            List<Double> avgs = new ArrayList<>();
            for (List<Packet> list: clientInfos) {
                double avg = 0;
                for (Packet p: list) {
                    avg += p.getDB();
                }
                avg = (list.size()>0) ? (avg/list.size()) : -1;
                avgs.add(avg);
            }
            ciLock.unlock();
            return avgs;
        }
        
        
    }
    
    private static class ServerThread extends Thread{
        int id, threadNumber;
        Socket s;
        BufferedReader in;
        PrintWriter out;
        int times;
        boolean active;

        public ServerThread(Socket s, int id, int number){
            this.s = s;
            this.id=id;
            this.threadNumber = number;
            clientCount = increment(clientCount);
            ciLock.lock();
            clientInfos.add(new ArrayList<>());
            ciLock.unlock();
            active = true;
            times=0;
        }
        
        public void deleteClient () {
            clientInfos.remove(id);
            try {
                    in.close();
                    out.close();
                    s.close();
                }
            catch (Exception e2) {}
            finally {
                stdsLock.lock();
                stds[threadNumber]=null;
                stdsLock.unlock();
            }
        }
        
        public void killClient () {
            out.println("kill");
            deleteClient();
        }

        public void run() {
            try{
                double db;
                long timestamp;
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);
                out.println("Server registered Client " + id);
                StringTokenizer st;
                while(active) {
                    st = new StringTokenizer (in.readLine(),";");
                    if (st.countTokens()==2) {
                        db = Double.parseDouble(st.nextToken());
                        timestamp = Long.parseLong(st.nextToken());
                        clientInfos.get(id).add(new Packet(db,timestamp));
                        //System.out.println(++times + ": Client <" + id + "> sent " + db + " db.");
                    }
                    if (st.countTokens()==1) {
                        if (st.nextToken().equals("over")) {
                            deleteClient();
                            active = false;
                        }
                    }
                }
            }
            catch(Exception e){
                deleteClient();
            }
        }
    }
    
    private static synchronized int increment(int x){
        return ++x;
    }
    
    private static int nextFree (Object [] array) {
        int size = array.length;
        for (int i = 0; i < size; i++) {
            if (array[i]==null) return i;
        }
        return -1;
    }
    
    public static void main(String[] args) throws Exception{
        ServerSocket ss = null;
        int i=0;
        try{
            clientCount = 0;
            clientInfos = new ArrayList<>();
            ss = new ServerSocket(PORT);
            WorkerThread wt = new WorkerThread();
            wt.start();
            while(true) {
                stdsLock.lock();
                i=nextFree(stds);
                if (i>=0) {
                    stds[i]=new ServerThread(ss.accept(), clientInfos.size(), i);
                    stds[i].start();
                    stdsLock.unlock();
                }
                else System.out.println("Every server thread is occupied. Try again later.");
            }      
        }
        catch(Exception e){}
        finally{
            stdsLock.unlock();
            ss.close();
        }
    }
}
