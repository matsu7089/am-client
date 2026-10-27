package jp.nao.amclient;

import android.content.Context;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Legacy metadata repair uses synthetic accounts, preserving audio, order and membership. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ArtworkVerification {
    static void run(Context c)throws Exception{
        String scope="offline-deletion-artwork",other="offline-deletion-artwork-other";
        File index=new File(c.getFilesDir(),"offline/"+scope+"/collections/Playlist-delete-playlist.json");
        try{
            OfflineDeletionVerification.clear(c,scope);OfflineDeletionVerification.clear(c,other);
            OfflineDeletionVerification.Fixture first=new OfflineDeletionVerification.Fixture(c,scope),second=new OfflineDeletionVerification.Fixture(c,other);
            JSONObject legacy=new JSONObject(OfflineStore.read(index));legacy.getJSONObject("collection").remove("artworkVersion");
            legacy.getJSONObject("collection").put("images",new JSONArray().put(new JSONObject().put("url","https://example.org/logo.jpg").put("width",300)));
            Files.write(index.toPath(),legacy.toString().getBytes(StandardCharsets.UTF_8));
            String id=OfflineDeletionVerification.PLAYLIST_ONLY;File complete=new File(first.store.manifestFile(id).getParentFile(),"complete.json");
            JSONObject original=first.store.get(id);original.remove("originArtworkVersion");original.put("originArtwork","https://example.org/logo.jpg");Files.write(complete.toPath(),original.toString().getBytes(StandardCharsets.UTF_8));
            File manifest=new File(complete.getParentFile(),SavedTrack.manifestName(original));byte[] audioMetadata=Files.readAllBytes(manifest.toPath());
            JSONObject older=new JSONObject(legacy.getJSONObject("collection").toString()).put("playedAt",10),newer=new JSONObject().put("__typename","Album").put("id","album").put("playedAt",20);
            c.getSharedPreferences("playback-history-"+scope,0).edit().putString("items",new JSONArray().put(newer).put(older).toString()).commit();
            MusicEntity fresh=new MusicEntity(new JSONObject().put("__typename","Playlist").put("id","delete-playlist").put("images",new JSONArray().put(new JSONObject().put("url","https://example.org/cover.jpg").put("width",500).put("height",500).put("imageType","PRIMARY"))));
            HistoryStore history=new HistoryStore(c,scope);long network=OfflineStore.networkAudioBytes();
            if(!first.store.refreshArtwork(fresh)||!history.refreshArtwork(fresh))throw new AssertionError("Legacy artwork not repaired");
            JSONObject updated=first.store.get(id);for(Iterator<String> keys=original.keys();keys.hasNext();){String key=keys.next();if(!"originArtwork".equals(key)&&!original.get(key).toString().equals(updated.get(key).toString()))throw new AssertionError("Saved audio metadata changed: "+key);}
            if(!Arrays.equals(audioMetadata,Files.readAllBytes(manifest.toPath()))||first.store.list().size()!=3||second.store.list().size()!=3||OfflineStore.networkAudioBytes()!=network)throw new AssertionError("Repair altered saved audio or another account");
            JSONObject repaired=new JSONObject(OfflineStore.read(index));if(!legacy.getJSONArray("trackIds").toString().equals(repaired.getJSONArray("trackIds").toString())||!fresh.artworkUrl.equals(new MusicEntity(repaired.getJSONObject("collection")).artworkUrl))throw new AssertionError("Collection membership or artwork incorrect");
            JSONArray entries=new JSONArray(c.getSharedPreferences("playback-history-"+scope,0).getString("items","[]"));
            if(entries.getJSONObject(0).getLong("playedAt")!=20||entries.getJSONObject(1).getLong("playedAt")!=10||!"album".equals(history.list().get(0).id)||!fresh.artworkUrl.equals(history.list().get(1).artworkUrl))throw new AssertionError("History order or timestamps changed");
            if(first.store.refreshArtwork(fresh)||history.refreshArtwork(fresh))throw new AssertionError("Already repaired artwork written again");
            first.store.removeCollection(first.playlist);history.remove(fresh);first.store.refreshArtwork(fresh);history.refreshArtwork(fresh);
            if(index.exists()||!first.store.collections("Playlist").isEmpty()||history.list().size()!=1)throw new AssertionError("Late artwork response recreated deleted content");
        }finally{
            OfflineDeletionVerification.clear(c,scope);OfflineDeletionVerification.clear(c,other);c.getSharedPreferences("playback-history-"+scope,0).edit().clear().commit();
        }
    }
}
