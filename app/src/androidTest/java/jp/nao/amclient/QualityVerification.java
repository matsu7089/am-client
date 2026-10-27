package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.extractor.mp4.PsshAtomUtil;
import org.json.*;
import java.io.File;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class QualityVerification {
    static void run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();OfflineStore store=new OfflineStore(c,"quality-verification");String id="quality-verification";
        String xml=manifest();DashPackage low=new DashPackage(xml,AudioQuality.LOW),mid=new DashPackage(xml,AudioQuality.MEDIUM),high=new DashPackage(xml,AudioQuality.HIGH);
        check(low.format.bitrate==53000&&mid.format.bitrate==197000&&high.format.bitrate==325000,"SD selection mixed with LD/HD");
        check(high.streamKey.groupIndex==1&&high.streamKey.streamIndex==2,"Wrong download stream key");
        DashPackage variable=new DashPackage(xml.replace("bandwidth='197000'","bandwidth='600000'"),AudioQuality.MEDIUM);
        check(variable.quality==AudioQuality.MEDIUM&&variable.format.bitrate==600000,"Peak bitrate overrode explicit quality ranking");
        MusicEntity owner=new MusicEntity(new JSONObject().put("__typename","Album").put("id",id).put("title","test"));
        MusicEntity track=new MusicEntity(new JSONObject().put("__typename","Track").put("id",id).put("title","test"));
        String first=store.newVariant(),second=store.newVariant();OfflineStore.SavedAudio lease=null;
        try{
            store.removeStream(c,id);store.stage(id,first,xml);store.commit(track,owner,111,high,first);
            lease=store.open(id);check(lease.dash.format.bitrate==325000,"Saved quality was reselected");
            try(OfflineStore.SavedAudio preferred=store.openIfSaved(id)){check(preferred!=null&&preferred.dash.quality==AudioQuality.HIGH,"Saved audio was not preferred");}
            check(store.openIfSaved("missing-audio")==null,"Missing audio silently resolved");
            check(store.matches(id,AudioQuality.HIGH)&&!store.matches(id,AudioQuality.LOW),"Skip ignores quality");
            store.stage(id,second,xml);store.discardVariant(c,id,second);
            check(first.equals(SavedTrack.variant(store.get(id)))&&store.hasStream(id),"Cancelled upgrade damaged completed audio");
            store.stage(id,second,xml);store.commit(track,owner,222,mid,second);store.cleanup(c,id);
            check(new File(store.manifestFile(id).getParentFile(),"manifest-"+first+".mpd").isFile(),"Playing source removed by replacement");
            try(OfflineStore.SavedAudio current=store.open(id)){check(current.dash.format.bitrate==197000,"Replacement was not committed");}
            lease.close();lease=null;store.cleanup(c,id);
            check(!new File(store.manifestFile(id).getParentFile(),"manifest-"+first+".mpd").exists(),"Superseded audio not cleaned up");
            store.removeStream(c,id);store.stage(id,xml);store.completeStream(track,owner,111);
            try(OfflineStore.SavedAudio legacy=store.openIfSaved(id)){check(legacy.variant.isEmpty()&&legacy.dash.format.bitrate==53000,"Legacy downloads lost compatibility");}
        }finally{if(lease!=null)lease.close();store.removeStream(c,id);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String manifest(){
        String pssh=android.util.Base64.encodeToString(PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID,new byte[0]),android.util.Base64.NO_WRAP);
        String protection="<ContentProtection schemeIdUri='urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed'><cenc:pssh>"+pssh+"</cenc:pssh></ContentProtection>";
        StringBuilder s=new StringBuilder("<MPD xmlns='urn:mpeg:dash:schema:mpd:2011' xmlns:cenc='urn:mpeg:cenc:2013' type='static' mediaPresentationDuration='PT60S' minBufferTime='PT2S'><Period>");
        for(String type:new String[]{"LD","SD","HD"}){
            s.append("<AdaptationSet contentType='audio' mimeType='audio/mp4'><SupplementalProperty schemeIdUri='amz-music:trackType' value='").append(type).append("'/>").append(protection);
            int[] rates="SD".equals(type)?new int[]{53000,197000,325000}:"LD".equals(type)?new int[]{40000,165000}:new int[]{1200000};
            for(int i=0;i<rates.length;i++)s.append("<Representation id='").append(type).append(i).append("' qualityRanking='").append("SD".equals(type)?3-i:1000).append("' codecs='").append("HD".equals(type)?"flac":"opus").append("' audioSamplingRate='48000' bandwidth='").append(rates[i]).append("'><BaseURL>https://example.org/").append(type).append(i).append(".mp4</BaseURL><SegmentBase indexRange='0-10'/></Representation>");
            s.append("</AdaptationSet>");
        }
        return s.append("</Period></MPD>").toString();
    }
}
