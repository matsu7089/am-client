package jp.nao.amclient;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.*;

/** Keep decoding through PCM even if an HDMI/Bluetooth output advertises encoded playback. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NormalizationAudioSink extends ForwardingAudioSink {
    NormalizationAudioSink(Context context,NormalizationAudioProcessor processor){
        super(new DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setAudioProcessors(new AudioProcessor[]{processor}).build());
    }
    @Override public boolean supportsFormat(Format format){return getFormatSupport(format)!=SINK_FORMAT_UNSUPPORTED;}
    @Override public int getFormatSupport(Format format){return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)?super.getFormatSupport(format):SINK_FORMAT_UNSUPPORTED;}
    @Override public AudioOffloadSupport getFormatOffloadSupport(Format format){return AudioOffloadSupport.DEFAULT_UNSUPPORTED;}
}
