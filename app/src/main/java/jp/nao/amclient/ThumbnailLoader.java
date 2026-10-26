package jp.nao.amclient;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.concurrent.*;

/** Small decoded images are cached; recycled rows never receive an older row's artwork. */
final class ThumbnailLoader implements AutoCloseable {
    private final ExecutorService work=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private volatile boolean closed;
    void bind(ImageView image,String url){
        if(url.equals(image.getTag()))return;
        image.setTag(url);image.setImageResource(R.drawable.ic_music);
        if(url.isEmpty()||closed)return;
        Bitmap cached=cache.get(url);if(cached!=null){image.setImageBitmap(cached);return;}
        WeakReference<ImageView> target=new WeakReference<>(image);
        work.execute(()->{try{
            Bitmap bitmap=fetch(url);if(bitmap==null||closed)return;cache.put(url,bitmap);
            main.post(()->{ImageView current=target.get();if(!closed&&current!=null&&url.equals(current.getTag()))current.setImageBitmap(bitmap);});
        }catch(IOException|IllegalArgumentException ignored){/* Keep the placeholder on an image failure. */}});
    }
    private Bitmap fetch(String address)throws IOException {
        URL url=new URL(address);
        for(int redirects=0;redirects<4;redirects++){
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Invalid artwork URL");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            try{
                c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
                int code=c.getResponseCode();
                if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)return null;url=new URL(url,location);continue;}
                if(code!=200||c.getContentLengthLong()>2_000_000)return null;
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2_000_000)return null;out.write(buffer,0,n);}}
                byte[] data=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,options);
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>384)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,options);
            }finally{c.disconnect();}
        }
        return null;
    }
    @Override public void close(){closed=true;work.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
