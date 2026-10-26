package jp.nao.amclient;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArtworkTest {
    private JSONObject image(String url,int width,String type)throws Exception{return new JSONObject().put("url",url).put("width",width).put("imageType",type);}
    @Test public void prefersSmallUsableCoverToBackgroundOrOversizedImage()throws Exception {
        JSONArray images=new JSONArray().put(image("https://example.org/background.jpg",160,"BACKGROUND")).put(image("https://example.org/large.jpg",1400,"PRIMARY"))
            .put(image("https://example.org/cover.jpg",500,"PRIMARY")).put(image("https://example.org/tiny.jpg",32,"PRIMARY"));
        assertEquals("https://example.org/cover.jpg",MusicEntity.selectArtwork(images));
    }
    @Test public void usesAlbumArtworkAndRejectsLocalOrInsecureUrls()throws Exception {
        JSONArray images=new JSONArray().put(image("file:///private/image",500,"PRIMARY")).put(image("http://example.org/image",500,"PRIMARY")).put(image("https://example.org/album.jpg",500,"PRIMARY"));
        MusicEntity entity=new MusicEntity(new JSONObject().put("album",new JSONObject().put("images",images)));
        assertEquals("https://example.org/album.jpg",entity.artworkUrl);assertEquals("",MusicEntity.selectArtwork(null));
    }
}
