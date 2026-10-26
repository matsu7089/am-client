package jp.nao.amclient;

import android.content.Context;
import android.webkit.CookieManager;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental adapter to the authenticated web service, without loading its player. */
public final class AmazonApi {
    private static volatile AmazonApi instance;
    public static AmazonApi get(Context context) {
        if (instance==null) synchronized(AmazonApi.class) { if(instance==null) instance=new AmazonApi(context.getApplicationContext()); }
        return instance;
    }
    private final JSONObject protocol;
    private JSONObject config;
    private long configuredAt;
    private AmazonApi(Context context) {
        try(InputStream in=context.getAssets().open("protocol.json")) { protocol=new JSONObject(read(in)); }
        catch(Exception e) { throw new IllegalStateException("通信設定を読み込めません",e); }
    }
    public synchronized void invalidate() { config=null; configuredAt=0; }
    public void finishLoginRedirect(String url,String agent) throws Exception {
        for(int redirect=0;redirect<8;redirect++) {
            if(!NavigationPolicy.isInternal(url))throw new IOException("ログイン先を確認できません");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            try {
                c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",agent);
                String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)c.setRequestProperty("Cookie",cookies);
                int code=c.getResponseCode();
                for(Map.Entry<String,List<String>> e:c.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(e.getKey()))
                    for(String cookie:e.getValue())CookieManager.getInstance().setCookie(url,cookie);
                if(code>=300&&code<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("ログインの転送先がありません");url=new URL(new URL(url),next).toString();continue;}
                if(code>=400)throw new IOException("ログインを完了できませんでした");
                CookieManager.getInstance().flush();invalidate();config();return;
            }finally{c.disconnect();}
        }
        throw new IOException("ログインの転送回数が多すぎます");
    }
    public synchronized JSONObject config() throws Exception {
        if(config!=null && System.currentTimeMillis()-configuredAt<600_000) return config;
        JSONObject c=request("https://music.amazon.co.jp/config.json?clientApplication=hornet&skipToken=false",new JSONObject(),new HashMap<>());
        if(c.optString("customerId").isEmpty() || c.optBoolean("isCasper",false)) throw new IOException("Amazon にログインしてください");
        config=c; configuredAt=System.currentTimeMillis(); return c;
    }
    public JSONObject gql(String operation, JSONObject variables) throws Exception {
        JSONObject c=config(); Map<String,String> h=headers(c);
        h.put("music-territory",c.optString("musicTerritory","JP"));
        h.put("x-amzn-device-type",c.getString("deviceType")); h.put("x-amzn-device-id",c.getString("deviceId"));
        h.put("x-amzn-client-subscription-tier",c.getString("tier"));h.put("x-amzn-session-id",c.getString("sessionId"));
        h.put("x-api-key",protocol.getString("publicApiKey"));
        JSONObject body=new JSONObject().put("operationName",operation).put("variables",variables);
        String nativeQuery=CatalogQueries.query(operation);
        if(nativeQuery!=null)body.put("query",nativeQuery);
        else if("ShuffleQueue".equals(operation))body.put("query","mutation ShuffleQueue($id: String!, $shuffleMode: String!) { setQueueShuffleMode(id: $id, shuffleMode: $shuffleMode) { status } }");
        else if("LoopQueue".equals(operation))body.put("query","mutation LoopQueue($id: String!, $loopMode: String!) { setQueueLoopMode(id: $id, loopMode: $loopMode) { status } }");
        else body.put("extensions",new JSONObject().put("persistedQuery",new JSONObject().put("version",1)
            .put("sha256Hash",protocol.getJSONObject("operations").getString(operation))));
        JSONObject result=request("https://gql.music.amazon.co.jp/",body,h);
        if(result.has("errors") && !("LibraryHomePage".equals(operation) && result.optJSONObject("data")!=null && result.optJSONObject("data").optJSONObject("user")!=null)) throw new IOException("Amazon API が要求を受け付けませんでした（"+operation+"）。再ログインまたは API の更新が必要です。");
        return result.getJSONObject("data");
    }
    public MusicEntity metadata(String asin) throws Exception {
        return new MusicEntity(gql("NativeTrackMetadata",new JSONObject().put("id",asin)).getJSONObject("track"));
    }
    public MusicEntity historyEntity(MusicEntity item)throws Exception{
        if(!HistoryRecords.supported(item))throw new IOException("この履歴からは再生できません");
        String field="Artist".equals(item.type)?"artist":"Album".equals(item.type)?"album":"playlist";
        JSONObject node=gql("NativeHistory"+item.type,new JSONObject().put("id",item.id)).optJSONObject(field);
        if(node==null)throw new IOException("この項目は現在利用できません");MusicEntity fresh=new MusicEntity(node);
        if(!fresh.canPlay())throw new IOException("この項目は現在再生できません");return fresh;
    }
    public List<MusicEntity> search(String query) throws Exception {
        JSONArray specs=new JSONArray();
        specs.put(new JSONObject().put("label","dmusic_find_song_results_label").put("types",new JSONArray().put("track")).put("limit",30));
        specs.put(new JSONObject().put("label","dmusic_find_album_results_label").put("types",new JSONArray().put("album")).put("limit",10));
        specs.put(new JSONObject().put("label","dmusic_find_playlist_results_label").put("types",new JSONArray().put("playlist")).put("limit",10));
        specs.put(new JSONObject().put("label","dmusic_find_artist_results_label").put("types",new JSONArray().put("artist")).put("limit",15));
        return MusicEntity.collect(gql("SpecSearchPage",new JSONObject().put("searchText",query).put("specifications",specs)
            .put("isAuthenticated",true).put("audiobooksEnabled",false).put("merchEnabled",false).put("categoryInEntityUnionEnabled",false)));
    }
    public CatalogPage library(SearchCategory category,String cursor) throws Exception {
        if(category==SearchCategory.PLAYLISTS)return LibraryPage.playlists(gql("NativeLibraryPlaylists",LibraryPage.playlistVariables(cursor)).getJSONObject("user"));
        if(category!=SearchCategory.TRACKS&&category!=SearchCategory.ALBUMS)throw new IllegalArgumentException("Unsupported library category");
        boolean tracks=category==SearchCategory.TRACKS;
        JSONObject user=gql(tracks?"NativeLibraryTracks":"NativeLibraryAlbums",new JSONObject().put("cursor",cursor.isEmpty()?JSONObject.NULL:cursor)).getJSONObject("user");
        return CatalogPage.connection(null,user.getJSONObject(tracks?"libraryTracks":"libraryAlbums"));
    }
    public MusicEntity savedSelection(JSONObject item)throws Exception{
        PlaybackOrigin source=SavedTrack.origin(item);
        if(source==null)return metadata(item.getString("id"));
        String cursor="";Set<String> visited=new HashSet<>();
        do{
            CatalogPage page=collection(source.type,source.id,cursor);
            MusicEntity selected=SavedTrack.selection(page,item.getString("id"));
            if(selected!=page.owner||!page.owner.hasSelection("PLAY")||page.cursor.isEmpty())return selected;
            cursor=page.cursor;
        }while(visited.add(cursor));
        throw new IOException("再生元の曲一覧を読み込めませんでした");
    }
    public JSONObject createQueue(MusicEntity entity) throws Exception {return createQueue(entity,false);}
    public JSONObject createQueue(MusicEntity entity,boolean shuffle) throws Exception {
        if(entity.playParams==null) throw new IOException("この項目には再生操作がありません");
        JSONObject p=entity.playParams;
        boolean onDemand=contains(p.optJSONArray("selections"),"PLAY");
        boolean random=shuffle||!onDemand&&contains(p.optJSONArray("selections"),"SHUFFLE");
        return gql("CreateRemoteQueue",capabilities().put("id",p.getString("id"))
            .put("containerIds",p.optJSONArray("containerIds")==null?new JSONArray():p.getJSONArray("containerIds"))
            .put("queueId",UUID.randomUUID().toString()).put("shuffle",random?"SHUFFLE_ON":"SHUFFLE_OFF").put("loopMode","LOOP_OFF")
            .put("playbackContinuationMode",onDemand&&("Album".equals(entity.type)||"Playlist".equals(entity.type)||p.optJSONArray("containerIds")!=null&&p.optJSONArray("containerIds").length()>0)?"AUTOPLAY_OFF":"AUTOPLAY_ON")
            .put("startAsStation",!onDemand&&!contains(p.optJSONArray("selections"),"SHUFFLE")&&contains(p.optJSONArray("selections"),"STATION")))
            .getJSONObject("createQueue").getJSONObject("queue");
    }
    public CatalogPage artist(String id,boolean playlists,String cursor)throws Exception{
        String operation=playlists?"NativeArtistPlaylists":"NativeArtistAlbums";
        JSONObject variables=new JSONObject().put("id",id).put("cursor",cursor.isEmpty()?JSONObject.NULL:cursor);
        if(playlists)variables.put("recommender","uri:///artist/"+id+"/related-playlists");
        JSONObject data=gql(operation,variables),person=data.optJSONObject("artist");
        if(person==null)throw new IOException("アーティストが見つかりません");
        MusicEntity owner=new MusicEntity(person);
        if(!playlists)return CatalogPage.connection(owner,person.optJSONObject("albums"));
        List<MusicEntity> items=new ArrayList<>();String next="";JSONObject recommendations=data.optJSONObject("recommender");
        JSONArray edges=recommendations==null?null:recommendations.optJSONArray("edges");
        if(edges!=null)for(int i=0;i<edges.length();i++){
            JSONObject e=edges.optJSONObject(i),n=e==null?null:e.optJSONObject("node");
            CatalogPage page=CatalogPage.connection(owner,n==null?null:n.optJSONObject("recommendedContent"));items=CatalogPage.append(items,page.items);if(!page.cursor.isEmpty())next=page.cursor;
        }
        return new CatalogPage(owner,items,next);
    }
    public CatalogPage collection(String type,String id,String cursor)throws Exception{
        boolean album="Album".equals(type);if(!album&&!"Playlist".equals(type))throw new IOException("曲一覧を取得できません");
        JSONObject data=gql(album?"NativeAlbum":"NativePlaylist",new JSONObject().put("id",id).put("cursor",cursor.isEmpty()?JSONObject.NULL:cursor));
        JSONObject node=data.optJSONObject(album?"album":"playlist");if(node==null)throw new IOException("アルバムまたはプレイリストが見つかりません");
        MusicEntity owner=new MusicEntity(node);if(!album)return CatalogPage.connection(owner,node.optJSONObject("tracks"));
        List<MusicEntity> items=new ArrayList<>();JSONArray tracks=node.optJSONArray("tracks");
        if(tracks!=null)for(int i=0;i<tracks.length();i++)if(tracks.optJSONObject(i)!=null)items.add(new MusicEntity(tracks.optJSONObject(i)));
        return new CatalogPage(owner,items,"");
    }
    public void shuffle(String id,boolean enabled)throws Exception{
        JSONObject result=gql("ShuffleQueue",new JSONObject().put("id",id).put("shuffleMode",enabled?"SHUFFLE_ON":"SHUFFLE_OFF"));
        if(!"SUCCESS".equals(result.getJSONObject("setQueueShuffleMode").optString("status")))throw new IOException("シャッフルを変更できませんでした");
    }
    public void loop(String id,int mode)throws Exception{
        JSONObject result=gql("LoopQueue",new JSONObject().put("id",id).put("loopMode",QueueModes.remoteLoop(mode)));
        if(!"SUCCESS".equals(result.getJSONObject("setQueueLoopMode").optString("status")))throw new IOException("リピートを変更できませんでした");
    }
    public JSONObject queue(String id,String reference) throws Exception {
        return gql("FetchRemoteQueue",capabilities().put("id",id).put("currentEntityReferenceId",reference).put("fetchPrevious",true)).getJSONObject("queue");
    }
    private JSONObject capabilities() throws JSONException {
        return new JSONObject().put("nextLimit",3).put("drmType","WIDEVINE").put("format","ENCRYPTED_OPUS_FLAC")
            .put("audioCapability",new JSONArray().put("STD_RES"));
    }
    private static boolean contains(JSONArray array,String value) { if(array!=null)for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false; }
    public void report(String queue,String reference,String content,String state,long position) throws Exception {
        gql("ReportDevicePlayback",new JSONObject().put("id",queue).put("entityReferenceId",reference)
            .put("clientTimestampInMs",System.currentTimeMillis()).put("playbackState","PAUSED".equals(state)?"STOPPED":state).put("progressInMs",position));
        gql("ReportCurrentDeviceStreaming",new JSONObject().put("contentId",content).put("playbackState","ENDED".equals(state)?"STOPPED":state).put("progressInMs",position));
    }
    public String manifest(String asin) throws Exception {
        JSONObject body=base().put("contentIdList",new JSONArray().put(new JSONObject().put("identifier",asin).put("identifierType","ASIN")))
            .put("customerInfo",new JSONObject().put("marketplaceId",config().getString("marketplaceId")).put("territoryId",config().getString("musicTerritory")))
            .put("musicDashVersionList",new JSONArray().put("SIREN_KATANA")).put("contentProtectionList",new JSONArray().put("TRACK_PSSH"))
            .put("tryAsinSubstitution",ManifestPolicy.allowsAsinSubstitution(config().optString("tier")));
        JSONObject r=dmls("getDashManifestsV2",body).getJSONArray("contentResponseList").getJSONObject(0);
        if(!"SUCCESS".equals(r.optString("contentResponseStatusCode"))) throw new IOException("この曲の再生権限がありません");
        return r.getString("manifest");
    }
    public byte[] license(byte[] challenge) throws Exception {
        JSONObject r=dmls("getLicenseForPlaybackV2",base().put("DrmType","WIDEVINE")
            .put("licenseChallenge",android.util.Base64.encodeToString(challenge,android.util.Base64.NO_WRAP)));
        if(!r.has("license")) throw new IOException("Amazon が再生ライセンスを発行しませんでした");
        return android.util.Base64.decode(r.getString("license"),android.util.Base64.DEFAULT);
    }
    private JSONObject base() throws Exception {
        JSONObject c=config();return new JSONObject().put("appInfo",new JSONObject().put("musicAgent","Native/1.0 AMClient/0.2.0 ("+UUID.randomUUID()+")"))
            .put("customerId",c.getString("customerId")).put("deviceToken",new JSONObject().put("deviceTypeId",c.getString("deviceType")).put("deviceId",c.getString("deviceId")));
    }
    private JSONObject dmls(String target,JSONObject body) throws Exception {
        Map<String,String> h=headers(config());h.put("content-encoding","amz-1.0");
        h.put("x-amz-target","com.amazon.digitalmusiclocator.DigitalMusicLocatorServiceExternal."+target);
        return request("https://music.amazon.co.jp/FE/api/dmls/",body,h);
    }
    private Map<String,String> headers(JSONObject c) throws JSONException {
        JSONObject csrf=c.getJSONObject("csrf");Map<String,String> h=new HashMap<>();
        h.put("csrf-token",csrf.getString("token"));h.put("csrf-rnd",csrf.getString("rnd"));h.put("csrf-ts",csrf.getString("ts"));return h;
    }
    private JSONObject request(String url,JSONObject body,Map<String,String> headers) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15_000);connection.setReadTimeout(25_000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","AMClient/0.2.0 Android");
            connection.setRequestProperty("Origin","https://music.amazon.co.jp");connection.setRequestProperty("Referer",NavigationPolicy.HOME);
            String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null) connection.setRequestProperty("Cookie",cookies);
            for(Map.Entry<String,String> h:headers.entrySet()) connection.setRequestProperty(h.getKey(),h.getValue());
            try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();
            if(code==401||code==403){invalidate();throw new IOException("認証または利用権限を確認できません。再ログインしてください（HTTP "+code+"）");}
            if(code<200||code>=300) {
                String category="";
                try(InputStream error=connection.getErrorStream()) {
                    if(error!=null){JSONObject failure=new JSONObject(read(error));String type=failure.optString("__type");
                        java.util.regex.Matcher match=java.util.regex.Pattern.compile("\\bEC_[A-Z0-9_]+\\b").matcher(failure.optString("message"));
                        category=type.replaceAll("[^a-zA-Z0-9.#_]","")+(match.find()?" "+match.group():"");
                        if(BuildConfig.DEBUG)android.util.Log.w("AMPlayback","API rejected "+headers.getOrDefault("x-amz-target","request")+": HTTP "+code+" "+category);
                    }
                }catch(Exception ignored){}
                throw new HttpFailure(code,category);
            }
            Map<String,List<String>> responseHeaders=connection.getHeaderFields();
            for(Map.Entry<String,List<String>> entry:responseHeaders.entrySet()) if("Set-Cookie".equalsIgnoreCase(entry.getKey()))
                for(String cookie:entry.getValue()) CookieManager.getInstance().setCookie(url,cookie);
            try(InputStream in=connection.getInputStream()){JSONObject j=new JSONObject(read(in));if(j.has("__type")||j.has("Output"))throw new IOException("Amazon API が要求を拒否しました");return j;}
        } finally {connection.disconnect();}
    }
    static final class HttpFailure extends IOException {
        final int status;final String category;
        HttpFailure(int status,String category){super("Amazon との通信に失敗しました（HTTP "+status+" "+category+"）");this.status=status;this.category=category;}
    }
    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>8_000_000)throw new IOException("応答が大きすぎます");out.write(buffer,0,count);}return out.toString(StandardCharsets.UTF_8.name());
    }
}
