package tp2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class Audio {
    private ByteArrayOutputStream out;
    private AudioFormat format;
    private DataLine.Info info;
    private TargetDataLine line;
    private HashMap<String,List<Double>> tablEvaluation;
    private HashMap<List<Integer>,String> tablExposure;
    private static final double CALIBRATION_VALUE = -80;
    double mMaxValue;
    
    public boolean connect() throws LineUnavailableException{
        boolean resp = true;
        this.out = new ByteArrayOutputStream();
        float sampleRate = 16000;
        int sampleSizeInBits = 8;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = true;
        
        this.format = new AudioFormat(sampleRate, sampleSizeInBits, channels,signed, bigEndian);
        this.info = new DataLine.Info(TargetDataLine.class, format);
        this.line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        
        this.mMaxValue = 0;
        
        return resp;
    }
    
    public double capture(){
        double splValue = 0.0;
        double rmsValue = 0.0;
        int bufferSize = line.getBufferSize() * 2;
        byte buffer[] = new byte[bufferSize];
        double P0 = 0.000002;
        int counted = line.read(buffer, 0, buffer.length);
        
        for (int i = 0; i < bufferSize - 1; i++) {
            rmsValue += (short) buffer[i] * (short) buffer[i];
        }
        
        rmsValue /= bufferSize;
        rmsValue = Math.sqrt(rmsValue);
        splValue = 20 * Math.log10(rmsValue / P0);
        splValue += CALIBRATION_VALUE;
        splValue = Math.round(splValue * 100) / 100;
        
        if (mMaxValue < splValue) {
            mMaxValue = splValue;
        }
        
        return splValue;
}

    public boolean close() throws IOException{
        boolean resp = true;
        this.line.close();
        this.out.close();
        return resp;
    }
    
    //chave -> valor int dos decibéis
    //valor -> valor float do limite de tempo de exposição
    public void fillTablExposure(){
        tablExposure = new HashMap<> ();
        List<Integer> aux = new ArrayList<> ();
        
        aux.add(85);
        aux.add(88);
        tablExposure.put(aux,"8 Hours");
        aux= new ArrayList<> ();
        
        aux.add(88);
        aux.add(91);
        tablExposure.put(aux,"4 Hours");
        aux= new ArrayList<> ();
        
        aux.add(91);
        aux.add(94);
        tablExposure.put(aux,"2 Hours");
        aux= new ArrayList<> ();
        
        aux.add(94);
        aux.add(97);
        tablExposure.put(aux,"1 Hour");
        aux= new ArrayList<> ();
        
        aux.add(97);
        aux.add(100);
        tablExposure.put(aux,"30 minutes");
        aux= new ArrayList<> ();
        
        aux.add(100);
        aux.add(103);
        tablExposure.put(aux,"15 minutes");
        aux= new ArrayList<> ();
        
        aux.add(103);
        aux.add(106);
        tablExposure.put(aux,"7.5 minutes");
        aux= new ArrayList<> ();
        
        aux.add(106);
        aux.add(109);
        tablExposure.put(aux,"4 minutes");
        aux= new ArrayList<> ();
        
        aux.add(109);
        aux.add(112);
        tablExposure.put(aux,"2 minutes");
        aux= new ArrayList<> ();
        
        aux.add(112);
        aux.add(115);
        tablExposure.put(aux,"1 minute");
        aux= new ArrayList<> ();
        
        aux.add(115);
        tablExposure.put(aux,"30 seconds");
        aux= new ArrayList<> ();
    }
    
    public void fillTablEvalutaion(){
        tablEvaluation = new HashMap<> ();
        ArrayList<Double> aux = new ArrayList<> ();
        
        aux.add(0.0);
        aux.add(15.0);
        
        tablEvaluation.put("Soft Sound", aux);
        aux= new ArrayList<> ();
        
        aux.add(16.0);
        aux.add(45.0);
        tablEvaluation.put("Whisper",aux);
        aux= new ArrayList<> ();
        
        aux.add(45.0);
        aux.add(55.0);
        tablEvaluation.put("Rainfall",aux);
        aux= new ArrayList<> ();
        
        aux.add(55.0);
        aux.add(65.0);
        tablEvaluation.put("Typical speech",aux);
        aux= new ArrayList<> ();
        
        aux.add(65.0);
        aux.add(75.0);
        tablEvaluation.put("Washing Machine",aux);
        aux= new ArrayList<> ();
        
        aux.add(75.0);
        aux.add(85.0);
        tablEvaluation.put("Busy city traffic",aux);
        aux= new ArrayList<> ();
        
        aux.add(85.0);
        aux.add(95.0);
        tablEvaluation.put("Gas mower or hair dryer",aux);
        aux= new ArrayList<> ();
        
        aux.add(95.0);
        aux.add(105.0);
        tablEvaluation.put("Walkman or tractor",aux);
        aux= new ArrayList<> ();
        
        aux.add(105.0);
        aux.add(115.0);
        tablEvaluation.put("Leaf blower or rock concert or chainsaw",aux);
        aux= new ArrayList<> ();
        
        aux.add(155.0);
        aux.add(125.0);
        tablEvaluation.put("Ambulance or jack hammer",aux);
        aux= new ArrayList<> ();
        
        aux.add(125.0);
        aux.add(135.0);
        tablEvaluation.put("Jet plane (from 100 ft.)",aux);
        aux= new ArrayList<> ();
        
        aux.add(135.0);
        aux.add(145.0);
        tablEvaluation.put("Fireworks or gun shot",aux);
        aux= new ArrayList<> ();
        
        aux.add(145.0);
        aux.add(165.0);
        tablEvaluation.put("12-gauge shotgun",aux);
        aux= new ArrayList<> ();
        
        aux.add(165.0);
        tablEvaluation.put("Rocket Launch",aux);
        aux= new ArrayList<> ();
    }
    
    public void createInstaEvaluation(double decibel){
        List<Double> aux;
        
        for(Map.Entry<String,List<Double>> kp : tablEvaluation.entrySet()){
            aux = kp.getValue();
            if(aux.size() == 2){
                if(aux.get(0) <= decibel && decibel < aux.get(1)){
                    System.out.println(kp.getKey());
                    break;
                }
            }else if(aux.size() == 1){
                if(aux.get(0) == decibel){
                    System.out.println(kp.getKey());
                    break;
                }
            }
        }
    }
    
    // map.get( chave ), chave -> (((int)dbs-85) / 3) * 3 + 85
    public void createExposureEvaluation(int decibel){
        Integer aux = (Integer) decibel;
        List<Integer> dbs;
        
        for(Map.Entry<List<Integer>,String> kp : tablExposure.entrySet()){
            dbs = kp.getKey();
            if(dbs.size() == 2){
                if(dbs.get(0) <= aux && dbs.get(1) > aux ){
                  System.out.println(kp.getValue());
                }   
            }else if (dbs.size() == 1){
                System.out.println(kp.getValue());
            }
        }
    }
        
}
