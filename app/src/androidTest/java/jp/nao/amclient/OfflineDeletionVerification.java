package jp.nao.amclient;

import android.content.Context;
import android.net.Uri;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import org.json.*;
import java.io.File;
import java.util.*;

/** Uses synthetic manifests and local bytes in isolated accounts; no user downloads or HTTP. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class OfflineDeletionVerification {
    static final String SHARED="delete-test-shared",ALBUM_ONLY="delete-test-album",PLAYLIST_ONLY="delete-test-playlist";
    static final String[] IDS={SHARED,ALBUM_ONLY,PLAYLIST_ONLY};
    static final class Fixture{
        final OfflineStore store;final MusicEntity album,playlist;
        Fixture(Context c,String scope)throws Exception{
            store=new OfflineStore(c,scope);album=entity("Album","delete-album","削除テストアルバム");playlist=entity("Playlist","delete-playlist","削除テストプレイリスト");
            MusicEntity shared=entity("Track",SHARED,SHARED),a=entity("Track",ALBUM_ONLY,ALBUM_ONLY),p=entity("Track",PLAYLIST_ONLY,PLAYLIST_ONLY);
            store.rememberCollection(album,Arrays.asList(shared,a));store.rememberCollection(playlist,Arrays.asList(shared,p));
            for(MusicEntity track:Arrays.asList(shared,a,p)){
                String variant=store.newVariant();DashPackage dash=new DashPackage(QualityVerification.manifest(),AudioQuality.HIGH);store.stage(track.id,variant,dash.xml);store.commit(track,track==p?playlist:album,4096,dash,variant);
                CacheDataSource data=store.streamSource(c,track.id,variant,true).setUpstreamDataSourceFactory(()->new ByteArrayDataSource(new byte[4096])).createDataSource();
                try{data.open(new DataSpec(Uri.parse("https://example.org/deletion-fixture")));byte[] bytes=new byte[4096];while(data.read(bytes,0,bytes.length)!=-1){}}finally{data.close();}
            }
        }
    }
    static void run(Context c)throws Exception{
        String scope="offline-deletion-verification",other="offline-deletion-other";OfflineStore.SavedAudio pinned=null;long bytes=OfflineStore.networkAudioBytes();
        try{
            clear(c,scope);clear(c,other);Fixture f=new Fixture(c,scope),untouched=new Fixture(c,other);Cache cache=OfflineStore.dataSource(c,false).getCache();
            OfflineDownloadService.active=true;try{f.store.removeCollection(f.album);throw new AssertionError("Deletion allowed during download");}catch(java.io.IOException expected){}finally{OfflineDownloadService.active=false;}
            check(f.store.hasStream(ALBUM_ONLY),"Rejected deletion removed audio");
            SavedCollections.Removal removed=f.store.removeCollection(f.album);check(removed.delete.equals(Collections.singleton(ALBUM_ONLY))&&removed.shared.equals(Collections.singleton(SHARED)),"Incorrect shared deletion plan");
            check(!f.store.hasStream(ALBUM_ONLY)&&f.store.hasStream(SHARED)&&f.store.hasStream(PLAYLIST_ONLY),"Shared or unrelated saved audio was removed");
            check(!has(cache,scope,ALBUM_ONLY)&&has(cache,scope,SHARED),"Encrypted cache deletion did not match membership");
            check(f.store.collections("Album").isEmpty()&&f.store.collections("Playlist").size()==1,"Deleted album reappeared from fallback origin");
            check("delete-playlist".equals(f.store.get(SHARED).getString("originId"))&&SavedTrack.quality(f.store.get(SHARED))==AudioQuality.HIGH,"Reassignment damaged shared audio metadata");
            pinned=f.store.open(PLAYLIST_ONLY);File manifest=new File(f.store.manifestFile(PLAYLIST_ONLY).getParentFile(),SavedTrack.manifestName(f.store.get(PLAYLIST_ONLY)));
            f.store.removeCollection(f.playlist);check(!f.store.hasStream(PLAYLIST_ONLY)&&manifest.isFile()&&has(cache,scope,PLAYLIST_ONLY),"Active reader was interrupted or deletion marker retained");
            check(!has(cache,scope,SHARED)&&f.store.collections("Playlist").isEmpty(),"Final collection deletion retained unshared audio");
            pinned.close();pinned=null;check(!manifest.exists()&&!has(cache,scope,PLAYLIST_ONLY),"Deferred cache removal did not finish after reader closed");
            check(untouched.store.list().size()==3&&has(cache,other,SHARED)&&has(cache,other,ALBUM_ONLY),"Another account was modified");
            File interrupted=untouched.store.manifestFile(ALBUM_ONLY).getParentFile();java.nio.file.Files.write(new File(interrupted,"pending-delete").toPath(),new byte[]{1});java.nio.file.Files.delete(new File(interrupted,"complete.json").toPath());
            new OfflineStore(c,other).list();check(!has(cache,other,ALBUM_ONLY)&&!interrupted.exists(),"Interrupted deletion was not resumed when reopening saved list");
            check(bytes==OfflineStore.networkAudioBytes(),"Fixture used media network");
        }finally{OfflineDownloadService.active=false;if(pinned!=null)pinned.close();clear(c,scope);clear(c,other);}
    }
    static boolean has(Cache cache,String scope,String id){String prefix=SavedTrack.cachePrefix(scope,id);for(String key:cache.getKeys())if(key.startsWith(prefix))return true;return false;}
    static void clear(Context c,String scope)throws Exception{if(!scope.startsWith("offline-deletion-"))throw new IllegalArgumentException("Invalid fixture scope");OfflineStore store=new OfflineStore(c,scope);for(String id:IDS)store.removeStream(c,id);delete(new File(c.getFilesDir(),"offline/"+scope));}
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
    static MusicEntity entity(String type,String id,String name)throws JSONException{return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("title",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
