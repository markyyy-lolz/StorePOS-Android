package com.azurate.restaurantpos;

import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

final class CloudApi {
  final Context context;
  String base,cookie="",tenant="",restaurant="",role="",staff="";
  boolean onlineSession=false;
  CloudApi(Context c){context=c; base=prefs().getString("server","");}
  android.content.SharedPreferences prefs(){return context.getSharedPreferences("restaurantpos_cloud",Context.MODE_PRIVATE);}
  void setBase(String url){UriCheck(url);base=url;cookie="";onlineSession=false;prefs().edit().putString("server",base).apply();}
  static void UriCheck(String raw){
    UriWrapper.parse(raw);
  }
  static class UriWrapper{
    static void parse(String raw){
      try{
        java.net.URI u=new java.net.URI(raw.trim());
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null
            ||(u.getPath()!=null&&!u.getPath().isEmpty()&&!u.getPath().equals("/")))throw new Exception();
      }catch(Exception e){throw new IllegalArgumentException("Enter an HTTPS base URL without a path.");}
    }
  }
  JSONObject request(String method,String path,JSONObject payload)throws Exception{
    if(base.isEmpty())throw new IOException("Set your HTTPS cloud address in Settings.");
    URL u=new URL(base+path);
    HttpURLConnection conn=(HttpURLConnection)u.openConnection();
    conn.setConnectTimeout(5000);conn.setReadTimeout(11000);
    conn.setRequestMethod(method);conn.setRequestProperty("Accept","application/json");
    if(!cookie.isEmpty())conn.setRequestProperty("Cookie",cookie);
    if(payload!=null){
      conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
      try(OutputStream out=conn.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
    }
    try{
      int status=conn.getResponseCode();
      if(path.equals("/api/login") && status==200){
        String setCookie=conn.getHeaderField("Set-Cookie");
        if(setCookie!=null&&setCookie.startsWith("rpos_session="))cookie=setCookie.split(";",2)[0];
      }
      InputStream stream=status>=400?conn.getErrorStream():conn.getInputStream();
      String text=stream==null?"":readAll(stream);
      JSONObject obj=new JSONObject(text.isEmpty()?"{}":text);
      if(status>=400)throw new ApiFailure(status,obj.optString("error","Request failed ("+status+")"));
      return obj;
    }finally{conn.disconnect();}
  }
  static String readAll(InputStream s)throws IOException{
    try(InputStream in=s;ByteArrayOutputStream buf=new ByteArrayOutputStream()){
      byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){buf.write(b,0,n);if(buf.size()>2000000)throw new IOException("Response too large");}
      return buf.toString("UTF-8");
    }
  }
  static final class ApiFailure extends IOException{
    final int status;ApiFailure(int code,String message){super(message);status=code;}
  }
  void login(String email,String password)throws Exception{
    JSONObject p=new JSONObject();p.put("email",email);p.put("password",password);
    request("POST","/api/login",p);
    JSONObject me=request("GET","/api/me",null);
    JSONObject rest=me.getJSONObject("restaurant"),user=me.getJSONObject("user");
    tenant=rest.getString("id");restaurant=rest.getString("name");
    role=user.getString("role");staff=user.getString("name");
    onlineSession=true;
    String salt=randomSalt(),hash=pbkdf(password,salt);
    prefs().edit().putString("offline_email",email.toLowerCase(Locale.ROOT).trim())
      .putString("offline_salt",salt).putString("offline_verifier",hash)
      .putString("tenant",tenant).putString("restaurant",restaurant)
      .putString("role",role).putString("staff",staff)
      .putLong("offline_until",System.currentTimeMillis()+8*60*60*1000L).apply();
  }
  boolean offlineLogin(String email,String password){
    android.content.SharedPreferences p=prefs();
    if(System.currentTimeMillis()>p.getLong("offline_until",0))return false;
    if(!p.getString("offline_email","").equals(email.toLowerCase(Locale.ROOT).trim()))return false;
    try{
      String val=pbkdf(password,p.getString("offline_salt",""));
      if(!MessageDigest.isEqual(val.getBytes(StandardCharsets.UTF_8),
         p.getString("offline_verifier","").getBytes(StandardCharsets.UTF_8)))return false;
      tenant=p.getString("tenant","");restaurant=p.getString("restaurant","");
      role=p.getString("role","");staff=p.getString("staff","");
      onlineSession=false;return !tenant.isEmpty();
    }catch(Exception e){return false;}
  }
  static String randomSalt(){byte[] buf=new byte[16];new SecureRandom().nextBytes(buf);return android.util.Base64.encodeToString(buf,android.util.Base64.NO_WRAP);}
  static String pbkdf(String password,String salt)throws Exception{
    byte[] raw=android.util.Base64.decode(salt,android.util.Base64.DEFAULT);
    KeySpec spec=new PBEKeySpec(password.toCharArray(),raw,210000,256);
    byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    return android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP);
  }
  JSONArray getList(String path)throws Exception{
    // The /api/tables and /api/orders endpoints return root JSON arrays.
    URL u=new URL(base+path);HttpURLConnection conn=(HttpURLConnection)u.openConnection();
    conn.setRequestProperty("Cookie",cookie);conn.setConnectTimeout(5000);conn.setReadTimeout(11000);
    try{
      int code=conn.getResponseCode();String text=readAll(code>=400?conn.getErrorStream():conn.getInputStream());
      if(code>=400)throw new ApiFailure(code,text);
      return new JSONArray(text);
    }finally{conn.disconnect();}
  }
  JSONObject fetchMenu()throws Exception{return request("GET","/api/menu",null);}
  void refresh(LocalStore store)throws Exception{
    if(!onlineSession)throw new IOException("Sign in online to refresh.");
    JSONObject m=fetchMenu();JSONArray t=getList("/api/tables");
    store.replaceCatalog(tenant,m,t);
  }
  String sync(LocalStore store)throws Exception{
    if(!onlineSession)throw new IOException("Sign in online before synchronization.");
    JSONArray pending=store.pending(tenant);
    int count=0,conflicts=0;
    for(int i=0;i<pending.length();i++){
      JSONObject s=pending.getJSONObject(i);String id=s.getString("id");
      if("conflict".equals(s.optString("cloud_status"))){conflicts++;continue;}
      try{
        JSONObject data=new JSONObject(s.getString("json"));
        String cloud=s.optString("cloud_id","");
        JSONObject remote;
        if(cloud.isEmpty()){
          remote=request("POST","/api/orders",data);
          cloud=remote.getJSONObject("order").getString("id");
          store.cloudCreated(id,cloud);
        } else {
          remote=request("GET","/api/orders/"+cloud,null);
        }
        int cloudTotal=remote.getJSONObject("order").getInt("total_minor");
        if(cloudTotal!=s.getInt("amount")){
          store.conflict(id,"Price changed: local PHP "+String.format(Locale.US,"%.2f",s.getInt("amount")/100.0)
            +", cloud PHP "+String.format(Locale.US,"%.2f",cloudTotal/100.0)+". Requires manager review.");
          conflicts++;continue;
        }
        if(s.optString("local_payment","").equals("cash")){
          JSONObject pay=new JSONObject();pay.put("method","cash");pay.put("tendered_minor",cloudTotal);
          request("POST","/api/orders/"+cloud+"/payment",pay);
        }
        store.synced(id);count++;
      }catch(ApiFailure e){
        if(e.status==401)throw e;
        if(e.status==409)store.conflict(id,"Cloud conflict: "+e.getMessage());
        else store.syncError(id,e.getMessage());
      }catch(Exception e){store.syncError(id,e.getMessage());throw e;}
    }
    return "Synced "+count+" • Conflicts "+conflicts;
  }
  String gatewayCheckout(String cloudId)throws Exception{
    JSONObject r=request("POST","/api/orders/"+cloudId+"/paymongo-checkout",new JSONObject());
    String url=r.getString("checkout_url");
    if(!url.startsWith("https://checkout.paymongo.com/"))throw new IOException("Unexpected gateway URL");
    return url;
  }
}