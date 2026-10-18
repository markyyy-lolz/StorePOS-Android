package com.azurate.restaurantpos;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Bundle;
import android.print.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int BLUE=Color.rgb(23,105,224), DARK=Color.rgb(21,35,53), MUTED=Color.rgb(101,117,135);
    private WebView browser;
    private String server="";
    private LinearLayout root;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(20,89,189));
        server=getPreferences(0).getString("server_url","");
        if(server.isEmpty()) setup(); else open();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold) t.setTypeface(null,1);
        return t;
    }
    private Button button(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false);
        b.setBackground(rounded(BLUE,12)); b.setTextColor(Color.WHITE);
        return b;
    }
    private void setup(){
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(246,248,251));
        root.setPadding(dp(22),dp(30),dp(22),dp(22));
        ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);
        TextView icon=text("R+",29,Color.WHITE,true);icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(BLUE,18));
        root.addView(icon,new LinearLayout.LayoutParams(dp(70),dp(70)));
        TextView title=text("RestaurantPOS",30,DARK,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(23);
        root.addView(title,p);
        root.addView(text("AZURATE CLOUD  •  ANDROID",13,BLUE,true));
        TextView sub=text("Same blue-and-white StorePOS-inspired interface, with a separate secure Neon-backed restaurant server.",15,MUTED,false);
        LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.topMargin=dp(16);q.bottomMargin=dp(22);
        root.addView(sub,q);
        root.addView(text("HTTPS backend address",14,DARK,true));
        EditText url=new EditText(this);url.setSingleLine(true);url.setTextSize(15);
        url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://restaurantpos.example.com");url.setText(server);
        root.addView(url,new LinearLayout.LayoutParams(-1,dp(65)));
        Button connect=button("Connect to RestaurantPOS");
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(56));cp.topMargin=dp(15);
        root.addView(connect,cp);
        connect.setOnClickListener(v->{
            try{
                Uri u=Uri.parse(url.getText().toString().trim());
                if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null ||
                   u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null ||
                   (u.getPath()!=null && !u.getPath().isEmpty() && !"/".equals(u.getPath()))) throw new Exception();
                String next="https://"+u.getEncodedAuthority();
                if(!next.equals(server)) CookieManager.getInstance().removeAllCookies(null);
                server=next;
                getPreferences(0).edit().putString("server_url",server).apply();
                open();
            }catch(Exception ex){url.setError("Use a valid HTTPS base URL (no path or query).");}
        });
        TextView note=text("Your Node.js API must be deployed first. The APK never stores Neon database credentials. No offline sales or direct Bluetooth ESC/POS support in v0.1.0.",13,MUTED,false);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(27);
        root.addView(note,np);
    }
    private void open(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14),0,dp(8),0);bar.setBackgroundColor(BLUE);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(55)));
        TextView brand=text("R+  RestaurantPOS",18,Color.WHITE,true);
        bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("↻");refresh.setContentDescription("Refresh");
        bar.addView(refresh,new LinearLayout.LayoutParams(dp(47),dp(44)));
        Button settings=button("⚙");settings.setContentDescription("Server settings");
        bar.addView(settings,new LinearLayout.LayoutParams(dp(47),dp(44)));
        refresh.setOnClickListener(v->{if(browser!=null)browser.reload();});
        settings.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Cloud connection").setMessage(server+"\nChange backend address?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Server settings",(d,w)->setup()).show());
        browser=new WebView(this);
        browser.setBackgroundColor(Color.rgb(246,248,251));
        WebSettings opt=browser.getSettings();
        opt.setJavaScriptEnabled(true);opt.setDomStorageEnabled(true);
        opt.setAllowFileAccess(false);opt.setAllowContentAccess(false);
        opt.setJavaScriptCanOpenWindowsAutomatically(false);
        opt.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.addJavascriptInterface(new PrintBridge(),"RestaurantNative");
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                Uri u=req.getUrl();Uri base=Uri.parse(server);
                boolean same="https".equalsIgnoreCase(u.getScheme())&&
                    base.getHost().equalsIgnoreCase(u.getHost())&&base.getPort()==u.getPort();
                if(same)return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}
                catch(Exception e){Toast.makeText(MainActivity.this,"Link unavailable",Toast.LENGTH_SHORT).show();}
                return true;
            }
            @Override public void onPageFinished(WebView v,String url){
                if(url.equals(server)||url.equals(server+"/"))
                    v.evaluateJavascript("window.print=function(){RestaurantNative.printReceipt();};",null);
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){
                if(req.isForMainFrame())
                    Toast.makeText(MainActivity.this,"Cannot reach cloud server. Check your URL and internet.",Toast.LENGTH_LONG).show();
            }
        });
        root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        browser.loadUrl(server+"/");
    }
    private final class PrintBridge{
        @JavascriptInterface public void printReceipt(){runOnUiThread(()->{
            if(browser==null)return;
            PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
            if(pm!=null)pm.print("RestaurantPOS Non-BIR Acknowledgment",
                browser.createPrintDocumentAdapter("RestaurantPOS"),
                new PrintAttributes.Builder().setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
        });}
    }
    @Override public void onBackPressed(){
        if(browser!=null&&browser.canGoBack())browser.goBack();
        else if(browser!=null)new AlertDialog.Builder(this).setTitle("Exit RestaurantPOS?")
            .setPositiveButton("Exit",(d,w)->finish())
            .setNegativeButton("Stay",null).show();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(browser!=null){browser.destroy();browser=null;}super.onDestroy();}
}
