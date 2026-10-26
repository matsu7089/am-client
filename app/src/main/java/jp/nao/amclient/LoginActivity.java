package jp.nao.amclient;

import android.app.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import android.view.Gravity;

/** Only the Amazon authentication pages are loaded; the Music player is intercepted. */
public class LoginActivity extends Activity {
    private WebView login;
    private boolean completing;
    private final java.util.concurrent.ExecutorService work=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("Amazon アカウントでログイン");title.setTextSize(18);title.setGravity(Gravity.CENTER);root.addView(title);
        login=new WebView(this);root.addView(login,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        login.getSettings().setJavaScriptEnabled(true);login.getSettings().setDomStorageEnabled(true);
        login.getSettings().setAllowFileAccess(false);login.getSettings().setAllowContentAccess(false);
        login.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(login,false);
        login.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                String url=request.getUrl().toString();if(!NavigationPolicy.isInternal(url))return true;
                if(request.isForMainFrame()&&NavigationPolicy.isMusic(url)&&!url.contains("forceSignIn")&&!url.contains("ap/")){
                    if(!completing){completing=true;title.setText("ログインを確認しています…");String agent=login.getSettings().getUserAgentString();
                        work.execute(()->{try{AmazonApi api=AmazonApi.get(LoginActivity.this);api.finishLoginRedirect(url,agent);getSharedPreferences("account",0).edit().putString("scope",OfflineStore.scope(api.config().getString("customerId"))).apply();runOnUiThread(()->{if(!isFinishing()){setResult(RESULT_OK);finish();}});}
                        catch(Exception e){runOnUiThread(()->{completing=false;title.setText("ログインを再試行してください");Toast.makeText(LoginActivity.this,"認証を完了できませんでした",Toast.LENGTH_LONG).show();});}});
                    }return true;
                }return false;
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())Toast.makeText(LoginActivity.this,"ログイン画面を開けませんでした",Toast.LENGTH_LONG).show();}
        });
        login.loadUrl(NavigationPolicy.HOME+"forceSignIn?useHorizonte=true");
    }
    @Override protected void onDestroy(){work.shutdownNow();if(login!=null){login.stopLoading();login.destroy();}super.onDestroy();}
}
