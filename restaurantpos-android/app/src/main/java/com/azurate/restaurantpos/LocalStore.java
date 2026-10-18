package com.azurate.restaurantpos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class LocalStore extends SQLiteOpenHelper {
  LocalStore(Context c){ super(c,"restaurantpos_native_v2.db",null,1); }
  @Override public void onCreate(SQLiteDatabase d){
    d.execSQL("CREATE TABLE menus(tenant TEXT NOT NULL,id TEXT NOT NULL,name TEXT NOT NULL,category TEXT,price INTEGER NOT NULL,active INTEGER NOT NULL,PRIMARY KEY(tenant,id))");
    d.execSQL("CREATE TABLE tables(tenant TEXT NOT NULL,id TEXT NOT NULL,label TEXT NOT NULL,active INTEGER NOT NULL,PRIMARY KEY(tenant,id))");
    d.execSQL("CREATE TABLE sales(id TEXT PRIMARY KEY,tenant TEXT NOT NULL,json TEXT NOT NULL,amount INTEGER NOT NULL,cloud_id TEXT,local_payment TEXT,cloud_status TEXT NOT NULL DEFAULT 'pending',error TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
    d.execSQL("CREATE INDEX sales_sync_idx ON sales(tenant,cloud_status,created)");
    d.execSQL("CREATE TABLE printers(id TEXT PRIMARY KEY,name TEXT NOT NULL,role TEXT NOT NULL,transport TEXT NOT NULL,host TEXT NOT NULL,port INTEGER NOT NULL,mac TEXT NOT NULL,vid INTEGER NOT NULL,pid INTEGER NOT NULL,active INTEGER NOT NULL)");
    d.execSQL("CREATE TABLE print_jobs(id TEXT PRIMARY KEY,printer_id TEXT NOT NULL,sale_id TEXT NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'pending',last_error TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
    d.execSQL("CREATE INDEX jobs_status_idx ON print_jobs(state,created)");
  }
  @Override public void onUpgrade(SQLiteDatabase d,int old,int ver){ throw new IllegalStateException("Explicit migration required; refusing data deletion"); }
  synchronized void replaceCatalog(String tenant,JSONObject menu,JSONArray tables)throws JSONException{
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
    try {
      d.delete("menus","tenant=?",new String[]{tenant});
      JSONArray items=menu.getJSONArray("items");
      for(int i=0;i<items.length();i++){
        JSONObject o=items.getJSONObject(i); ContentValues v=new ContentValues();
        v.put("tenant",tenant);v.put("id",o.getString("id"));v.put("name",o.getString("name"));
        v.put("category",o.optString("category_id",""));v.put("price",o.getInt("price_minor"));
        v.put("active",o.optBoolean("active",true)?1:0);
        d.insertOrThrow("menus",null,v);
      }
      d.delete("tables","tenant=?",new String[]{tenant});
      for(int i=0;i<tables.length();i++){
        JSONObject o=tables.getJSONObject(i);ContentValues v=new ContentValues();
        v.put("tenant",tenant);v.put("id",o.getString("id"));v.put("label",o.getString("label"));
        v.put("active",o.optBoolean("active",true)?1:0);d.insertOrThrow("tables",null,v);
      }
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
  }
  synchronized JSONArray menus(String tenant){
    JSONArray arr=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,price FROM menus WHERE tenant=? AND active=1 ORDER BY name",new String[]{tenant})){
      while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getString(0));o.put("name",c.getString(1));o.put("price_minor",c.getInt(2));arr.put(o);}
    }catch(Exception e){throw new IllegalStateException(e);}
    return arr;
  }
  synchronized JSONArray tables(String tenant){
    JSONArray arr=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,label FROM tables WHERE tenant=? AND active=1 ORDER BY label",new String[]{tenant})){
      while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getString(0));o.put("label",c.getString(1));arr.put(o);}
    }catch(Exception e){throw new IllegalStateException(e);}
    return arr;
  }
  synchronized void addSale(String tenant,String id,JSONObject data,int amount,String paid,String kitchen,String receipt)throws Exception {
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
    try{
      ContentValues v=new ContentValues();v.put("id",id);v.put("tenant",tenant);v.put("json",data.toString());
      v.put("amount",amount);v.put("local_payment",paid);v.put("created",System.currentTimeMillis());
      d.insertOrThrow("sales",null,v);
      // Printer jobs are persisted in the same transaction as the sale.
      JSONArray printers=printers();
      for(int i=0;i<printers.length();i++){
        JSONObject p=printers.getJSONObject(i);
        String role=p.getString("role"),body=role.equals("kitchen")?kitchen:(paid!=null?receipt:null);
        if(body==null||!p.getBoolean("active"))continue;
        ContentValues j=new ContentValues();
        j.put("id",UUID.randomUUID().toString());j.put("printer_id",p.getString("id"));
        j.put("sale_id",id);j.put("role",role);j.put("body",body);j.put("created",System.currentTimeMillis());
        d.insertOrThrow("print_jobs",null,j);
      }
      d.setTransactionSuccessful();
    }finally{d.endTransaction();}
  }
  synchronized JSONArray sales(String tenant){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,json,amount,cloud_id,local_payment,cloud_status,error,created FROM sales WHERE tenant=? ORDER BY created DESC LIMIT 100",new String[]{tenant})){
      while(c.moveToNext()){JSONObject o=new JSONObject();
        for(int k=0;k<8;k++){String name=new String[]{"id","json","amount","cloud_id","local_payment","cloud_status","error","created"}[k];
          if(c.isNull(k))o.put(name,JSONObject.NULL);else if(k==2||k==7)o.put(name,c.getLong(k));else o.put(name,c.getString(k));}
        out.put(o);
      }
    }catch(Exception e){throw new IllegalStateException(e);}
    return out;
  }
  synchronized JSONArray pending(String tenant){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,json,amount,cloud_id,local_payment,cloud_status,error,created FROM sales WHERE tenant=? AND cloud_status<>'synced' ORDER BY created ASC",new String[]{tenant})){
      while(c.moveToNext()){JSONObject o=new JSONObject();
        String[] names={"id","json","amount","cloud_id","local_payment","cloud_status","error","created"};
        for(int k=0;k<8;k++){if(c.isNull(k))o.put(names[k],JSONObject.NULL);
          else if(k==2||k==7)o.put(names[k],c.getLong(k));else o.put(names[k],c.getString(k));}
        out.put(o);
      }
    }catch(Exception e){throw new IllegalStateException(e);}
    return out;
  }
  synchronized void cloudCreated(String id,String cloud){
    ContentValues v=new ContentValues();v.put("cloud_id",cloud);v.put("cloud_status","pending_payment");v.put("error","");
    getWritableDatabase().update("sales",v,"id=?",new String[]{id});
  }
  synchronized void synced(String id){ContentValues v=new ContentValues();v.put("cloud_status","synced");v.put("error","");getWritableDatabase().update("sales",v,"id=?",new String[]{id});}
  synchronized void conflict(String id,String reason){ContentValues v=new ContentValues();v.put("cloud_status","conflict");v.put("error",reason);getWritableDatabase().update("sales",v,"id=?",new String[]{id});}
  synchronized void syncError(String id,String reason){ContentValues v=new ContentValues();v.put("error",reason);getWritableDatabase().update("sales",v,"id=?",new String[]{id});}
  synchronized void addPrinter(JSONObject p)throws Exception{
    ContentValues v=new ContentValues();
    for(String k:new String[]{"id","name","role","transport","host","mac"})v.put(k,p.getString(k));
    for(String k:new String[]{"port","vid","pid"})v.put(k,p.getInt(k));
    v.put("active",1);getWritableDatabase().insertWithOnConflict("printers",null,v,SQLiteDatabase.CONFLICT_REPLACE);
  }
  synchronized void deletePrinter(String id){getWritableDatabase().delete("printers","id=?",new String[]{id});}
  synchronized JSONArray printers(){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,role,transport,host,port,mac,vid,pid,active FROM printers ORDER BY name",null)){
      while(c.moveToNext()){JSONObject p=new JSONObject();
        for(int i=0;i<10;i++){String k=new String[]{"id","name","role","transport","host","port","mac","vid","pid","active"}[i];
          if(i==5||i==7||i==8)p.put(k,c.getInt(i));else if(i==9)p.put(k,c.getInt(i)==1);else p.put(k,c.getString(i));}
        out.put(p);}
    }catch(Exception e){throw new IllegalStateException(e);}
    return out;
  }
  synchronized JSONArray waitingJobs(){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT id,printer_id,sale_id,role,body,state,last_error FROM print_jobs WHERE state='pending' ORDER BY created LIMIT 100",null)){
      while(c.moveToNext()){JSONObject j=new JSONObject();String[] n={"id","printer_id","sale_id","role","body","state","last_error"};for(int k=0;k<n.length;k++)j.put(n[k],c.getString(k));out.put(j);}
    }catch(Exception e){throw new IllegalStateException(e);}
    return out;
  }
  synchronized boolean claimJob(String id){
    ContentValues v=new ContentValues();v.put("state","attempted");
    // Commit the attempt BEFORE bytes go to the printer. Ambiguous transport failures are not auto-retried.
    return getWritableDatabase().update("print_jobs",v,"id=? AND state='pending'",new String[]{id})==1;
  }
  synchronized void resultJob(String id,boolean ok,String error){
    ContentValues v=new ContentValues();v.put("state",ok?"sent":"uncertain");v.put("last_error",error);
    getWritableDatabase().update("print_jobs",v,"id=?",new String[]{id});
  }
  synchronized void manuallyReprint(String sale,String role,String body)throws Exception{
    JSONArray ps=printers();
    for(int i=0;i<ps.length();i++){
      JSONObject p=ps.getJSONObject(i);if(!p.optBoolean("active")||!role.equals(p.getString("role")))continue;
      ContentValues j=new ContentValues();j.put("id",UUID.randomUUID().toString());j.put("printer_id",p.getString("id"));
      j.put("sale_id",sale);j.put("role",role);j.put("body","*** AUTHORIZED REPRINT ***\n"+body);
      j.put("created",System.currentTimeMillis());
      getWritableDatabase().insertOrThrow("print_jobs",null,j);
    }
  }
}