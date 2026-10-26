package jp.nao.amclient;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Small native controls shared by browsing and the full player. */
final class PlayerViews {
    static final int BG=0xff101619,SURFACE=0xff1d292e,ACCENT=0xff70e5d5,MUTED=0xffb6c4c8;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    static void text(TextView v,CharSequence value){if(!TextUtils.equals(v.getText(),value))v.setText(value);}
    static GradientDrawable background(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static Drawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x2870e5d5),background(c,color,radius),background(c,Color.WHITE,radius));}
    static ImageView artwork(Context c,int radius){ImageView v=new ImageView(c);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setImageResource(R.drawable.ic_music);v.setBackground(background(c,0xff28383e,radius));v.setClipToOutline(true);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static ImageButton iconButton(Context c,Icon icon,String label,Runnable click){ImageButton v=new ImageButton(c);v.setBackground(ripple(c,Color.TRANSPARENT,28));v.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));v.setScaleType(ImageView.ScaleType.CENTER);icon(v,icon,ACCENT);v.setContentDescription(label);v.setOnClickListener(w->click.run());return v;}
    static void icon(ImageView v,Icon icon,int color){String key=icon.name()+color;if(!key.equals(v.getTag(R.id.player_icon))){v.setTag(R.id.player_icon,key);v.setImageDrawable(new Symbol(v.getContext(),icon,color));}}
    static Button button(Context c,String label,Runnable click){Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(13);b.setTextColor(ACCENT);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackground(ripple(c,Color.TRANSPARENT,12));b.setOnClickListener(v->click.run());return b;}
    static void enabled(View v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1f:.35f);}
    static void insets(LinearLayout root){root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});}
    static String time(long millis){long seconds=Math.max(0,millis)/1000;return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    enum Icon {SEARCH,LIBRARY,DOWNLOAD,PLAY,PAUSE,NEXT,PREVIOUS,SHUFFLE,REPEAT,REPEAT_ONE,DOWN,SETTINGS}
    /** Vector strokes avoid font-dependent transport glyphs and extra bitmap assets. */
    private static final class Symbol extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Icon icon;private final int size;
        Symbol(Context c,Icon icon,int color){this.icon=icon;size=dp(c,24);paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        @Override public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);paint.setStyle(Paint.Style.STROKE);
            switch(icon){
                case SEARCH:c.drawCircle(10.5f,10.5f,6.5f,paint);line(c,15.5f,15.5f,21,21);break;
                case LIBRARY:line(c,4,4,4,20);line(c,9,4,9,20);line(c,14,5,19,20);break;
                case DOWNLOAD:line(c,12,3,12,15);path(c,7,10,12,15,17,10);path(c,4,16,4,21,20,21,20,16);break;
                case PLAY:paint.setStyle(Paint.Style.FILL);path(c,7,4,20,12,7,20,7,4);break;
                case PAUSE:paint.setStyle(Paint.Style.FILL);c.drawRoundRect(6,4,10,20,1,1,paint);c.drawRoundRect(14,4,18,20,1,1,paint);break;
                case PREVIOUS:c.save();c.translate(24,0);c.scale(-1,1);skip(c);c.restore();break;
                case NEXT:skip(c);break;
                case SHUFFLE:path(c,3,5,6,5,17,19,21,19);path(c,17,15,21,19,17,23);path(c,3,19,6,19,17,5,21,5);path(c,17,1,21,5,17,9);break;
                case REPEAT:case REPEAT_ONE:path(c,4,10,4,6,20,6,17,3);path(c,20,6,17,9);path(c,20,14,20,18,4,18,7,21);path(c,4,18,7,15);if(icon==Icon.REPEAT_ONE){paint.setStyle(Paint.Style.FILL);paint.setTextSize(8);paint.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("1",10,15,paint);}break;
                case DOWN:path(c,5,9,12,16,19,9);break;
                case SETTINGS:c.drawCircle(12,12,3,paint);c.drawCircle(12,12,8,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,(float)(12+8*Math.cos(a)),(float)(12+8*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)));}break;
            }c.restore();
        }
        private void skip(Canvas c){paint.setStyle(Paint.Style.FILL);path(c,5,5,16,12,5,19,5,5);c.drawRoundRect(17,5,19,19,.7f,.7f,paint);}
        private void line(Canvas c,float x,float y,float a,float b){c.drawLine(x,y,a,b,paint);}
        private void path(Canvas c,float... xy){Path p=new Path();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);c.drawPath(p,paint);}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
