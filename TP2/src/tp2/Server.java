package tp2;

import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 *
 * @author Gervásio Palhas
 */
public class Server {
    private static final int PORT = 6063, SIZE = 20, DEFAULT_TIME = 5000, QUANT = 1000, INTERVAL = 2000, DAY = 86400, MOD = 500;
    private static int clientCount, uniqueID;
    private static List<List<Packet>> clientInfos;
    private static List<Packet> buffer;
    private static ReentrantLock stdsLock = new ReentrantLock(), ciLock = new ReentrantLock();
    private static ReentrantLock bufferlock = new ReentrantLock(), csLock = new ReentrantLock();
    private static ReentrantLock logLock = new ReentrantLock();
    private static ServerThread[] stds = new ServerThread[QUANT];
    private static List<ClientStats> clientStats = new ArrayList<>();
    private static Audio audio = null;
    private static long tp;
    private static String logfile;
    
    private static class Packet {
        private long timestamp;
        private double db;
        private int client_id;
        
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
        
        public String toString(){
            StringBuilder sb = new StringBuilder();
            sb.append(this.timestamp).append(":");
            sb.append(this.db);
            return sb.toString();
        }
    }
    
    private static class SensorTimes {
        private int id;
        private double maxExp;
        private double remaining;
      
        public SensorTimes (int id) {
            this.id = id;
            this.maxExp = 28800.0;
            this.remaining = this.maxExp;
        }
        
      	//só é chamada esta função quando existe pelo menos um packet neste cliente
        //assumimos que quando recebemos um novo packet o sensor esteve numa intensidade correspondente ao packet anterior a esse
        public boolean update (List<Packet> lp, Packet p) {
            long timestamp;
            double db, oldDB, dif;
            int jumps;
            
            if (remaining < 0){ return true; }
            
            timestamp = p.getTimestamp();
            db = p.getDB();
            
            lp.add(p);
            
            if(audio.evaluateExposure(db) < 0) { return false; }
            if(lp.size() < 2) { return false; }
            
            oldDB = lp.get(lp.size()-2).getDB();
            timestamp -= lp.get(lp.size()-2).getTimestamp();
            timestamp /= 1000.0;
            remaining -= timestamp;
            if (normalize(db)!=normalize(oldDB)) {
                jumps = (normalize(db)-normalize(oldDB))/3;
                dif = maxExp - remaining;
                maxExp = audio.evaluateExposure(db);          
                remaining = maxExp - dif*Math.pow(2,-jumps);
            }                   
            return (remaining < 0);
        }
        
        public void reset () {
            this.maxExp = 28800.0;
            this.remaining = this.maxExp;
        }
  
  	public static int normalize(double decibel){
            int dif = (int)decibel - 85;
            if(decibel < 85.0){ return (int)decibel; }
            if(decibel > 130.0){ return (int)decibel; }
            return 85 + (3 * (((dif % 3) > 0 ? 1 : 0) + (dif / 3)));
  	}
        
        public boolean hasExceedLimit(){
            return remaining < 0;
        }
      	
    }
    
    private static class ClientStats {
        private int id, number;
        private double avg, std;
        private Map<Integer, Integer> percents;
        private List<Boolean> exceededLimit; 
        
        public ClientStats (int id) {
            this.id = id;
            avg = 0;
            std = 0;
            number = 0;
            percents = new HashMap<>();
            exceededLimit = new ArrayList<>();
        }
        public ClientStats (int id, double avg, double std) {
            this.id = id;
            this.avg = avg;
            this.std = std;
            this.number = 0;
            percents = new HashMap<>();
            exceededLimit = new ArrayList<>();
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
        
        public void saveIntensity(double decibel){
            int dec = SensorTimes.normalize(decibel);
            dec = dec > 130 ? 131 : (dec < 85 ? 84 : dec);
            if(!percents.containsKey(dec)) { percents.put(dec, 0); }
            percents.get(dec);
        }
        
        public Map<Integer, Double> getIntensityPercents(){
            Map<Integer, Double> ret = new HashMap<>();
            
            percents.entrySet().stream().forEach((entry) -> {
                ret.put(entry.getKey(), (double)entry.getValue() / number);
            });
            
            return ret;
        }
        
        public void hasExceedLimits(Boolean bool){
            exceededLimit.add(bool);
        }
        
        public List<String> getIntensityPercents_string(){
            int key;
            String h = new String(), l = new String();
            StringBuilder sb = new StringBuilder();
            List<String> ret = new ArrayList<>();
            Map<Integer,Double> vals = getIntensityPercents();
            
            for(Map.Entry<Integer,Double> entry : vals.entrySet()){
                key = entry.getKey();
                sb.append(key < 85 ? "<85" : (key > 130 ? ">130" : key));
                sb.append("=").append(entry.getValue());
                if(key < 85) { l = sb.toString(); }
                else if(key > 130) { h = sb.toString(); }
                else { ret.add(sb.toString()); }
                sb = new StringBuilder();
            }
            
            ret.sort((s1, s2) -> s1.compareTo(s2));
            if(!l.equals("")) { ret.add(0, l); }
            if(!h.equals("")) { ret.add(ret.size(), h); }
            
            return ret;
        }
        
        public List<String> getLimits_string(){
            List<String> ret = new ArrayList<>();
            
            for(Boolean b : exceededLimit){
                ret.add(b ? "1" : "0");
            }
            
            return ret;
        }
        
    }
    
    private static class WorkerThread extends Thread {
        private int duration;
        private double avg, stdev;
        private StringTokenizer st;
        private boolean cycle;
        private BufferedReader bf;
        
        public WorkerThread () {
            cycle=true;
            bf = new BufferedReader(new InputStreamReader(System.in));
            avg = 0.0; stdev = 0.0;
            duration = 0;
        }
        
        @Override
        public void run() {
            try {
                System.out.printf("Server is running on %s:%d\n", InetAddress.getLocalHost().getHostAddress(), PORT);
                while (cycle) {
                    Thread.sleep(INTERVAL);
                    /*clientStats.forEach((ClientStats k) -> {
                        if (k!=null){ System.out.printf("Sensor %d -> Average = %f, STD = %f\n", k.getId(), k.getAvg(), k.getStd()); }
                    });*/
                    processBuffer();
                }             
            }
            catch (Exception e) {
                System.out.println("Exception in WorkerThread.");
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
            faststd /= (test.length-1);
            faststd = fastStd(avg,test.length-1,3,faststd);
            System.out.println("FastSTD -> " + faststd);
        }
        
        private int getHighestIntensity(List<Packet> list){
            list.sort((p1, p2) -> ((Double)p1.getDB()).compareTo(p2.getDB()));
            return list.get(0).getID();
        }
        
        private void processBuffer(){
            int c_high;
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
            
            if(!local.isEmpty()){
                c_high = getHighestIntensity(local);
                System.out.printf("The highest sound intensity is near sensor %d\n", c_high);
            }
            
            
            vals = averageAndSTD(local);
            if(vals != null) {
                audio1 = audio.evaluateExposure(vals[0]);
                audio2 = audio.evaluateExposure(vals[0] + vals[1]);
                
                if(audio1 > -1 && (audio1*1000) < INTERVAL){
                    System.out.println("Too loud. Possible health risk.");
                }

                if(audio2 == 0.0){
                    System.out.println("Sound might be too high. Possible health risk");
                }
            }
            
        }
        
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
            
            try {
                ciLock.lock();
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
        private int id, threadNumber, uniqueID;
        private Socket s;
        private BufferedReader in;
        private PrintWriter out;
        private int times;
        private boolean active;
        private Packet p;
        private SensorTimes sensorT;
        private List<String> log;
        private long stimestamp = 0;

        public ServerThread(Socket s, int id, int number){
            this.s = s;
            this.id = id;
            this.threadNumber = number;
            clientCount = increment(clientCount, 1);
            Server.uniqueID = increment(uniqueID, 1);
            this.uniqueID = Server.uniqueID;
            sensorT = new SensorTimes(id);
            stimestamp = System.currentTimeMillis();
            log = new ArrayList<>();
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
        
        private void deleteClient () {
            try{
                ciLock.lock();
                clientInfos.remove(id);
                clientInfos.add(id,null);
            }finally{
                ciLock.unlock();
            }
            
            clientCount = increment(clientCount, -1);
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
        
        private void updateStats(double db, ClientStats cs) {
            int number = cs.getNumber();
            double avg = cs.getAvg();
            double variance = Math.pow(cs.getStd(),2);
            cs.saveIntensity(db);
            cs.setNumber(number + 1);
            if (number == 0) {
                cs.setAvg(db);
                cs.setStd(0);
            } 
            else {
                cs.setAvg(fastAvg(avg,number,db));
                cs.setStd(fastStd(avg,number,db,variance));
            }
        }
        
        private boolean isOutlier (double db) {
            //ClientStats cs = clientStats.get(id);
            return (db > 200);
            //return (cs.getNumber()>2) ? (db>cs.getStd()*MOD) : false;
        }
        
        private void saveToLog(String filename){
            int size, i;
            StringBuilder sb = new StringBuilder();
            List<String> vals = clientStats.get(id).getIntensityPercents_string();
            List<String> bools = clientStats.get(id).getLimits_string();
            Writer target;
            try{
                target = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)));
            
                sb.append(Server.timestampToDate(this.stimestamp)).append("|");
                sb.append(Server.timestampToDate(System.currentTimeMillis())).append("|");
                sb.append(this.uniqueID).append("|");
                for(i = 0, size = vals.size(); i < size; ++i){
                    sb.append(vals.get(i));
                    if(i < size - 1) { sb.append(","); }
                }
                sb.append("|");
                for(i = 0, size = bools.size(); i < size; ++i){
                    sb.append(bools.get(i));
                    sb.append(",");
                }
                sb.append(this.sensorT.hasExceedLimit() ? 1 : 0);
                sb.append("\r\n");
                
                logLock.lock();
                target.append(sb.toString());
                target.flush();
                target.close();
            }catch(Exception e){}
            finally{
                logLock.unlock();
            }
        }
        
        @Override
        public void run() {
            try{
                double db;
                long timestamp;
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);
                out.printf("Server registered Sensor %d.\n", id);
                System.out.printf("Sensor %d operational.\n", id);
                StringTokenizer st;
                while(active) {
                    if ((System.currentTimeMillis()-this.stimestamp)%(DAY*1000)==0) {
                        clientStats.get(id).hasExceedLimits(this.sensorT.hasExceedLimit());
                        this.sensorT.reset();
                    }
                    st = new StringTokenizer (in.readLine(),";");
                    if (st.countTokens()==2) {
                        db = Double.parseDouble(st.nextToken());
                        if (isOutlier(db)==false) {
                            timestamp = Long.parseLong(st.nextToken());
                            p = new Packet(id,db,timestamp);
                            if (sensorT.update(clientInfos.get(id),p)) {
                                System.out.printf("Sensor %d has reached his daily exposure time limit.\n", id);
                                out.println("Daily exposure time limit reached. Go somewhere quieter. Fast.");
                            }
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
                saveToLog(logfile);
            }
            catch(Exception e){
                System.out.printf("Sensor %d stopped suddenly.\n", id);
                System.out.flush();
                saveToLog(logfile);
                deleteClient();
            }
        }
    }
    
    private static synchronized int increment(int x, int y){
        return x + y;
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
        
        for(Double d : values){
            avg += d;
        }
        
        return avg / values.size();
    }

    public static double stdDeviation(List<Double> values){
        double stdev, avg = 0.0, temp = 0.0;

        if(values.size() < 2){ return 0.0; }

        avg = average(values);
        
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
    
    public static String timestampToDate(long timestamp){
        Timestamp tp = new Timestamp(timestamp);
        Date date = new Date(tp.getTime());
        return date.toString();
    }
    
    public static void main(String[] args) throws Exception {
        ServerSocket ss;
        int i;
        tp = System.currentTimeMillis();
        audio = new Audio(true);
        buffer = new ArrayList<>();
        
        try{
            logfile = tp + ".log";
            clientCount = 0;
            uniqueID = 0;
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
