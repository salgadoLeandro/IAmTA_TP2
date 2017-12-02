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
    private static final int PORT = 6063, SIZE = 20, DEFAULT_TIME = 5000, QUANT = 1000, INTERVAL = 2000;
    private static int clientCount;
    private static List<List<Packet>> clientInfos;
    private static List<Packet> buffer;
    private static ReentrantLock stdsLock = new ReentrantLock(), ciLock = new ReentrantLock();
    private static ReentrantLock bufferlock = new ReentrantLock(), csLock = new ReentrantLock();
    private static ServerThread[] stds = new ServerThread[QUANT];
    private static List<ClientStats> clientStats = new ArrayList<>();
    private static Audio audio = null;
    
    private static class Packet {
        long timestamp;
        double db;
        int client_id;
        
        public Packet (int id, double db, long timestamp) {
            this.client_id=id;
            this.db=db;
            this.timestamp=timestamp;
        }
        
        public long getTimestamp(){
            return timestamp;
        }
        
        public void setTimestamp(long timestamp){
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
        int id, number;
        double avg, std;
        
        public ClientStats (int id) {
            this.id=id;
            avg=0;
            std=0;
            number=0;
        }
        public ClientStats (int id, double avg, double std) {
            this.id=id;
            this.avg=avg;
            this.std=std;
            this.number=number;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        public double getAvg() {
            return avg;
        }

        public void setAvg(double avg) {
            this.avg = avg;
        }

        public double getStd() {
            return std;
        }

        public void setStd(double std) {
            this.std = std;
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
            avg = 0.0; stdev = 0.0;
            duration = 0;
        }
        
        @Override
        public void run() {
            System.out.println("Server is running on port " + PORT);            
            try {
                while (cycle) {
                    Thread.sleep(INTERVAL);
                    clientStats.forEach((ClientStats k) -> {
                        if (k!=null) System.out.printf("Sensor %d -> Average = %f, STD = %f\n", k.getId(), k.getAvg(), k.getStd());
                    });
                    processBuffer();
                }             
            }
            catch (Exception e) {
                System.out.println("Exception in WorketThread.");
                e.printStackTrace();
            }   
        }
        
        private void testStuff() {   
            double [] test = {1,2,3,4,5,6,789,71,46,6,3,3};
            double avg = 0;
            for (int i = 0; i < test.length; i++) {
                avg+=test[i];
            } 
            avg /= test.length;
            System.out.println(avg);        
            double fast = 0;
            for (int i = 0; i < test.length-1; i++) {
                fast+=test[i];
            } 
            fast /= (test.length - 1);
            fast = fastAvg(fast,test.length-1,3);
            System.out.println(fast);
            double std = 0;
            for (int i = 0; i < test.length; i++) {
                std += Math.pow(test[i]-avg,2);
            }
            std = Math.sqrt(std/test.length);
            System.out.println("Std -> " + std);
            double faststd = 0;
            for (int i = 0; i < test.length-1; i++) {
                faststd += Math.pow(test[i]-avg,2);
            }
            faststd = faststd/(test.length-1);
            faststd = fastStd(avg,test.length-1,3,faststd);
            System.out.println("FastSTD -> " + faststd);
        }
        
        private void processBuffer(){
            double audio1, audio2;
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
            audio1 = audio.evaluateExposure(vals[0]);
            audio2 = audio.evaluateExposure(vals[0] + vals[1]);
            
            if(audio1 < INTERVAL){
                System.out.println("Too loud. Possible health risk");
            }
            
            if(audio2 == 0.0){
                System.out.println("Sound might be too high. Possible health risk");
            }
            
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
            if (list.isEmpty()){ return null; }
            
            temp = list.stream().map(Packet::getDB).collect(Collectors.toList());
            
            r[0] = average(temp);
            r[1] = list.size() < 2 ? 0 : stdDeviation(temp);
            return r;
        }
        
        private List<Double> medianByClient(){
            List<Double> medians = new ArrayList<> ();
            double median;
            int m;
            
            ciLock.lock();
            try {
                for(List<Packet> pt: clientInfos){
                    m = pt.size()/2;
                    Collections.sort(pt, (Packet t, Packet t1) -> {
                        Double x = t.getDB();
                        Double y = t1.getDB();
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
            try{
                ciLock.lock();
                clientInfos.add(id,new ArrayList<>());
                clientStats.add(id,new ClientStats(id));
            }finally{
                ciLock.unlock();
            }
            active = true;
            times=0;
        }
        
        
        public void deleteClient () {
            try{
                ciLock.lock();
                clientInfos.remove(id);
                clientInfos.add(id,null);
            }finally{
                ciLock.unlock();
            }
            
            clientStats.remove(id);
            clientStats.add(id,null);
            
            try {
                in.close();
                out.close();
                s.close();
            }
            catch (Exception e2) {}
            
            try{
                stdsLock.lock();
                stds[threadNumber]=null;
            }finally{
                stdsLock.unlock();
            }
        }
        
        public void killClient () {
            out.println("kill");
            deleteClient();
        }
        
        public void updateStats(double db, ClientStats cs) {
            int number = cs.getNumber();
            double avg = cs.getAvg();
            double variance = Math.pow(cs.getStd(),2);
            cs.setNumber(number+1);
            if (number==0) {
                cs.setAvg(db);
                cs.setStd(0);
            } 
            else {
                cs.setAvg(fastAvg(avg,number,db));
                cs.setStd(fastStd(avg,number,db,variance));
            }   
        }
        
        public boolean isOutlier (double db) {
            ClientStats cs = clientStats.get(id);
            return (cs.getNumber()>2) ? (db>cs.getStd()*100) : false;
        }
        
        //vou fazer cenas aqui ler
        @Override
        public void run() {
            try{
                double db;
                long timestamp;
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);
                out.printf("Server registered Sensor %d.\n",id);
                System.out.printf("Sensor %d operational.\n", id);
                StringTokenizer st;
                while(active) {
                    st = new StringTokenizer (in.readLine(),";");
                    if (st.countTokens()==2) {
                        db = Double.parseDouble(st.nextToken());
                        if (isOutlier(db)==false) {
                            timestamp = Long.parseLong(st.nextToken());
                            p = new Packet(id,db,timestamp);
                            clientInfos.get(id).add(p);
                            updateStats(db,clientStats.get(id));
                            try{
                                bufferlock.lock();
                                buffer.add(p);
                            } finally{
                                bufferlock.unlock();
                            }
                        }
                    }
                    if (st.countTokens()==1) {
                        if (st.nextToken().equals("over")) {
                            System.out.printf("Sensor %d disconnected.\n", id);
                            deleteClient();
                            active = false;
                        }
                    }
                    
                }
            }
            catch(Exception e){
                System.out.println(e.getMessage());
                System.out.println("Sensor " + id + " stopped suddenly.");
                System.out.flush();
                deleteClient();
            }
        }
    }
    
    private static synchronized int increment(int x){
        return x + 1;
    }
    
    private static int nextFree (Object [] array) {
        int size = array.length;
        for (int i = 0; i < size; i++) {
            if (array[i]==null){ return i; }
        }
        return -1;
    }
    
    public static double average(List<Double> values){
        double avg = 0.0;

        if(values.isEmpty()){ return 0.0; }
        
        avg = average(values);

        for(Double d : values){
            avg += d;
        }
        
        return avg / values.size();
    }

    public static double stdDeviation(List<Double> values){
        double stdev, avg = 0.0, temp = 0.0;

        if(values.size() < 2){ return 0.0; }

        for(Double d : values){
            temp += Math.pow(d - avg, 2);
        }

        stdev = Math.sqrt(temp / values.size());

        return stdev;
    }
    
    public static double fastAvg (double avg, int number,double x) {
        return ((avg*number)/(number+1)) + x/(number+1);
    }
    
    public static double fastStd (double avg, int number,double x,double variance) {
        return Math.sqrt((variance*number)/(number+1) + (Math.pow(x-avg,2))/(number+1));
    }
    
    
    public static void main(String[] args) throws Exception {
        ServerSocket ss;
        int i;
        audio = new Audio(true);
        buffer = new ArrayList<>();
        try{
            clientCount = 0;
            clientInfos = new ArrayList<>();
            for (i=0; i < SIZE;i++) {
                clientInfos.add(null);
                clientStats.add(null);
            }
            ss = new ServerSocket(PORT);
            WorkerThread wt = new WorkerThread();
            wt.start();
            
            while(true) {
                try {
                    stdsLock.lock();
                    i=nextFree(stds);
                    if (i>=0) {
                        stds[i]=new ServerThread(ss.accept(), nextFree(clientInfos.toArray()), i);
                        stds[i].start();
                    }
                    else{ System.out.println("Every server thread is occupied. Try again later."); }
                } finally {
                    stdsLock.unlock();
                }

            }      
        }
        catch(Exception e){System.out.println(e.getMessage());}
    }
}
