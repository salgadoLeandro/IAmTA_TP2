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
    private HashMap<Integer,Double> tablExposure;
    private static final double CALIBRATION_VALUE = -80;
    double mMaxValue;
    
    public Audio(){ }
    
    public Audio(boolean bool){
        if(bool){
            this.fillTablExposure();
            this.fillTablEvaluation();
        }
    }
    
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
    
    public final void fillTablExposure(){
        tablExposure = new HashMap<>();
        
        tablExposure.put(85, 28800.0);
        tablExposure.put(88, 14400.0);
        tablExposure.put(91, 7200.0);
        tablExposure.put(94, 3600.0);
        tablExposure.put(97, 1800.0);
        tablExposure.put(100, 900.0);
        tablExposure.put(103, 450.0);
        tablExposure.put(106, 222.0);
        tablExposure.put(109, 112.0);
        tablExposure.put(112, 56.0);
        tablExposure.put(115, 28.0);
        tablExposure.put(118, 14.0);
        tablExposure.put(121, 7.0);
        tablExposure.put(124, 3.0);
        tablExposure.put(127, 1.0);
        tablExposure.put(130, 0.0);
        
    }
    
    public final void fillTablEvaluation(){
        tablEvaluation = new HashMap<> ();
        ArrayList<Double> aux = new ArrayList<> ();
        
        aux.add(0.0);
        aux.add(15.0);
        
        tablEvaluation.put("Soft Sound", aux);
        aux = new ArrayList<> ();
        
        aux.add(16.0);
        aux.add(45.0);
        tablEvaluation.put("Whisper",aux);
        aux = new ArrayList<> ();
        
        aux.add(45.0);
        aux.add(55.0);
        tablEvaluation.put("Rainfall",aux);
        aux = new ArrayList<> ();
        
        aux.add(55.0);
        aux.add(65.0);
        tablEvaluation.put("Typical speech",aux);
        aux = new ArrayList<> ();
        
        aux.add(65.0);
        aux.add(75.0);
        tablEvaluation.put("Washing Machine",aux);
        aux = new ArrayList<> ();
        
        aux.add(75.0);
        aux.add(85.0);
        tablEvaluation.put("Busy city traffic",aux);
        aux = new ArrayList<> ();
        
        aux.add(85.0);
        aux.add(95.0);
        tablEvaluation.put("Gas mower or hair dryer",aux);
        aux = new ArrayList<> ();
        
        aux.add(95.0);
        aux.add(105.0);
        tablEvaluation.put("Walkman or tractor",aux);
        aux = new ArrayList<> ();
        
        aux.add(105.0);
        aux.add(115.0);
        tablEvaluation.put("Leaf blower or rock concert or chainsaw",aux);
        aux = new ArrayList<> ();
        
        aux.add(155.0);
        aux.add(125.0);
        tablEvaluation.put("Ambulance or jack hammer",aux);
        aux = new ArrayList<> ();
        
        aux.add(125.0);
        aux.add(135.0);
        tablEvaluation.put("Jet plane (from 100 ft.)",aux);
        aux = new ArrayList<> ();
        
        aux.add(135.0);
        aux.add(145.0);
        tablEvaluation.put("Fireworks or gun shot",aux);
        aux = new ArrayList<> ();
        
        aux.add(145.0);
        aux.add(165.0);
        tablEvaluation.put("12-gauge shotgun",aux);
        aux = new ArrayList<> ();
        
        aux.add(165.0);
        tablEvaluation.put("Rocket Launch",aux);
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
    
    
    public double evaluateExposure(double decibel){
        if(decibel > 130.0){ return tablExposure.get(130); }
        if(decibel < 85.0){ return tablExposure.get(85); }
        return tablExposure.get((((int)decibel - 85) / 3) * 4 + 85);
    }
        
}
