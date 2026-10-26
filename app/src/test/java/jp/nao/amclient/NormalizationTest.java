package jp.nao.amclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class NormalizationTest {
    private static double tone(NormalizationEngine engine,double amplitude,int rate,int channels,int seconds){
        double sum=0;int count=0;
        for(int i=0;i<rate*seconds;i++){
            double sample=amplitude*Math.sin(2*Math.PI*1000*i/rate);
            double gain=engine.frame(sample,channels==2?sample:0);
            if(i>=rate*(seconds-1)){sum+=sample*sample*gain*gain;count++;}
        }
        return 10*Math.log10(sum/count);
    }
    @Test public void kWeightingCalibratesAt48And44k(){
        for(int rate:new int[]{48000,44100}){
            NormalizationEngine engine=new NormalizationEngine(rate,2,false,-20,Double.NaN);
            tone(engine,Math.pow(10,-23.0/20),rate,2,6);
            assertEquals("1 kHz stereo reference at "+rate,-23,engine.measured(),0.1);
        }
    }
    @Test public void differentTracksReachTheSameLoudness(){
        double loud=tone(new NormalizationEngine(48000,2,true,-20,Double.NaN),0.4,48000,2,16);
        double quiet=tone(new NormalizationEngine(48000,2,true,-20,Double.NaN),0.04,48000,2,16);
        assertEquals(loud,quiet,0.2);assertEquals(-23,loud,0.2); // stereo LUFS vs one-channel RMS
    }
    @Test public void lowerTargetMakesPlaybackSixDbQuieter(){
        double normal=tone(new NormalizationEngine(48000,2,true,-20,Double.NaN),0.25,48000,2,12);
        double quiet=tone(new NormalizationEngine(48000,2,true,-26,Double.NaN),0.25,48000,2,12);
        assertEquals(6,normal-quiet,0.1);
    }
    @Test public void silenceAndSubGateNoiseDoNotCreateABoost(){
        NormalizationEngine engine=new NormalizationEngine(48000,2,true,-20,Double.NaN);
        for(int i=0;i<48000*10;i++)assertTrue(Double.isFinite(engine.frame(0,0)));
        assertTrue(Double.isNaN(engine.measured()));assertEquals(0,engine.measuredSeconds(),0);
        tone(engine,0.00001,48000,2,3);assertTrue(Double.isNaN(engine.measured()));assertTrue(engine.gainDb()<0);
    }
    @Test public void gatesIgnoreSilenceWithinATrack(){
        NormalizationEngine engine=new NormalizationEngine(48000,2,true,-20,Double.NaN);
        tone(engine,0.2,48000,2,8);double measured=engine.measured();
        for(int i=0;i<48000*8;i++)engine.frame(0,0);
        tone(engine,0.2,48000,2,8);assertEquals(measured,engine.measured(),0.2);
    }
    @Test public void transientPeaksAreLimitedWithoutChangingStereoBalance(){
        NormalizationEngine engine=new NormalizationEngine(48000,2,true,-14,-26);
        for(int i=0;i<48000;i++)engine.frame(0.01,0.02);
        double gain=engine.frame(0.5,1);
        assertTrue(gain<=NormalizationEngine.CEILING);assertTrue(gain>0);
        double left=0.5*gain,right=gain;assertEquals(0.5,left/right,0);
        for(int i=0;i<48000;i++){gain=engine.frame(1,-1);assertTrue(Math.abs(gain)<=NormalizationEngine.CEILING+1e-10);}
    }
    @Test public void knownTrackStartsWithItsOwnGainAndSeekingKeepsIt(){
        NormalizationEngine engine=new NormalizationEngine(48000,2,true,-26,-16);
        assertEquals(-10,engine.gainDb(),0.001);tone(engine,0.25,48000,2,5);
        double measured=engine.measured();engine.configure(48000,2);
        assertEquals(measured,engine.measured(),0);assertEquals(-10,engine.gainDb(),0.001);
        NormalizationEngine next=new NormalizationEngine(48000,2,true,-26,-26);assertEquals(0,next.gainDb(),0.001);assertTrue(Double.isNaN(next.measured()));
    }
    @Test public void disabledIsTransparentAndLiveSettingsConverge(){
        NormalizationEngine engine=new NormalizationEngine(48000,2,false,-20,-10);
        assertEquals(1,engine.frame(0.9,-0.8),0);
        engine.settings(true,-26);tone(engine,0.1,48000,2,5);assertEquals(-16,engine.gainDb(),0.01);
        engine.settings(false,-26);tone(engine,0.1,48000,2,40);assertEquals(1,engine.frame(0.8,-0.9),1e-8);
    }
    @Test public void amplificationAndSettingBoundsAreLimited(){
        assertEquals(-30,NormalizationEngine.clampTarget(-100));assertEquals(-14,NormalizationEngine.clampTarget(0));
        NormalizationEngine engine=new NormalizationEngine(48000,2,true,-14,-69);assertEquals(12,engine.gainDb(),0.001);
        assertFalse(NormalizationEngine.validLevel(Double.NaN));assertFalse(NormalizationEngine.validLevel(Double.POSITIVE_INFINITY));
    }
    @Test public void monoAndStereoBothReachTheirTarget(){
        NormalizationEngine mono=new NormalizationEngine(48000,1,true,-20,Double.NaN),stereo=new NormalizationEngine(48000,2,true,-20,Double.NaN);
        double m=tone(mono,0.2,48000,1,16),s=tone(stereo,0.2,48000,2,16);
        assertEquals(3.0103,m-s,0.1);assertEquals(-20,m,0.2);
    }
}
