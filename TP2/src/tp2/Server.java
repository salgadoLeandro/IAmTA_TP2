package tp2;

import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Gervásio Palhas
 */
public class Server {
    private static final int PORT = 6063, SIZE = 20, QUANT = 1000;
    private static int clientCount;
    private static List<List<Packet>> clientInfos;
    private static final ReentrantLock stdsLock = new ReentrantLock(), ciLock = new ReentrantLock();
    private static final ServerThread[] stds = new ServerThread[QUANT];
    private static final ReentrantLock[] stBufferLock = new ReentrantLock[QUANT];
    private static Audio audio = null;
    
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
        
        StringTokenizer st;
        
        public WorkerThread () {
        }
        
        @Override
        public void run() {
            System.out.println("Server is running on port " + PORT);

            //BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));


            List<ClientStats> cs = new ArrayList<>();
            try {
                while (true) {
                    Thread.sleep(2000);
                    cs = averageByClient();
                    cs.forEach((k) -> System.out.println("Client "+k.getID()+" -> Average = "+k.getAverage()+", STD = "+k.getSTD()));
                }             
            }
            catch (Exception e) {
                System.out.println("Exception in WorketThread:" + e.getMessage());
            }
            
        }
        
        //retorna lista com média de DB por cada cliente desde o momento da conexão
        //a lista vem ordenada exatamente como a clientInfos, e os indíces vêm exatamente como o clientInfos
        //será preciso controlo de concorrência?
        public List<ClientStats> averageByClient () {
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
        
        public  double [] averageAndSTD(List<Packet> list) {
            double [] r = new double[2];
            if (list.isEmpty()) return null;
            for (Packet p: list) {
                r[0] += p.getDB();
            }
            r[0] /= (list.size());
            for (Packet p: list) {
                r[1] += (p.getDB()-r[0])*(p.getDB()-r[0]);
            }
            r[1] /= ((list.size())-1);
            return r;
        }
        
        
        
        
        public List<Double> medianByClient(){
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
    
    private static class IntensityWorker extends Thread{
        private int id;
        private int interval;
        private double timeInterval;
        private List<Packet> buffer;
        private List<Double> temp;
        private boolean run;
        
        public IntensityWorker(int id, int interval, List<Packet> buffer){
            this.id = id;
            this.interval = interval;
            this.buffer = buffer;
            this.run = true;
        }
        
        @Override
        public void run(){
            while(run){
                temp = new ArrayList<>();
                stBufferLock[id].lock();
                for(Packet p : buffer){
                    temp.add(p.getDB());
                }
                timeInterval = buffer.get(buffer.size() - 1).timestamp - buffer.get(0).timestamp;
                buffer.clear();
                stBufferLock[id].unlock();
                
                double avg = average(temp);
                double std_dev = stdDeviation(temp);
                
                //outras coisas
            }
        }
        
        public void end(){
            run = false;
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
    }
    
    private static class ServerThread extends Thread{
        private int id, threadNumber;
        private Socket s;
        private BufferedReader in;
        private PrintWriter out;
        private int times;
        private boolean active;
        private List<Packet> buffer;
        private static final int interval = 5;

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
            buffer = new ArrayList<>();
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
                stBufferLock[threadNumber]=null;
                stdsLock.unlock();
            }
        }
        
        public void killClient () {
            out.println("kill");
            deleteClient();
        }
        
        @Override
        public void run() {
            Packet p;
            IntensityWorker iw = new IntensityWorker(id, interval, buffer);
            iw.start();
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
                        if(audio.evaluateExposure(db) < interval)
                            System.out.println("Too loud, Client " + id);
                        timestamp = Long.parseLong(st.nextToken());
                        p = new Packet(db, timestamp);
                        clientInfos.get(id).add(p);
                        
                        stBufferLock[threadNumber].lock();
                        buffer.add(p);
                        stBufferLock[threadNumber].unlock();
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
                iw.end();
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
        audio = new Audio(true);
        try{
            clientCount = 0;
            clientInfos = new ArrayList<>();
            for (i=0; i < SIZE; i++) {
                clientInfos.add(null);
            }
            ss = new ServerSocket(PORT);
            WorkerThread wt = new WorkerThread();
            wt.start();
            
            while(true) {
                stdsLock.lock();

                try {
                    i=nextFree(stds);
                    if (i>=0) {
                        stds[i] = new ServerThread(ss.accept(), clientInfos.size(), i);
                        stds[i].start();
                        stBufferLock[i] = new ReentrantLock();
                    }
                    else System.out.println("Every server thread is occupied. Try again later.");
                } finally {
                    stdsLock.unlock();
                }
                
                //isto parece cópia do que tá em cima. confirmas? #overhere
                i=nextFree(stds);
                if (i>=0) {
                    stds[i] = new ServerThread(ss.accept(), nextFree(clientInfos.toArray()), i);
                    stds[i].start();
                    stBufferLock[i] = new ReentrantLock();
                }
                else System.out.println("Every server thread is occupied. Try again later.");
                stdsLock.unlock();

            }      
        }
        catch(Exception e){System.out.println(e.getMessage());}
        
    }
}
