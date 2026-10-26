package jp.nao.amclient;

import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.dash.manifest.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class DashPackage {
    public final DashManifest manifest;
    public final Format format;
    public final StreamKey streamKey;
    public final String xml;
    public final AudioQuality quality;
    public final AudioQuality requested;
    public DashPackage(String xml) throws IOException {
        this(xml,AudioQuality.LOW,null);
    }
    public DashPackage(String xml,AudioQuality requested) throws IOException {this(xml,requested,null);}
    public DashPackage(String xml,AudioQuality requested,StreamKey saved) throws IOException {
        this.xml=xml;
        this.requested=requested;
        DashManifest original=new DashManifestParser().parse(Uri.parse(NavigationPolicy.HOME),new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Map<String,AudioQuality> rankings=rankings(xml);
        int group=-1,track=-1;
        List<Integer> groups=new ArrayList<>(),tracks=new ArrayList<>(),bandwidths=new ArrayList<>();
        List<AudioQuality> levels=new ArrayList<>();
        for(int a=0;a<original.getPeriod(0).adaptationSets.size();a++) {
            AdaptationSet set=original.getPeriod(0).adaptationSets.get(a);
            if(set.type!=C.TRACK_TYPE_AUDIO)continue;
            String type="";for(Descriptor property:set.supplementalProperties)if("amz-music:trackType".equals(property.schemeIdUri))type=property.value;
            // Only SD is selectable here. LD is for a different entitlement, FLAC requires HD rights.
            if(saved==null&&!"SD".equals(type))continue;
            for(int r=0;r<set.representations.size();r++) {
                Format f=set.representations.get(r).format;
                if(f.codecs==null||!f.codecs.toLowerCase(Locale.ROOT).contains("opus"))continue;
                if(saved!=null){if(saved.periodIndex==0&&saved.groupIndex==a&&saved.streamIndex==r){group=a;track=r;}continue;}
                // The SD set is authoritative; do not mix its levels with LD representations.
                if(group<0)group=a;
                if(a==group){groups.add(a);tracks.add(r);bandwidths.add(f.bitrate);levels.add(rankings.getOrDefault(a+":"+r,AudioQuality.fromBandwidth(f.bitrate)));}
            }
        }
        if(saved==null&&!bandwidths.isEmpty()){int index=AudioQuality.select(bandwidths,levels,requested);group=groups.get(index);track=tracks.get(index);}
        if(group<0)throw new IOException("対応する音声形式がありません");
        if(track<0)throw new IOException("保存した音質の情報を確認できません");
        streamKey=new StreamKey(0,group,track); manifest=original.copy(Collections.singletonList(streamKey));
        format=manifest.getPeriod(0).adaptationSets.get(0).representations.get(0).format;
        quality=rankings.getOrDefault(group+":"+track,AudioQuality.fromBandwidth(format.bitrate));
        if(format.drmInitData==null)throw new IOException("DRM 情報を確認できません");
    }
    private static Map<String,AudioQuality> rankings(String xml)throws IOException{
        Map<String,AudioQuality> result=new HashMap<>();
        try{
            XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(new StringReader(xml));int period=-1,group=-1,stream=-1;
            for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next())if(event==XmlPullParser.START_TAG){
                String name=parser.getName();if("Period".equals(name)){if(++period>0)break;}
                else if(period==0&&"AdaptationSet".equals(name)){group++;stream=-1;}
                else if(period==0&&"Representation".equals(name)){stream++;String rank=parser.getAttributeValue(null,"qualityRanking");
                    if("1".equals(rank))result.put(group+":"+stream,AudioQuality.HIGH);else if("2".equals(rank))result.put(group+":"+stream,AudioQuality.MEDIUM);else if("3".equals(rank))result.put(group+":"+stream,AudioQuality.LOW);}
            }
        }catch(Exception e){throw new IOException("音質の情報を読み込めません",e);}return result;
    }
}
