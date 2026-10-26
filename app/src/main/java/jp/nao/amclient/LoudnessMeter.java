package jp.nao.amclient;

/** Mono/stereo K-weighted loudness. 400 ms windows, 100 ms steps, -70 LUFS / -10 LU gates.
 * The histogram bounds memory for long sessions; relative gating has 0.1 LU resolution.
 * Only energy statistics are retained, never decoded audio.
 */
final class LoudnessMeter {
    private final long[] counts=new long[1001];
    private final double[] energies=new double[1001],recent=new double[4];
    private Filter[] shelf,highPass;
    private int sampleRate,channels,stepFrames,frames,steps,slot;
    private double sum,absoluteEnergy;
    private long absoluteCount;
    private double integrated=Double.NaN;
    void configure(int rate,int channelCount){
        sampleRate=rate;channels=channelCount;stepFrames=Math.max(1,rate/10);
        shelf=new Filter[channels];highPass=new Filter[channels];
        for(int c=0;c<channels;c++){shelf[c]=Filter.shelf(rate);highPass[c]=Filter.highPass(rate);}
        frames=steps=slot=0;sum=0;java.util.Arrays.fill(recent,0);
    }
    boolean frame(double left,double right){
        double a=highPass[0].process(shelf[0].process(left));sum+=a*a;
        if(channels==2){double b=highPass[1].process(shelf[1].process(right));sum+=b*b;}
        if(++frames<stepFrames)return false;
        recent[slot]=sum/frames;slot=(slot+1)%4;frames=0;sum=0;
        if(++steps<4)return false;
        double energy=(recent[0]+recent[1]+recent[2]+recent[3])/4;
        double level=lufs(energy);
        if(level>=-70&&Double.isFinite(level)){
            int bin=Math.max(0,Math.min(counts.length-1,(int)Math.floor((level+70)*10)));
            counts[bin]++;energies[bin]+=energy;absoluteCount++;absoluteEnergy+=energy;
            double threshold=absoluteEnergy/absoluteCount*0.1,total=0;long count=0;
            for(int i=0;i<counts.length;i++)if(counts[i]>0&&Math.pow(10,(-70+(i+0.5)/10+0.691)/10)>=threshold){total+=energies[i];count+=counts[i];}
            integrated=count==0?Double.NaN:lufs(total/count);
        }
        return true;
    }
    double integrated(){return integrated;}
    double measuredSeconds(){return absoluteCount*0.1;}
    static double lufs(double energy){return energy>0?-0.691+10*Math.log10(energy):Double.NaN;}
    private static final class Filter {
        final double b0,b1,b2,a1,a2;double z1,z2;
        Filter(double b0,double b1,double b2,double a1,double a2){this.b0=b0;this.b1=b1;this.b2=b2;this.a1=a1;this.a2=a2;}
        double process(double x){double y=b0*x+z1;z1=b1*x-a1*y+z2;z2=b2*x-a2*y;return y;}
        static Filter shelf(int rate){
            double k=Math.tan(Math.PI*1681.974450955533/rate),q=0.7071752369554196;
            double vh=Math.pow(10,3.999843853973347/20),vb=Math.pow(vh,0.4996667741545416),a0=1+k/q+k*k;
            return new Filter((vh+vb*k/q+k*k)/a0,2*(k*k-vh)/a0,(vh-vb*k/q+k*k)/a0,2*(k*k-1)/a0,(1-k/q+k*k)/a0);
        }
        static Filter highPass(int rate){
            double k=Math.tan(Math.PI*38.13547087602444/rate),q=0.5003270373238773,a0=1+k/q+k*k;
            return new Filter(1,-2,1,2*(k*k-1)/a0,(1-k/q+k*k)/a0);
        }
    }
}
