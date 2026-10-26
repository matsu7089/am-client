package jp.nao.amclient;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NormalizationAudioProcessor extends BaseAudioProcessor {
    static final class Snapshot {
        final String id;final double lufs,seconds,gainDb;final long frames;final int sampleRate,target;final boolean enabled;
        Snapshot(String id,NormalizationEngine engine){this.id=id;lufs=engine.measured();seconds=engine.measuredSeconds();gainDb=engine.gainDb();frames=engine.frames();sampleRate=engine.sampleRate();target=engine.target();enabled=engine.enabled();}
    }
    private static final class Request {
        final String id;final boolean enabled;final int target;final double learned;
        Request(String id,boolean enabled,int target,double learned){this.id=id;this.enabled=enabled;this.target=target;this.learned=learned;}
    }
    private volatile Request request=new Request("",true,NormalizationEngine.DEFAULT_TARGET,Double.NaN);
    private Request applied;
    private NormalizationEngine engine;
    private volatile Snapshot snapshot;
    void track(String id,boolean enabled,int target,double learned){request=new Request(id,enabled,target,learned);}
    void settings(boolean enabled,int target){Request old=request;request=new Request(old.id,enabled,target,old.learned);}
    Snapshot snapshot(){return snapshot;}
    @Override protected AudioFormat onConfigure(AudioFormat format)throws UnhandledAudioFormatException{
        if(format.encoding!=C.ENCODING_PCM_16BIT||format.channelCount<1||format.channelCount>2)throw new UnhandledAudioFormatException(format);
        return format;
    }
    @Override protected void onFlush(){if(inputAudioFormat.sampleRate>0){applyRequest();engine.configure(inputAudioFormat.sampleRate,inputAudioFormat.channelCount);}}
    private void applyRequest(){
        Request next=request;
        if(engine==null||applied==null||!applied.id.equals(next.id))engine=new NormalizationEngine(inputAudioFormat.sampleRate,inputAudioFormat.channelCount,next.enabled,next.target,next.learned);
        else if(next!=applied)engine.settings(next.enabled,next.target);
        applied=next;
    }
    @Override public void queueInput(ByteBuffer input){
        applyRequest();input.order(ByteOrder.nativeOrder());ByteBuffer output=replaceOutputBuffer(input.remaining());int channels=inputAudioFormat.channelCount;
        while(input.remaining()>=channels*2){
            short left=input.getShort(),right=channels==2?input.getShort():0;
            double gain=engine.frame(left/32768.0,right/32768.0);
            output.putShort(sample(left,gain));if(channels==2)output.putShort(sample(right,gain));
        }
        output.flip();snapshot=new Snapshot(applied.id,engine);
    }
    private static short sample(short input,double gain){return (short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(input*gain)));}
}
