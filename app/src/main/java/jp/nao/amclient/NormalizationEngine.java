package jp.nao.amclient;

/** Runs on the audio thread. Gain is shared by both channels, with a sample-peak ceiling. */
final class NormalizationEngine {
    static final int DEFAULT_TARGET=-20,MIN_TARGET=-30,MAX_TARGET=-14;
    static final double CEILING=Math.pow(10,-1.0/20);
    private final LoudnessMeter meter=new LoudnessMeter();
    private final double learned;
    private boolean enabled;
    private int target,sampleRate;
    private double gain,desired,limiter=1,attack,release,limiterRelease;
    private long processedFrames;
    NormalizationEngine(int rate,int channels,boolean enabled,int target,double learned){
        this.learned=validLevel(learned)?learned:Double.NaN;
        configure(rate,channels);settings(enabled,target);gain=desired;
    }
    static int clampTarget(int target){return Math.max(MIN_TARGET,Math.min(MAX_TARGET,target));}
    static boolean validLevel(double level){return Double.isFinite(level)&&level>=-70&&level<=10;}
    void configure(int rate,int channels){sampleRate=rate;meter.configure(rate,channels);attack=1-Math.exp(-1.0/(rate*0.15));release=1-Math.exp(-1.0/(rate*2));limiterRelease=1-Math.exp(-1.0/(rate*0.1));limiter=1;}
    void settings(boolean enabled,int target){this.enabled=enabled;this.target=clampTarget(target);updateGain();}
    private void updateGain(){
        double reference=validLevel(learned)?learned:meter.measuredSeconds()>=2.5&&validLevel(meter.integrated())?meter.integrated():-10;
        // Limit amplification of very quiet recordings and attenuation of malformed measurements.
        desired=enabled?Math.pow(10,Math.max(-36,Math.min(12,target-reference))/20):1;
    }
    double frame(double left,double right){
        if(meter.frame(left,right))updateGain();
        gain+=(desired-gain)*(desired<gain?attack:release);processedFrames++;
        if(Math.abs(desired-gain)<1e-9)gain=desired;
        if(!enabled&&gain<=1)return gain;
        double peak=Math.max(Math.abs(left),Math.abs(right))*gain;
        double allowed=peak>CEILING?CEILING/peak:1;
        limiter=allowed<limiter?allowed:Math.min(allowed,limiter+(1-limiter)*limiterRelease);
        return gain*limiter;
    }
    double measured(){return meter.integrated();}
    double measuredSeconds(){return meter.measuredSeconds();}
    double gainDb(){return 20*Math.log10(gain*(enabled||gain>1?limiter:1));}
    long frames(){return processedFrames;}
    int sampleRate(){return sampleRate;}
    boolean enabled(){return enabled;}
    int target(){return target;}
}
