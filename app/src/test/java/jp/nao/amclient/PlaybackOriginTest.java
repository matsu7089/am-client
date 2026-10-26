package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackOriginTest {
    private MusicEntity collection(String type,String id)throws Exception{return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("title",id));}
    private MusicEntity track(String album)throws Exception{return new MusicEntity(new JSONObject().put("__typename","Track").put("id","same-song").put("album",new JSONObject().put("__typename","Album").put("id",album)));}
    @Test public void playlistPlaybackOpensThePlaylistInsteadOfTheTracksAlbum()throws Exception{
        PlaybackOrigin target=PlaybackOrigin.target(PlaybackOrigin.of(collection("Playlist","playlist")),track("song-album"));
        assertEquals("Playlist",target.type);assertEquals("playlist",target.id);
    }
    @Test public void albumPlaybackKeepsTheSelectedAlbumWhenTheServerSelectsRelatedSongs()throws Exception{
        PlaybackOrigin origin=PlaybackOrigin.of(collection("Album","selected-album"));
        assertEquals("selected-album",PlaybackOrigin.target(origin,track("other-album")).id);
        assertEquals("Album",PlaybackOrigin.target(origin,null).type);
    }
    @Test public void differentPlaylistsContainingTheSameSongHaveDifferentDestinations()throws Exception{
        MusicEntity song=track("song-album");PlaybackOrigin first=PlaybackOrigin.of(collection("Playlist","first")),second=PlaybackOrigin.of(collection("Playlist","second"));
        assertEquals("first",PlaybackOrigin.target(first,song).id);assertEquals("second",PlaybackOrigin.target(second,song).id);assertEquals("first",first.id);
    }
    @Test public void standaloneTracksAndArtistsFallBackWithoutKeepingAnOldCollection()throws Exception{
        assertNull(PlaybackOrigin.of(collection("Artist","artist")));assertNull(PlaybackOrigin.of(collection("Track","track")));assertNull(PlaybackOrigin.of(collection("Album","")));
        PlaybackOrigin target=PlaybackOrigin.target(null,track("current-album"));assertEquals("Album",target.type);assertEquals("current-album",target.id);
        assertNull(PlaybackOrigin.target(null,null));assertNull(PlaybackOrigin.target(null,new MusicEntity(new JSONObject().put("__typename","Track").put("id","missing-album"))));
    }
}
