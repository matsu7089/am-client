package jp.nao.amclient;

import org.json.*;
import java.util.*;

final class CatalogPage {
    final MusicEntity owner;
    final List<MusicEntity> items;
    final String cursor;
    CatalogPage(MusicEntity owner,List<MusicEntity> items,String cursor){this.owner=owner;this.items=Collections.unmodifiableList(new ArrayList<>(items));this.cursor=cursor;}
    static CatalogPage connection(MusicEntity owner,JSONObject connection){
        List<MusicEntity> items=new ArrayList<>();JSONArray edges=connection==null?null:connection.optJSONArray("edges");
        if(edges!=null)for(int i=0;i<edges.length();i++){
            JSONObject edge=edges.optJSONObject(i);if(edge==null||edge.optJSONObject("node")==null||edge.optJSONObject("node").optString("id").isEmpty())continue;
            items.add(MusicEntity.fromEdge(edge));
        }
        return new CatalogPage(owner,items,cursor(connection));
    }
    static String cursor(JSONObject connection){
        JSONObject page=connection==null?null:connection.optJSONObject("pageInfo");
        if(page==null||!page.optBoolean("hasNextPage"))return "";
        Object token=page.opt("token"),end=page.opt("endCursor");
        return token instanceof String&&!((String)token).isEmpty()?(String)token:end instanceof String?(String)end:"";
    }
    static List<MusicEntity> append(List<MusicEntity> old,List<MusicEntity> next){
        LinkedHashMap<String,MusicEntity> unique=new LinkedHashMap<>();
        for(MusicEntity item:old)unique.put(item.id,item);for(MusicEntity item:next)unique.putIfAbsent(item.id,item);
        return new ArrayList<>(unique.values());
    }
}
