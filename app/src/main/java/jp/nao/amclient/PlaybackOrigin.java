package jp.nao.amclient;

/** The collection that started this queue, independent of each track's own album. */
final class PlaybackOrigin {
    final String type,id;
    private PlaybackOrigin(String type,String id){this.type=type;this.id=id;}
    static PlaybackOrigin of(MusicEntity collection){
        return collection==null?null:of(collection.type,collection.id);
    }
    static PlaybackOrigin of(String type,String id){return id==null||id.isEmpty()||!("Album".equals(type)||"Playlist".equals(type))?null:new PlaybackOrigin(type,id);}
    static PlaybackOrigin target(PlaybackOrigin origin,MusicEntity track){
        return origin!=null?origin:of(track==null?null:track.album);
    }
}
