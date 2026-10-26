package jp.nao.amclient;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.EnumMap;
import java.util.function.Consumer;

@android.annotation.SuppressLint("ViewConstructor") // Built with activity callbacks, never inflated from XML.
final class BottomNavigationView extends LinearLayout {
    enum Section {SEARCH("検索",PlayerViews.Icon.SEARCH),LIBRARY("ライブラリ",PlayerViews.Icon.LIBRARY),DOWNLOADS("ダウンロード",PlayerViews.Icon.DOWNLOAD);
        final String label;final PlayerViews.Icon icon;
        Section(String label,PlayerViews.Icon icon){this.label=label;this.icon=icon;}
        static Section parse(String value){try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException ignored){return LIBRARY;}}
    }
    private final EnumMap<Section,LinearLayout> items=new EnumMap<>(Section.class);
    private final EnumMap<Section,ImageView> icons=new EnumMap<>(Section.class);
    private final EnumMap<Section,TextView> labels=new EnumMap<>(Section.class);
    BottomNavigationView(Activity activity,Section selected,Consumer<Section> click){super(activity);setBackgroundColor(PlayerViews.BG);setPadding(0,PlayerViews.dp(activity,4),0,PlayerViews.dp(activity,4));
        for(Section section:Section.values()){
            LinearLayout item=PlayerViews.column(activity);item.setGravity(Gravity.CENTER);item.setPadding(0,PlayerViews.dp(activity,4),0,0);item.setBackground(PlayerViews.ripple(activity,Color.TRANSPARENT,16));item.setContentDescription(section.label+"タブ");item.setFocusable(true);item.setOnClickListener(v->click.accept(section));item.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LinearLayout.LayoutParams(PlayerViews.dp(activity,56),PlayerViews.dp(activity,30)));
            TextView label=PlayerViews.text(activity,section.label,11,PlayerViews.MUTED);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(label,new LinearLayout.LayoutParams(-1,PlayerViews.dp(activity,22)));
            items.put(section,item);icons.put(section,icon);labels.put(section,label);addView(item,new LinearLayout.LayoutParams(0,PlayerViews.dp(activity,60),1));
        }select(selected);
    }
    void select(Section selected){for(Section section:Section.values()){boolean active=section==selected;items.get(section).setSelected(active);labels.get(section).setTextColor(active?PlayerViews.ACCENT:PlayerViews.MUTED);PlayerViews.icon(icons.get(section),section.icon,active?PlayerViews.ACCENT:PlayerViews.MUTED);icons.get(section).setBackground(PlayerViews.background(getContext(),active?0xff243c40:Color.TRANSPARENT,16));}}
    static Section section(Context context){return context instanceof Activity?Section.parse(((Activity)context).getIntent().getStringExtra("section")):Section.LIBRARY;}
    static Intent inherit(Context context,Intent intent){return intent.putExtra("section",section(context).name());}
    static void open(Activity activity,Section section){activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("destination",section.name()));}
}
