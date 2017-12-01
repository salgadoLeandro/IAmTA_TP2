package tp2;

import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 *
 * @author Gervásio Palhas
 */
public class Server {
    private static final int PORT = 6063, SIZE = 20, DEFAULT_TIME = 5000, QUANT = 1000, INTERVAL = 5000;
    private static int clientCount;
    private static List<List<Packet>> clientInfos;
    private static List<Packet> buffer;
    private static final ReentrantLock stdsLock = new ReentrantLock(), ciLock = new ReentrantLock();
    private static final ReentrantLock bufferlock = new ReentrantLock();
    private static final ServerThread[] stds = new ServerThread[QUANT];
    private static Audio audio = null;
    
    private static class Packet {
        long timestamp;
        double db;
        int client_id;
        
        public Packet (int id, double db, long timestamp) {
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
        
        public int getID(){
            return client_id;
        }
        
        public void setID(int id){
            this.client_id = id;
        }
    }
    
    private static class ClientStats {
        int id;
        double avg, std;
        
        public ClientStats (int id) {
            this.id=id;
            avg=0;
            std=0;
        }
        public ClientStats (int id, double avg, double std) {
            this.id=id;
            this.avg=avg;
            this.std=std;
        }
        
        public int getID () {
            return this.id;
        }        
        public double getAverage() {
            return this.avg;
        }
        public double getSTD() {
            return this.std;
        }
        
    }
    
    
    
    private static class WorkerThread extends Thread {
        int duration;
        double avg, stdev;
        StringTokenizer st;
        boolean cycle;
        BufferedReader bf;
        
        public WorkerThread () {
            cycle=true;
            bf = new BufferedReader(new InputStreamReader(System.in));
            avg = stdev = 0.0;
            duration = 0;
        }
        
        @Override
        public void run() {
            System.out.println("Server is running on port " + PORT);

            List<ClientStats> cs = new ArrayList<>();
            
            try {
                
                while (cycle) {
                    Thread.sleep(5000);
                    cs = statsByClient();
                    cs.forEach((ClientStats k) -> {
                        System.out.printf("Client %d -> Average = %f, STD = %f\n", k.getID(), k.getAverage(), k.getSTD());
                    });
                    processBuffer();
                    
                }             
            }
            catch (Exception e) {
                System.out.println("Exception in WorketThread:" + e.getMessage());
            }
            
        }
        
        private void processBuffer(){
            double time;
            List<Packet> local = new ArrayList<>();
            double vals[];
            
            try{
                bufferlock.lock();
                while(!buffer.isEmpty()){
                    local.add(buffer.remove(0));
                }
            }finally{
                bufferlock.unlock();
            }
            
            for(Packet p : local){
                if(audio.evaluateExposure(p.getDB()) == 0.0){
                    System.out.printf("Client %d -> Too loud. Possible health risk.\n", p.client_id);
                }
            }
            
            vals = averageAndSTD(local);
            
            //fazer coisas com média e desvio padrão
            
        }
        
        //retorna lista com média de DB por cada cliente desde o momento da conexão
        //a lista vem ordenada exatamente como a clientInfos, e os indíces vêm exatamente como o clientInfos
        //será preciso controlo de concorrência?
        private List<ClientStats> statsByClient () {
            List<ClientStats> avgs = new ArrayList<>();
            int size = clientInfos.size();
            List<Packet> aux;
            for (int i = 0; i < size; i++) {
                double [] values = new double[2];
                aux = clientInfos.get(i);
                if (aux!=null) {
                    values = (aux.size()>0) ? averageAndSTD(aux) : values;
                    avgs.add(new ClientStats(i,values[0],values[1]));
                } 
            }
            return avgs;
        }
        
        private double[] averageAndSTD(List<Packet> list) {
            double [] r = new double[2];
            List<Double> temp;
            if (list.isEmpty()) return null;
            
            temp = list.stream().map(Packet::getDB).collect(Collectors.toList());
            
            r[0] = average(temp);
            if(!(list.size() < 2))
                ;
            r[1] = list.size() < 2 ? 0 : stdDeviation(temp);
            return r;
        }
        
        private List<Double> medianByClient(){
            List<Double> medians = new ArrayList<> ();
            double median = 0.0;
            int m;
            
            ciLock.lock();
            try {
                for(List<Packet> pt: clientInfos){
                    median = 0.0;
                    m = pt.size()/2;
                    Collections.sort(pt, (Packet t, Packet t1) -> {
                        Double x = (Double) t.getDB();
                        Double y = (Double) t1.getDB();
                        return x.compareTo(y);
                    });
                    
                    median = pt.size()%2 == 1 ? pt.get(m).getDB() : (pt.get(m-1).getDB()+pt.get(m).getDB())/2.0;
                    
                    medians.add(median);
                    
                }
            } finally {
                ciLock.unlock();
            }
            
            return medians;
        }
               
        
    }
    
    private static class ServerThread extends Thread{
        int id, threadNumber;
        Socket s;
        BufferedReader in;
        PrintWriter out;
        int times;
        boolean active;
        Packet p;

        public ServerThread(Socket s, int id, int number){
            this.s = s;
            this.id=id;
            this.threadNumber = number;
            clientCount = increment(clientCount);
            ciLock.lock();
            clientInfos.add(id,new ArrayList<>());
            ciLock.unlock();
            active = true;
            times=0;
        }
        
        
        public void deleteClient () {
            ciLock.lock();
            clientInfos.add(id,null);
            ciLock.unlock();
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
        
        //vou fazer cenas aqui l8r
        public void run() {
            try{
                double db;
                long timestamp;
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);
                out.println("Server registered Client " + id);
                System.out.println("Good "+id);
                StringTokenizer st;
                while(active) {
                    st = new StringTokenizer (in.readLine(),";");
                    if (st.countTokens()==2) {
                        db = Double.parseDouble(st.nextToken());
                        timestamp = Long.parseLong(st.nextToken());
                        p = new Packet(id,db,timestamp);
                        clientInfos.get(id).add(p);
                        try{
                            bufferlock.lock();
                            buffer.add(p);
                        } finally{
                            bufferlock.unlock();
                        }
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
                System.out.println(e.getMessage());
                System.out.println("Client " + id + " cancelled suddenly.");
                System.out.flush();
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
    
    public static double average(List<Double> values){
        double avg = 0.0;

        if(values.isEmpty())
            return 0.0;


        for(Double d : values){
            avg += d;
        }
        
        return avg / values.size();
    }

    public static double stdDeviation(List<Double> values){
        double stdev = 0.0, temp = 0.0;
        double avg = 0.0;

        if(values.size() < 2) return 0.0;

        avg = average(values);

        for(Double d : values){
            temp += Math.pow(d - avg, 2);
        }

        stdev = Math.sqrt(temp / values.size());

        return stdev;
    }
    
    public static void main(String[] args) throws Exception{
        ServerSocket ss = null;
        int i=0;
        audio = new Audio(true);
        buffer = new ArrayList<>();
        try{
            clientCount = 0;
            clientInfos = new ArrayList<>();
            for (i=0; i < SIZE;i++) {
                clientInfos.add(null);
            }
            ss = new ServerSocket(PORT);
            WorkerThread wt = new WorkerThread();
            wt.start();
            
            while(true) {
                try {
                    stdsLock.lock();
                    i=nextFree(stds);
                    if (i>=0) {
                        stds[i]=new ServerThread(ss.accept(), clientInfos.size(), i);
                        stds[i].start();
                    }
                    else System.out.println("Every server thread is occupied. Try again later.");
                } finally {
                    stdsLock.unlock();
                }

            }      
        }
        catch(Exception e){System.out.println(e.getMessage());}
        
    }
}
