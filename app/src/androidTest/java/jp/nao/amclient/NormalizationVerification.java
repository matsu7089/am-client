package jp.nao.amclient;

import android.content.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.*;
import java.util.UUID;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NormalizationVerification {
    static void run(Context context)throws Exception{
        NormalizationAudioProcessor processor=new NormalizationAudioProcessor();
        processor.track("fixture-loud",true,-20,Double.NaN);
        AudioProcessor.AudioFormat format=new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT);
        processor.configure(format);processor.flush();double outputEnergy=0;
        for(int second=0;second<36;second++){
            ByteBuffer input=ByteBuffer.allocateDirect(48000*4).order(ByteOrder.nativeOrder());
            for(int frame=0;frame<48000;frame++){short value=(short)Math.round(0.4*32768*Math.sin(2*Math.PI*1000*frame/48000));input.putShort(value).putShort(value);}input.flip();processor.queueInput(input);
            ByteBuffer output=processor.getOutput().order(ByteOrder.nativeOrder());
            if(output.remaining()!=48000*4||input.hasRemaining())throw new AssertionError("PCM frame count changed");
            for(int frame=0;frame<48000;frame++){short left=output.getShort(),right=output.getShort();if(left!=right)throw new AssertionError("Stereo balance changed");if(second==35)outputEnergy+=(double)left*left/(32768.0*32768);}
        }
        if(Math.abs(10*Math.log10(outputEnergy/48000)+23)>0.2)throw new AssertionError("PCM output missed target");
        NormalizationAudioProcessor.Snapshot snapshot=processor.snapshot();
        if(snapshot.frames!=36L*48000||!"fixture-loud".equals(snapshot.id))throw new AssertionError("Measurement belongs to wrong track");
        String name="verify-loudness-"+UUID.randomUUID();Context isolated=new ContextWrapper(context){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return context.getSharedPreferences(name,mode);}};
        try{
            LoudnessStore store=new LoudnessStore(isolated);store.save(snapshot);
            if(!NormalizationEngine.validLevel(new LoudnessStore(isolated).get(snapshot.id))||!Double.isNaN(store.get("missing")))throw new AssertionError("Level cache persistence failed");
            processor.queueEndOfStream();if(!processor.isEnded())throw new AssertionError("EOS did not drain");processor.flush();
            processor.track("fixture-off",false,-26,Double.NaN);
            ByteBuffer input=ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());short[] samples={Short.MIN_VALUE,Short.MAX_VALUE,-12345,6789};for(short s:samples)input.putShort(s);input.flip();processor.queueInput(input);
            ByteBuffer output=processor.getOutput().order(ByteOrder.nativeOrder());for(short s:samples)if(output.getShort()!=s)throw new AssertionError("Disabled PCM is not transparent");
            if(processor.snapshot().frames!=2||processor.snapshot().enabled||processor.snapshot().target!=-26)throw new AssertionError("New track retained old measurement/settings");
            store.save(processor.snapshot());if(!Double.isNaN(store.get("fixture-off")))throw new AssertionError("Short measurement was cached");
            boolean rejected=false;try{processor.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_FLOAT));}catch(AudioProcessor.UnhandledAudioFormatException expected){rejected=true;}if(!rejected)throw new AssertionError("Unsupported PCM accepted");
        }finally{processor.reset();context.getSharedPreferences(name,0).edit().clear().commit();context.deleteSharedPreferences(name);}
    }
}
