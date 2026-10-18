package com.azurate.restaurantpos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class NativeActivity extends Activity {
  static final int BLUE=0xff1769e0,INK=0xff182537,SOFT=0xfff5f8fc,GREY=0xff617184;
  LocalStore db;CloudApi api;ExecutorService executor=Executors.newSingleThreadExecutor();
  LinearLayout root,content,nav;TextView subtitle;
  String page="Dashboard",service="dine_in",table="",orderNote="";
  final LinkedHashMap<String,Integer> cart=new LinkedHashMap<>();
  boolean busy=false;
  int d(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable bg(int color,int rad){GradientDrawable x=new GradientDrawable();x.setColor(color);x.setCornerRadius(d(rad));return x;}
  TextView text(String value,int size,int color,boolean bold){
    TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
    if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
  }
  LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
  LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(top);return p;}
  void pad(View v,int x){v.setPadding(d(x),d(x),d(x),d(x));}
  TextView button(String name,Runnable action){
    TextView b=text(name,13,Color.WHITE,true);b.setGravity(Gravity.CENTER);
    b.setBackground(bg(BLUE,11));b.setPadding(d(12),d(12),d(12),d(12));
    b.setOnClickListener(v->action.run());return b;
  }
  TextView secondary(String name,Runnable action){
    TextView b=text(name,13,BLUE,true);b.setGravity(Gravity.CENTER);
    b.setBackground(bg(0xffe5efff,11));b.setPadding(d(12),d(12),d(12),d(12));
    b.setOnClickListener(v->action.run());return b;
  }
  LinearLayout panel(String heading){
    LinearLayout p=column();p.setBackground(bg(Color.WHITE,16));pad(p,15);
    if(!heading.isEmpty())p.addView(text(heading,17,INK,true));
    content.addView(p,margin(12));return p;
  }
  void line(LinearLayout parent,String title,String desc){
    TextView h=text(title,15,INK,true);parent.addView(h,margin(10));
    if(desc!=null){TextView sub=text(desc,12,GREY,false);parent.addView(sub);}
  }
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  interface Job {void execute()throws Exception;}
  void work(Job j,Runnable done){
    if(busy){toast("Another action is running.");return;}busy=true;
    executor.execute(()->{String fail=null;
      try{j.execute();}catch(Exception e){fail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
      final String error=fail;
      runOnUiThread(()->{busy=false;if(error!=null)new AlertDialog.Builder(this).setTitle("RestaurantPOS").setMessage(error).setPositiveButton("OK",null).show();
        if(done!=null)done.run();});
    });
  }
  @Override public void onCreate(Bundle bundle){
    super.onCreate(bundle);getWindow().setStatusBarColor(0xff1459bd);
    db=new LocalStore(this);api=new CloudApi(this);
    login();
  }
  void shell(String title){
    root=column();root.setBackgroundColor(SOFT);setContentView(root);
    LinearLayout header=row();header.setBackgroundColor(BLUE);header.setPadding(d(14),d(15),d(12),d(15));
    LinearLayout brand=column();brand.addView(text("R+  RestaurantPOS",21,Color.WHITE,true));
    subtitle=text(api.restaurant.isEmpty()?"AZURATE CLOUD":api.restaurant,12,0xffd9e8ff,false);brand.addView(subtitle);
    header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
    TextView status=text(api.onlineSession?"● CLOUD":"● OFFLINE",12,Color.WHITE,true);header.addView(status);
    root.addView(header);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    content=column();content.setPadding(d(15),d(6),d(15),d(20));scroll.addView(content);
    TextView pageTitle=text(title,24,INK,true);content.addView(pageTitle,margin(12));
    TextView caption=text("Native Android • "+api.staff+" • "+api.role,12,GREY,false);content.addView(caption);
    nav=row();nav.setBackgroundColor(Color.WHITE);nav.setPadding(d(3),d(7),d(3),d(7));root.addView(nav);
    for(String name:new String[]{"Home","POS","Orders","Kitchen","Printers","Settings"}){
      TextView n=text(name,10,page.equals(name)?BLUE:GREY,page.equals(name));
      n.setGravity(Gravity.CENTER);n.setPadding(d(2),d(12),d(2),d(12));
      nav.addView(n,new LinearLayout.LayoutParams(0,-2,1));
      n.setOnClickListener(v->{page=name;render();});
    }
  }
  EditText field(String hint,String initial){
    EditText e=new EditText(this);e.setTextSize(15);e.setSingleLine(true);e.setHint(hint);e.setText(initial);return e;
  }
  void login(){
    page="Login";root=column();root.setBackgroundColor(SOFT);pad(root,22);setContentView(root);
    root.addView(text("R+  RestaurantPOS",27,BLUE,true),margin(25));
    root.addView(text("Azurate Cloud • Native Android",14,GREY,false),margin(6));
    root.addView(text("Server URL (HTTPS)",14,INK,true),margin(27));
    EditText server=field("https://your-restopos-backend.example",api.base);root.addView(server);
    root.addView(text("Staff email",14,INK,true),margin(14));
    EditText email=field("owner@restaurant.com",api.prefs().getString("offline_email",""));root.addView(email);
    root.addView(text("Password",14,INK,true),margin(14));
    EditText pass=field("Password","");pass.setInputType(129);root.addView(pass);
    TextView signIn=button("Sign in / Unlock offline",()->{
      final String address=server.getText().toString().trim(),user=email.getText().toString().trim(),password=pass.getText().toString();
      work(()->{
        api.setBase(address);
        try{api.login(user,password);api.refresh(db);}
        catch(CloudApi.ApiFailure failure){throw failure;}
        catch(java.io.IOException network){
          if(!api.offlineLogin(user,password))throw new java.io.IOException("Cloud unavailable and no valid offline login. "+network.getMessage());
        }
      },()->{if(!api.tenant.isEmpty()&&!page.equals("Login"))return;
        if(!api.tenant.isEmpty()){page="Home";render();}
      });
    });root.addView(signIn,margin(22));
    root.addView(text("Offline sign-in is limited to 8 hours after a verified cloud login on this device. Gateway payments need connectivity.",12,GREY,false),margin(16));
  }
  void render(){
    if(api.tenant.isEmpty()){login();return;}
    switch(page){
      case "POS":pos();break;
      case "Orders":orders();break;
      case "Kitchen":kitchen();break;
      case "Printers":printers();break;
      case "Settings":settings();break;
      default: home();break;
    }
  }
  String pesos(long cent){return String.format(Locale.US,"PHP %,.2f",cent/100.0);}
  void home(){
    shell("Command Center");
    JSONArray all=db.sales(api.tenant);
    int pending=0;long local=0;for(int i=0;i<all.length();i++){
      JSONObject o=all.optJSONObject(i);if(o==null)continue;
      if(!"synced".equals(o.optString("cloud_status")))pending++;
      local+=o.optLong("amount");
    }
    LinearLayout p=panel("Local terminal overview");
    line(p,"Sales saved: "+all.length(),"Locally captured value "+pesos(local));
    line(p,"Waiting / needs review: "+pending,"Offline orders are preserved in SQLite until reconciled.");
    line(p,"Menu: "+db.menus(api.tenant).length()+" items","Use Refresh Menu when connected.");
    p.addView(button("Sync Orders + Refresh Menu",()->sync()),margin(14));
    LinearLayout q=panel("Quick actions");
    q.addView(button("+ New Restaurant Order",()->{page="POS";render();}),margin(12));
    q.addView(secondary("Printer destinations",()->{page="Printers";render();}),margin(9));
    q.addView(text("Kitchen tickets use the configured kitchen destinations. Receipts use receipt destinations.",13,GREY,false),margin(14));
  }
  void sync(){
    if(!api.onlineSession){toast("Sign in online to sync.");return;}
    work(()->{
      String result=api.sync(db);
      api.refresh(db);
      runOnUiThread(()->toast(result));
    },()->render());
  }
  JSONObject menuItem(String id){
    JSONArray m=db.menus(api.tenant);for(int i=0;i<m.length();i++){
      JSONObject x=m.optJSONObject(i);if(x!=null&&id.equals(x.optString("id")))return x;
    }return null;
  }
  long cartTotal(){
    long total=0;for(Map.Entry<String,Integer> entry:cart.entrySet()){
      JSONObject item=menuItem(entry.getKey());
      if(item!=null)total+=item.optLong("price_minor")*entry.getValue();
    }return total;
  }
  void pos(){
    shell("New Order");
    LinearLayout servicePanel=panel("Service type");
    LinearLayout options=row();
    options.addView(secondary("Dine-in",()->{service="dine_in";pos();}),new LinearLayout.LayoutParams(0,-2,1));
    options.addView(secondary("Takeout",()->{service="takeout";table="";pos();}),new LinearLayout.LayoutParams(0,-2,1));
    servicePanel.addView(options,margin(10));
    servicePanel.addView(text("Selected: "+(service.equals("dine_in")?"Dine-in":"Takeout"),13,INK,false));
    JSONArray tables=db.tables(api.tenant);
    if(service.equals("dine_in")&&tables.length()>0){
      String[] labels=new String[tables.length()+1];labels[0]="Walk-in / No assigned table";
      int selection=0;for(int i=0;i<tables.length();i++){JSONObject t=tables.optJSONObject(i);labels[i+1]=t.optString("label");if(table.equals(t.optString("id")))selection=i+1;}
      Spinner select=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);select.setAdapter(adapter);
      select.setSelection(selection);select.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
        public void onNothingSelected(android.widget.AdapterView<?> parent){}
        public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){table=pos==0?"":tables.optJSONObject(pos-1).optString("id");}
      });servicePanel.addView(select);
    }
    LinearLayout products=panel("Menu");
    JSONArray m=db.menus(api.tenant);
    if(m.length()==0)line(products,"No cached menu","Sign in online and refresh the menu first.");
    for(int i=0;i<m.length();i++){
      JSONObject item=m.optJSONObject(i);if(item==null)continue;
      String id=item.optString("id");LinearLayout row=row();
      LinearLayout labels=column();labels.addView(text(item.optString("name"),15,INK,true));
      labels.addView(text(pesos(item.optInt("price_minor")),13,GREY,false));
      row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
      row.addView(secondary("+ Add",()->{cart.put(id,cart.getOrDefault(id,0)+1);pos();}));
      products.addView(row,margin(12));
    }
    LinearLayout basket=panel("Current order");
    if(cart.isEmpty())basket.addView(text("Tap + Add to select menu items.",13,GREY,false),margin(10));
    for(Map.Entry<String,Integer> x:new ArrayList<>(cart.entrySet())){
      JSONObject item=menuItem(x.getKey());if(item==null)continue;
      LinearLayout r=row();
      TextView name=text(item.optString("name")+" × "+x.getValue(),14,INK,false);
      r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
      r.addView(secondary("−",()->{int q=cart.getOrDefault(x.getKey(),1)-1;if(q<=0)cart.remove(x.getKey());else cart.put(x.getKey(),q);pos();}));
      r.addView(secondary("+",()->{cart.put(x.getKey(),cart.getOrDefault(x.getKey(),0)+1);pos();}));
      basket.addView(r,margin(10));
    }
    basket.addView(text("Item subtotal: "+pesos(cartTotal()),19,BLUE,true),margin(15));
    basket.addView(text("Cloud checkout may add service charge; totals verified during sync.",12,GREY,false));
    EditText notes=field("Order notes / kitchen instructions",orderNote);basket.addView(notes,margin(9));
    basket.addView(button("SEND ORDER TO KITCHEN",()->saveOrder(false,notes.getText().toString())),margin(14));
    basket.addView(secondary("CASH PAID + ACKNOWLEDGMENT",()->saveOrder(true,notes.getText().toString())),margin(10));
  }
  void saveOrder(boolean cash,String notes){
    if(cart.isEmpty()){toast("Add menu items first.");return;}
    if(!api.role.equals("owner")&&!api.role.equals("manager")&&!api.role.equals("cashier")){toast("No checkout permission");return;}
    if(cart.size()>60){toast("Maximum 60 item lines.");return;}
    // Native 8-hour offline authentication cannot be silently extended by making a sale.
    if(System.currentTimeMillis()>api.prefs().getLong("offline_until",0)){toast("Offline authorization expired. Reconnect and sign in.");return;}
    try{
      String id=UUID.randomUUID().toString();JSONArray lines=new JSONArray();StringBuilder kitchen=new StringBuilder();
      kitchen.append("*** KITCHEN ORDER ***\n").append(id).append("\n").append(service.toUpperCase(Locale.ROOT))
        .append(" Table: ").append(table.isEmpty()?"Walk-in":table).append("\n------------------------\n");
      StringBuilder receipt=new StringBuilder("PAYMENT ACKNOWLEDGMENT\n");
      receipt.append("PROVISIONAL - PENDING CLOUD SYNC\n").append(id).append("\n------------------------\n");
      long subtotal=0;
      for(Map.Entry<String,Integer> en:cart.entrySet()){
        JSONObject item=menuItem(en.getKey());if(item==null)throw new IllegalStateException("Menu item unavailable");
        JSONObject line=new JSONObject();line.put("menu_item_id",en.getKey());line.put("quantity",en.getValue());line.put("notes","");
        lines.put(line);
        long amount=item.getLong("price_minor")*en.getValue();subtotal+=amount;
        String name=item.optString("name");
        kitchen.append(en.getValue()).append(" x ").append(name).append("\n");
        receipt.append(en.getValue()).append(" x ").append(name).append("  ").append(pesos(amount)).append("\n");
      }
      kitchen.append("NOTES: ").append(notes).append("\n");
      receipt.append("------------------------\nLOCAL SUBTOTAL ").append(pesos(subtotal)).append("\n");
      receipt.append("CASH ACKNOWLEDGED LOCALLY\nNOT A BIR REGISTERED INVOICE\nNOT VALID FOR INPUT TAX CLAIM\n");
      JSONObject order=new JSONObject();order.put("client_request_id",id);
      order.put("service_type",service);
      if(service.equals("dine_in")&&!table.isEmpty())order.put("table_id",table);
      order.put("notes",notes);order.put("items",lines);
      if(subtotal>Integer.MAX_VALUE)throw new IllegalArgumentException("Cart too large");
      db.addSale(api.tenant,id,order,(int)subtotal,cash?"cash":null,kitchen.toString(),receipt.toString());
      cart.clear();orderNote="";
      page="Orders";render();
      runPrinterJobs();
      if(api.onlineSession)sync();
    }catch(Exception e){toast("Unable to save: "+e.getMessage());}
  }
  void orders(){
    shell("Orders & Offline Outbox");
    LinearLayout head=panel("Order sync");
    head.addView(button("Synchronize Pending Orders",()->sync()),margin(12));
    JSONArray orders=db.sales(api.tenant);
    if(orders.length()==0){line(head,"No local orders yet","Make your first sale from POS.");return;}
    for(int i=0;i<orders.length();i++){
      JSONObject sale=orders.optJSONObject(i);if(sale==null)continue;
      String id=sale.optString("id"),cloud=sale.optString("cloud_id","");
      LinearLayout p=panel("#"+id.substring(0,8).toUpperCase(Locale.ROOT));
      line(p,pesos(sale.optLong("amount"))+" • "+(sale.isNull("local_payment")?"UNPAID":"CASH"),
        "Sync: "+sale.optString("cloud_status")+(sale.optString("error").isEmpty()?"":"\n"+sale.optString("error")));
      if(!cloud.isEmpty()&&!sale.optString("cloud_status").equals("conflict")
          &&sale.isNull("local_payment")){
        p.addView(secondary("Pay with PayMongo • Online",()->paymongo(cloud)),margin(10));
      }
      p.addView(secondary("Reprint kitchen ticket (audited local copy)",()->reprint(sale,"kitchen")),margin(7));
      if(!sale.isNull("local_payment"))p.addView(secondary("Reprint acknowledgment",()->reprint(sale,"receipt")),margin(7));
    }
  }
  String slip(JSONObject sale,String role){
    try{
      JSONObject order=new JSONObject(sale.getString("json"));
      StringBuilder s=new StringBuilder();
      s.append(role.equals("kitchen")?"KITCHEN ORDER":"PAYMENT ACKNOWLEDGMENT").append("\n");
      s.append(sale.getString("id")).append("\n");
      JSONArray a=order.getJSONArray("items");for(int i=0;i<a.length();i++){
        JSONObject l=a.getJSONObject(i),m=menuItem(l.getString("menu_item_id"));
        s.append(l.getInt("quantity")).append(" x ").append(m==null?"Product":m.optString("name")).append("\n");
      }
      if(role.equals("receipt"))s.append("LOCAL TOTAL ").append(pesos(sale.getLong("amount"))).append("\nNON-BIR ACKNOWLEDGMENT\nNOT A SALES INVOICE\n");
      return s.toString();
    }catch(Exception e){return "Local reference "+sale.optString("id");}
  }
  void reprint(JSONObject sale,String role){
    new AlertDialog.Builder(this).setTitle("Reprint "+role+"?")
      .setMessage("This creates a new marked reprint job. Confirm the original was not fulfilled twice.")
      .setNegativeButton("Cancel",null)
      .setPositiveButton("Reprint",(v,w)->{
        try{db.manuallyReprint(sale.getString("id"),role,slip(sale,role));runPrinterJobs();}
        catch(Exception e){toast(e.getMessage());}
      }).show();
  }
  void paymongo(String cloud){
    if(!api.onlineSession){toast("Online sign-in required for payments.");return;}
    work(()->{
      String url=api.gatewayCheckout(cloud);
      runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));
    },()->{});
  }
  void kitchen(){
    shell("Kitchen Display");
    LinearLayout p=panel("Kitchen status board");
    p.addView(text("Cloud kitchen orders require internet. Offline kitchen tickets print immediately from local orders.",13,GREY,false),margin(10));
    if(!api.onlineSession){line(p,"Offline mode","The cloud queue is unavailable.");return;}
    p.addView(button("Refresh Kitchen",()->loadKitchen()),margin(10));
    loadKitchen();
  }
  void loadKitchen(){
    if(busy)return;
    work(()->{
      JSONArray orders=api.getList("/api/orders?status=active");
      runOnUiThread(()->showKitchenOrders(orders));
    },()->{});
  }
  void showKitchenOrders(JSONArray orders){
    if(!page.equals("Kitchen"))return;
    LinearLayout p=panel("Live queue • "+orders.length());
    for(int i=0;i<orders.length();i++){
      JSONObject o=orders.optJSONObject(i);if(o==null)continue;
      String status=o.optString("kitchen_status"),id=o.optString("id");
      if(status.equals("served"))continue;
      line(p,"#"+o.optString("order_number")+"  "+status.toUpperCase(Locale.ROOT),
        o.optString("service_type")+" • "+o.optString("table_label","Takeout"));
      String next=status.equals("queued")?"preparing":status.equals("preparing")?"ready":"served";
      p.addView(secondary("Mark "+next,()->{
        JSONObject change=new JSONObject();try{change.put("status",next);}catch(Exception ignored){}
        work(()->api.request("PATCH","/api/orders/"+id+"/kitchen",change),()->{
          if(page.equals("Kitchen"))loadKitchen();
        });
      }),margin(6));
    }
  }
  void printers(){
    shell("Multi-Printer Manager");
    LinearLayout intro=panel("Output destinations");
    intro.addView(text("Wi-Fi / LAN (TCP ESC/POS), paired Bluetooth SPP and USB OTG. Add multiple receipt and kitchen printers.",13,GREY,false),margin(10));
    intro.addView(button("+ Add printer",()->addPrinterDialog()),margin(12));
    intro.addView(secondary("Process unsent tickets",()->runPrinterJobs()),margin(8));
    JSONArray list=db.printers();
    for(int i=0;i<list.length();i++){
      JSONObject p=list.optJSONObject(i);if(p==null)continue;
      LinearLayout card=panel(p.optString("name"));
      line(card,p.optString("role").toUpperCase(Locale.ROOT)+" • "+p.optString("transport").toUpperCase(Locale.ROOT),
        p.optString("host")+p.optString("mac"));
      card.addView(secondary("Print test ticket",()->testPrinter(p)),margin(9));
      if(p.optString("transport").equals("usb"))card.addView(secondary("Grant USB permission",()->PrinterHub.requestUsb(this,p.optInt("vid"),p.optInt("pid"))),margin(8));
      card.addView(secondary("Remove printer",()->new AlertDialog.Builder(this).setTitle("Remove printer?")
        .setMessage("Previously attempted jobs are retained for audit.")
        .setNegativeButton("Cancel",null).setPositiveButton("Remove",(a,b)->{db.deletePrinter(p.optString("id"));printers();}).show()),margin(7));
    }
    intro.addView(text("Print jobs are claimed before transmission; delivery cannot be confirmed by most ESC/POS printers. Failed/uncertain jobs need an explicit reprint.",12,GREY,false),margin(12));
  }
  void addPrinterDialog(){
    LinearLayout layout=column();pad(layout,14);
    EditText name=field("Printer name e.g. Kitchen Grill","");layout.addView(name);
    Spinner role=new Spinner(this);role.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"receipt","kitchen"}));layout.addView(role);
    Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"lan","wifi","bluetooth","usb"}));layout.addView(type);
    EditText host=field("IP address (LAN/Wi-Fi)","192.168.1.100");layout.addView(host);
    EditText port=field("TCP port","9100");layout.addView(port);
    EditText mac=field("Paired Bluetooth MAC","");layout.addView(mac);
    EditText vid=field("USB Vendor ID (decimal)","0");layout.addView(vid);
    EditText pid=field("USB Product ID (decimal)","0");layout.addView(pid);
    new AlertDialog.Builder(this).setTitle("Register ESC/POS printer").setView(layout)
      .setNegativeButton("Cancel",null)
      .setPositiveButton("Save",(dialog,which)->{
        try{
          JSONObject p=new JSONObject();p.put("id",UUID.randomUUID().toString());p.put("name",name.getText().toString().trim());
          if(p.getString("name").isEmpty())throw new IllegalArgumentException("Printer name is required");
          p.put("role",role.getSelectedItem());p.put("transport",type.getSelectedItem());
          p.put("host",host.getText());p.put("port",Integer.parseInt(port.getText().toString()));
          p.put("mac",mac.getText());p.put("vid",Integer.parseInt(vid.getText().toString()));p.put("pid",Integer.parseInt(pid.getText().toString()));
          db.addPrinter(p);if(p.getString("transport").equals("bluetooth"))bluetoothPermission();
          if(p.getString("transport").equals("usb"))PrinterHub.requestUsb(this,p.getInt("vid"),p.getInt("pid"));
          printers();
        }catch(Exception e){toast("Invalid settings: "+e.getMessage());}
      }).show();
  }
  void bluetoothPermission(){
    if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},101);
  }
  void testPrinter(JSONObject p){
    if(p.optString("transport").equals("bluetooth"))bluetoothPermission();
    work(()->PrinterHub.send(this,p,PrinterHub.bytes("RESTAURANTPOS PRINTER TEST\n"+p.optString("name")
      +"\n"+new Date()+"\nNON-BIR TEST TICKET",true)),
      ()->toast("Print command completed. Confirm the physical ticket."));
  }
  void runPrinterJobs(){
    executor.execute(()->{
      JSONArray queue=db.waitingJobs(),printers=db.printers();
      int ok=0,uncertain=0;
      for(int i=0;i<queue.length();i++){
        JSONObject job=queue.optJSONObject(i);if(job==null)continue;
        JSONObject printer=null;
        for(int k=0;k<printers.length();k++){
          JSONObject p=printers.optJSONObject(k);
          if(p!=null&&p.optString("id").equals(job.optString("printer_id"))){printer=p;break;}
        }
        if(printer==null){continue;}
        String id=job.optString("id");if(!db.claimJob(id))continue;
        try{PrinterHub.send(this,printer,PrinterHub.bytes(job.getString("body"),true));
          db.resultJob(id,true,"");ok++;
        }catch(Exception e){db.resultJob(id,false,e.getMessage());uncertain++;}
      }
      final int a=ok,b=uncertain;
      runOnUiThread(()->{if(a>0||b>0)toast("Sent "+a+" print job(s). "+b+" uncertain; check printer before reprinting.");});
    });
  }
  void settings(){
    shell("Cloud & Device Settings");
    LinearLayout p=panel("Cloud endpoint");
    line(p,api.base,api.onlineSession?"Authenticated cloud session":"Offline session");
    p.addView(secondary("Sync now",()->sync()),margin(12));
    p.addView(secondary("Sign out / Change server",()->{
      api.cookie="";api.tenant="";api.onlineSession=false;login();
    }),margin(9));
    LinearLayout q=panel("Deployment checklist");
    line(q,"Neon PostgreSQL","Never put connection strings in the APK");
    line(q,"PayMongo Hosted Checkout","Backend secret and signed webhook verification are required");
    line(q,"Printers","Use ESC/POS; test each model for code pages, cut, and network routing");
    line(q,"Offline SQLite","Local data is not encrypted. Secure the device, avoid uninstalling, and back up before migration.");
    line(q,"Non-BIR acknowledgment","Issue legally required BIR invoices separately.");
  }
  @Override protected void onDestroy(){executor.shutdown();if(db!=null)db.close();super.onDestroy();}
}
