package jp.nao.amclient;

import android.content.Context;
import android.net.Uri;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.common.StreamKey;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class OfflineStore {
    private static SimpleCache cache;
    private static final AtomicLong networkAudioBytes=new AtomicLong();
    private final File root;
    private static final Map<String,Integer> readers=new HashMap<>();
    public OfflineStore(Context context) {
        String account=context.getSharedPreferences("account",0).getString("scope","signed-out");
        root=new File(context.getFilesDir(),"offline/"+account);root.mkdirs();
    }
    OfflineStore(Context context,String scope){root=new File(context.getFilesDir(),"offline/"+scope);root.mkdirs();}
    public static String scope(String customer) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(customer.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format(Locale.ROOT,"%02x",v));return b.toString();
    }
    public static synchronized CacheDataSource.Factory dataSource(Context context,boolean online) {
        if(cache==null)cache=new SimpleCache(new File(context.getFilesDir(),"encrypted-cache"),new NoOpCacheEvictor(),new StandaloneDatabaseProvider(context));
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(online
            ?networkSource(context):null);
    }
    public static DefaultDataSource.Factory networkSource(Context context){
        return new DefaultDataSource.Factory(context,new DefaultHttpDataSource.Factory().setUserAgent("AMClient/"+BuildConfig.VERSION_NAME).setConnectTimeoutMs(15000).setReadTimeoutMs(25000)
            .setTransferListener(new TransferListener(){
                @Override public void onTransferInitializing(DataSource source,DataSpec spec,boolean network){}
                @Override public void onTransferStart(DataSource source,DataSpec spec,boolean network){}
                @Override public void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int bytes){if(network)networkAudioBytes.addAndGet(bytes);}
                @Override public void onTransferEnd(DataSource source,DataSpec spec,boolean network){}
            }));
    }
    public static long networkAudioBytes(){return networkAudioBytes.get();}
    public CacheDataSource.Factory streamSource(Context context,String id,boolean online){
        return streamSource(context,id,"",online);
    }
    public CacheDataSource.Factory streamSource(Context context,String id,String variant,boolean online){
        String prefix=prefix(id,variant);
        return dataSource(context,online).setCacheKeyFactory(spec->prefix+CacheKeyFactory.DEFAULT.buildCacheKey(spec));
    }
    private String prefix(String id,String variant){
        if(!variant.isEmpty()&&!variant.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid audio variant");
        return SavedTrack.cachePrefix(root.getName(),id)+(variant.isEmpty()?"":variant+"/");
    }
    final class SavedAudio implements AutoCloseable {
        final DashPackage dash;final String variant;private final String prefix;private boolean closed;
        SavedAudio(DashPackage dash,String variant,String prefix){this.dash=dash;this.variant=variant;this.prefix=prefix;readers.put(prefix,readers.getOrDefault(prefix,0)+1);}
        @Override public void close(){synchronized(OfflineStore.class){if(closed)return;closed=true;int count=readers.getOrDefault(prefix,1)-1;if(count==0)readers.remove(prefix);else readers.put(prefix,count);}}
    }
    public SavedAudio open(String id)throws Exception{synchronized(OfflineStore.class){JSONObject item=get(id);return new SavedAudio(dash(id,item),SavedTrack.variant(item),prefix(id,SavedTrack.variant(item)));}}
    public SavedAudio openIfSaved(String id)throws Exception{synchronized(OfflineStore.class){return hasStream(id)?open(id):null;}}
    public boolean matches(String id,AudioQuality quality){try{return hasStream(id)&&SavedTrack.requested(get(id))==quality;}catch(Exception ignored){return false;}}
    public String newVariant(){return UUID.randomUUID().toString().replace("-","");}
    public File stage(String id,String variant,String xml)throws IOException{File dir=directory(id);dir.mkdirs();File file=new File(dir,"manifest-"+variant+".mpd");prefix(id,variant);write(file,xml);return file;}
    public void commit(MusicEntity track,MusicEntity owner,long bytes,DashPackage dash,String variant)throws Exception{
        synchronized(OfflineStore.class){
            prefix(track.id,variant);JSONObject metadata=SavedTrack.metadata(track,owner,bytes);
            // Preserve a more useful on-demand origin when replacing an existing download.
            if(hasStream(track.id)){JSONObject old=get(track.id);if(old.optBoolean("originSelectable")&&!metadata.optBoolean("originSelectable"))
                for(String key:new String[]{"originType","originId","originTitle","originArtwork","originSelectable"})metadata.put(key,old.get(key));}
            metadata.put("audioVariant",variant).put("audioQuality",dash.quality.name()).put("requestedQuality",dash.requested.name())
                .put("audioGroup",dash.streamKey.groupIndex).put("audioStream",dash.streamKey.streamIndex)
                .put("audioBitrate",dash.format.bitrate).put("audioSampleRate",dash.format.sampleRate);
            if(!new File(directory(track.id),SavedTrack.manifestName(metadata)).isFile())throw new IOException("保存する音源の情報がありません");
            write(new File(directory(track.id),"complete.json"),metadata.toString());
        }
    }
    public void discardVariant(Context context,String id,String variant)throws Exception{
        synchronized(OfflineStore.class){String prefix=prefix(id,variant);if(readers.containsKey(prefix))return;dataSource(context,false);
            for(String key:new ArrayList<>(cache.getKeys()))if(key.startsWith(prefix))cache.removeResource(key);
            new File(directory(id),"manifest-"+variant+".mpd").delete();}
    }
    public void cleanup(Context context,String id)throws Exception{
        synchronized(OfflineStore.class){dataSource(context,false);String keep="";boolean legacy=false;
            if(hasStream(id)){String v=SavedTrack.variant(get(id));keep=prefix(id,v);legacy=v.isEmpty();}
            String base=SavedTrack.cachePrefix(root.getName(),id);final String current=keep;
            for(String key:new ArrayList<>(cache.getKeys()))if(key.startsWith(base)){
                String tail=key.substring(base.length());String variant=tail.matches("^[a-f0-9]{32}/.*")?tail.substring(0,32):"";String candidate=prefix(id,variant);
                if(!candidate.equals(current)&&!readers.containsKey(candidate))cache.removeResource(key);
            }
            File[] files=directory(id).listFiles();if(files!=null)for(File file:files){String name=file.getName();
                String v=name.matches("manifest-[a-f0-9]{32}\\.mpd")?name.substring(9,41):null;
                if(v!=null&&!prefix(id,v).equals(keep)&&!readers.containsKey(prefix(id,v)))file.delete();
                else if("manifest.mpd".equals(name)&&!legacy&&!readers.containsKey(base))file.delete();
            }
        }
    }
    public void completeStream(MusicEntity track,MusicEntity owner,long bytes)throws Exception{write(new File(directory(track.id),"complete.json"),SavedTrack.metadata(track,owner,bytes).toString());}
    public void refreshStreamContext(MusicEntity track,MusicEntity owner)throws Exception{synchronized(OfflineStore.class){write(new File(directory(track.id),"complete.json"),SavedTrack.refreshContext(get(track.id),track,owner).toString());}}
    public boolean hasStream(String id){try{JSONObject item=get(id);return SavedTrack.isStream(item)&&new File(directory(id),SavedTrack.manifestName(item)).isFile();}catch(Exception ignored){return false;}}
    public void rememberCollection(MusicEntity owner,List<MusicEntity> tracks)throws Exception{
        if(PlaybackOrigin.of(owner)==null||!owner.id.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid collection id");
        synchronized(OfflineStore.class){
            File directory=new File(root,"collections");directory.mkdirs();File file=new File(directory,owner.type+"-"+owner.id+".json");JSONObject old=null;
            if(file.isFile())try{old=new JSONObject(read(file));}catch(Exception ignored){}
            write(file,SavedCollections.merge(old,SavedCollections.metadata(owner,tracks)).toString());
        }
    }
    public List<MusicEntity> collections(String type){
        List<JSONObject> indexes=new ArrayList<>();File[] files=new File(root,"collections").listFiles();
        if(files!=null)for(File file:files)if(file.getName().endsWith(".json"))try{indexes.add(new JSONObject(read(file)));}catch(Exception ignored){}
        try{return SavedCollections.list(type,list(),indexes);}catch(JSONException ignored){return Collections.emptyList();}
    }
    public void removeStream(Context context,String id)throws Exception{
        String prefix=SavedTrack.cachePrefix(root.getName(),id);dataSource(context,false);
        for(String key:new ArrayList<>(cache.getKeys()))if(key.startsWith(prefix))cache.removeResource(key);
        discard(id);
    }
    private File directory(String id) {
        if(!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid content id");
        return new File(root,id);
    }
    public File manifestFile(String id) {return new File(directory(id),"manifest.mpd");}
    public void stage(String id,String xml) throws IOException {File dir=directory(id);dir.mkdirs();write(manifestFile(id),xml);}
    public void complete(String id,String title,String artist,String artwork,byte[] keySet,long remaining) throws Exception {
        JSONObject json=new JSONObject().put("id",id).put("title",title).put("artist",artist)
            .put("artwork",artwork)
            .put("keySet",android.util.Base64.encodeToString(keySet,android.util.Base64.NO_WRAP)).put("savedAt",System.currentTimeMillis()).put("remainingSecondsAtSave",remaining);
        write(new File(directory(id),"complete.json"),json.toString());
    }
    public List<JSONObject> list() {
        List<JSONObject> result=new ArrayList<>();File[] dirs=root.listFiles();
        if(dirs!=null)for(File dir:dirs)try{File f=new File(dir,"complete.json");if(f.exists())result.add(new JSONObject(read(f)));}catch(Exception ignored){}
        result.sort(Comparator.comparing(o->o.optString("title")));return result;
    }
    public JSONObject get(String id) throws Exception {return new JSONObject(read(new File(directory(id),"complete.json")));}
    public DashPackage dash(String id) throws IOException {try{return dash(id,get(id));}catch(IOException e){throw e;}catch(Exception e){throw new IOException("保存音源の情報を確認できません",e);}}
    private DashPackage dash(String id,JSONObject item)throws IOException{
        String xml=read(new File(directory(id),SavedTrack.manifestName(item)));StreamKey key=item.has("audioGroup")&&item.has("audioStream")?new StreamKey(0,item.optInt("audioGroup"),item.optInt("audioStream")):null;
        return new DashPackage(xml,SavedTrack.requested(item),key);
    }
    public static byte[] keySet(JSONObject item) {return android.util.Base64.decode(item.optString("keySet"),android.util.Base64.DEFAULT);}
    public static String read(File file) throws IOException {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static void write(File file,String text) throws IOException {
        File tmp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!tmp.renameTo(file))throw new IOException("保存に失敗しました");
    }
    public void discard(String id) {
        File[] children=directory(id).listFiles();if(children!=null)for(File f:children)f.delete();directory(id).delete();
    }
}
