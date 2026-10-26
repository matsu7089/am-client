package jp.nao.amclient;

import java.util.*;

/** SD Opus qualities. Manifest bandwidth is a peak, not the nominal bitrate. */
public enum AudioQuality {
    LOW("節約",48), MEDIUM("標準",192), HIGH("高音質",320);
    public final String label;
    public final int kbps;
    AudioQuality(String label,int kbps){this.label=label;this.kbps=kbps;}
    public String description(){return label+" · "+kbps+" kbps";}
    public static AudioQuality parse(String value,AudioQuality fallback){try{return valueOf(value);}catch(Exception ignored){return fallback;}}
    public static AudioQuality fromBandwidth(int bandwidth){return bandwidth>250_000?HIGH:bandwidth>100_000?MEDIUM:LOW;}
    public static int select(List<Integer> bandwidths,AudioQuality requested){
        List<AudioQuality> levels=new ArrayList<>();for(int b:bandwidths)levels.add(fromBandwidth(b));return select(bandwidths,levels,requested);
    }
    public static int select(List<Integer> bandwidths,List<AudioQuality> levels,AudioQuality requested){
        if(bandwidths.isEmpty())throw new IllegalArgumentException("No audio representations");
        int best=-1;for(int i=0;i<bandwidths.size();i++){
            int b=bandwidths.get(i);if(b<=0)continue;
            if(levels.get(i).ordinal()<=requested.ordinal()&&(best<0||levels.get(i).ordinal()>levels.get(best).ordinal()||levels.get(i)==levels.get(best)&&b>bandwidths.get(best)))best=i;
        }
        if(best>=0)return best;
        for(int i=0;i<bandwidths.size();i++)if(bandwidths.get(i)>0&&(best<0||levels.get(i).ordinal()<levels.get(best).ordinal()||levels.get(i)==levels.get(best)&&bandwidths.get(i)<bandwidths.get(best)))best=i;
        if(best<0)throw new IllegalArgumentException("No known audio bandwidth");return best;
    }
}
