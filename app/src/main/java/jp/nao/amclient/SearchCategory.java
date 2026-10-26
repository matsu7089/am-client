package jp.nao.amclient;

import java.util.*;

public enum SearchCategory {
    TRACKS("楽曲","Track"), ALBUMS("アルバム","Album"), PLAYLISTS("プレイリスト","Playlist"), ARTISTS("アーティスト","Artist");
    public final String label,type;
    SearchCategory(String label,String type){this.label=label;this.type=type;}
    public List<MusicEntity> filter(List<MusicEntity> items){
        List<MusicEntity> result=new ArrayList<>();for(MusicEntity item:items)if(type.equals(item.type))result.add(item);return result;
    }
}
