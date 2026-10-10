using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;
using StorePOS.Windows.Core;

namespace StorePOS.Windows.Services;

/// <summary>
/// Calls the *existing StorePOS* Supabase Auth and PostgREST APIs with a real
/// user JWT. Never uses the service-role key or accesses app_private directly.
/// </summary>
public sealed class SupabaseCloud
{
    readonly HttpClient _http=new(){BaseAddress=new Uri(StorePOSConfig.Url+"/"),Timeout=TimeSpan.FromSeconds(20)};
    public AuthSession? Session {get;private set;}
    public void SetSession(AuthSession? session)=>Session=session;

    async Task<JsonElement> Send(HttpMethod method,string path,object? body=null,bool auth=true,CancellationToken ct=default) {
        using var request=new HttpRequestMessage(method,path);
        request.Headers.TryAddWithoutValidation("apikey",StorePOSConfig.PublishableKey);
        request.Headers.TryAddWithoutValidation("X-Client-Info","StorePOS-Windows/1.0.0");
        if(auth && Session is { AccessToken.Length:>0 } s)
            request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",s.AccessToken);
        if(body!=null)request.Content=JsonContent.Create(body,options:StorePOSConfig.Json);
        using var res=await _http.SendAsync(request,ct);
        var payload=await res.Content.ReadAsStringAsync(ct);
        if(!res.IsSuccessStatusCode) {
            var description=payload;
            try {
                using var parsed=JsonDocument.Parse(payload);
                var root=parsed.RootElement;
                description=Read(root,"message")??Read(root,"msg")??Read(root,"error_description")??payload;
            }catch(JsonException) { }
            throw new CloudException((int)res.StatusCode,description);
        }
        if(string.IsNullOrWhiteSpace(payload))return JsonDocument.Parse("null").RootElement.Clone();
        using var document=JsonDocument.Parse(payload);
        return document.RootElement.Clone();
    }
    static string? Read(JsonElement value,string key) =>
        value.ValueKind==JsonValueKind.Object && value.TryGetProperty(key,out var v)
            ? v.ToString():null;
    static Guid GuidField(JsonElement e,string name)=>Guid.Parse(Read(e,name)??throw new InvalidOperationException("Missing "+name));
    static decimal Num(JsonElement e,string name) =>
        decimal.TryParse(Read(e,name),System.Globalization.NumberStyles.Number,
            System.Globalization.CultureInfo.InvariantCulture,out var n)?n:0;
    static bool Flag(JsonElement e,string name,bool fallback=false)=>
        bool.TryParse(Read(e,name),out var value)?value:fallback;

    public async Task<AuthSession> SignIn(string email,string password,string? captchaToken=null) {
        var data=new Dictionary<string,object> {["email"]=email,["password"]=password,["gotrue_meta_security"] = new {}};
        if(!string.IsNullOrWhiteSpace(captchaToken))data["gotrue_meta_security"]=new {captcha_token=captchaToken.Trim()};
        var json=await Send(HttpMethod.Post,"auth/v1/token?grant_type=password",data,false);
        var session=ParseSession(json,email);
        Session=session;
        return session;
    }

    public async Task<AuthSession> Refresh() {
        var old=Session ?? throw new InvalidOperationException("Sign in is required.");
        var json=await Send(HttpMethod.Post,"auth/v1/token?grant_type=refresh_token",
            new{refresh_token=old.RefreshToken},false);
        Session=ParseSession(json,old.Email);
        return Session;
    }
    static AuthSession ParseSession(JsonElement json,string email) {
        var user=json.GetProperty("user");
        var expires=json.GetProperty("expires_in").GetInt32();
        return new AuthSession(
            json.GetProperty("access_token").GetString()!,
            json.GetProperty("refresh_token").GetString()!,
            Guid.Parse(user.GetProperty("id").GetString()!),
            Read(user,"email")??email,
            DateTimeOffset.UtcNow.AddSeconds(expires)
        );
    }
    public async Task EnsureFresh() {
        if(Session==null)throw new InvalidOperationException("Sign in to sync to cloud.");
        if(Session.ExpiresAt <= DateTimeOffset.UtcNow.AddMinutes(2))await Refresh();
    }

    public async Task<ShopContext> GetStorePosShop() {
        await EnsureFresh();
        var user=Session!.UserId;
        var memberships=await Send(HttpMethod.Get,
            $"rest/v1/shop_members?select=shop_id,role&user_id=eq.{user}&is_active=eq.true");
        foreach(var m in memberships.EnumerateArray()) {
            var id=GuidField(m,"shop_id");
            var shops=await Send(HttpMethod.Get,
                $"rest/v1/shops?select=id,name,app_code&id=eq.{id}");
            if(shops.GetArrayLength()==0)continue;
            var first=shops[0];
            if(Read(first,"app_code")!="storepos")continue;
            return new ShopContext(id,Read(first,"name")??"StorePOS",Read(m,"role")??"cashier",user);
        }
        throw new InvalidOperationException("No active StorePOS shop membership. Ask your shop owner.");
    }

    public async Task<(Guid Epoch,List<Product> Products,decimal TaxRate)> DownloadCatalog(Guid shopId) {
        await EnsureFresh();
        var old=await Epoch(shopId);
        var result=await Send(HttpMethod.Get,
            $"rest/v1/products?select=*&shop_id=eq.{shopId}&order=name.asc");
        var settings=await Send(HttpMethod.Get,
            $"rest/v1/shop_settings?select=tax_enabled,default_tax_rate&shop_id=eq.{shopId}");
        var updated=await Epoch(shopId);
        if(old!=updated)throw new InvalidOperationException("Shop catalog reset while downloading. Retry.");
        var tax=settings.GetArrayLength()>0 && Flag(settings[0],"tax_enabled")
            ? Num(settings[0],"default_tax_rate"):0;
        var products=new List<Product>();
        foreach(var p in result.EnumerateArray()) {
            var complex=Flag(p,"is_weighed")||Flag(p,"batch_tracked")||Flag(p,"serial_tracked")
                ||Read(p,"retail_parent_id") is {Length:>0} parent && parent!="null"
                ||Num(p,"wholesale_min")>0;
            products.Add(new Product(
                GuidField(p,"id"),GuidField(p,"shop_id"),
                Read(p,"sku")??"",Read(p,"name")??"",Read(p,"barcode"),
                Num(p,"selling_price"),Num(p,"cost_price"),Num(p,"stock_quantity"),Num(p,"reorder_level"),
                Flag(p,"is_active",true),Flag(p,"track_stock",true),
                Read(p,"category_id"),Read(p,"brand"),Read(p,"part_number"),
                Read(p,"unit")??"pc",Read(p,"item_type")??"product",complex
            ));
        }
        return (updated,products,tax);
    }
    public async Task<Guid> Epoch(Guid id) {
        var a=await Send(HttpMethod.Get,
            $"rest/v1/storepos_hybrid_epochs?select=epoch&shop_id=eq.{id}");
        if(a.GetArrayLength()!=1)throw new InvalidOperationException("No verified catalog epoch. Offline mode disabled.");
        return GuidField(a[0],"epoch");
    }

    public async Task<LicenseSnapshot> ValidateLicense(Guid shopId,string deviceId,bool activate=false,string key="") {
        await EnsureFresh();
        var payload=new Dictionary<string,object> {
            ["p_shop_id"]=shopId,["p_device_id"]=deviceId,
            ["p_device_name"]=Environment.MachineName,["p_app_version"]="Windows 1.0.0"
        };
        if(activate)payload["p_license_key"]=key;
        var response=await Rpc(activate?"activate_device_access":"validate_device_access",payload);
        if(response.ValueKind!=JsonValueKind.Array || response.GetArrayLength()==0)
            throw new InvalidOperationException("License server returned no result.");
        var item=response[0];
        var valid=Flag(item,"valid");
        var expiration=DateTimeOffset.TryParse(Read(item,"expires_at"),out var date)
            ? date:(DateTimeOffset?)null;
        var grace=int.TryParse(Read(item,"offline_grace_days"),out var days)?days:0;
        return new LicenseSnapshot(valid,Read(item,"plan_code"),Read(item,"status")??"unknown",
            DateTimeOffset.UtcNow,expiration,grace,DateTimeOffset.UtcNow);
    }

    public async Task<JsonElement> Rpc(string name,object payload,CancellationToken ct=default) {
        await EnsureFresh();
        return await Send(HttpMethod.Post,"rest/v1/rpc/"+name,payload,true,ct);
    }

    public async Task Replay(QueueItem op) {
        if(Session?.UserId!=op.ActorId)
            throw new InvalidOperationException("Original cashier/operator must sign in before syncing this operation.");
        var data=JsonNode.Parse(op.Payload)??throw new InvalidOperationException("Invalid local operation JSON");
        if(op.Kind=="sale") {
            await Rpc("storepos_hybrid_reconcile_sale",new {
                p_client_key=op.Id,p_shop_id=op.ShopId,p_catalog_epoch=op.Epoch,
                p_cashier_id=op.ActorId,p_payload=data
            });
        } else {
            await Rpc("storepos_reconcile_inventory",new {
                p_operation_id=op.Id,p_shop_id=op.ShopId,p_actor_id=op.ActorId,
                p_epoch=op.Epoch,p_kind=op.Kind,p_data=data
            });
        }
    }
}
public sealed class CloudException(int code,string message):Exception(message) {
    public int StatusCode {get;}=code;
    public bool Transient=>code==408||code==429||code>=500;
}
