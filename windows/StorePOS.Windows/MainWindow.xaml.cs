using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using System.Net;
using Microsoft.Web.WebView2.Core;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using StorePOS.Windows.Core;
using StorePOS.Windows.Services;

namespace StorePOS.Windows;

public partial class MainWindow : Window
{
    readonly LocalStore _store=new();
    readonly SupabaseCloud _cloud=new();
    readonly SessionVault _vault;
    readonly string _deviceId;
    readonly ObservableCollection<CartLine> _cart=new();
    readonly DispatcherTimer _clock=new(){Interval=TimeSpan.FromSeconds(45)};
    readonly SemaphoreSlim _syncLock=new(1,1);
    ShopContext? _shop;
    LicenseSnapshot? _license;
    bool _online;

    public MainWindow() {
        InitializeComponent();
        Loaded+=async (_,_)=>await InitializeCaptchaAsync();
        _vault=new SessionVault(_store.Root);
        _deviceId=_store.GetOrCreateDeviceId();
        DeviceIdText.Text="Device ID: "+_deviceId;
        PrinterInput.Text=_store.GetSetting("printer")??"";
        var size=_store.GetSetting("paper")??"80";
        PaperWidthInput.SelectedIndex=size=="58"?0:1;
        AutoPrintInput.IsChecked=(_store.GetSetting("auto_print")??"true")=="true";
        CartGrid.ItemsSource=_cart;
        _cart.CollectionChanged+=(_,_)=>UpdateTotal();
        OutboxGrid.SelectionChanged+=(_,_)=>{
            if(OutboxGrid.SelectedItem is QueueItem item)ReceiptText.Text=item.Receipt;
        };
        _clock.Tick+=async (_,_)=>{
            if(_shop!=null)await SyncBestEffort();
        };
        _clock.Start();
        var cached=_vault.Load();
        if(cached!=null) {
            EmailInput.Text=cached.Session.Email;
            Status("Saved encrypted session found. Sign in or open saved offline session.");
        }else Status("Sign in to start. First-use cloud setup requires internet.");
    }

    // Same host and callback URI as StorePOS Android's existing
    // production Turnstile flow. Never run arbitrary remote JavaScript
    // under an app-origin or accept navigation to an untrusted HTTP host.
    const string ChallengeUrl="https://storepos.2023107337.workers.dev/turnstile.html?mode=signin&v=3";
    async Task InitializeCaptchaAsync() {
        try {
            await CaptchaWeb.EnsureCoreWebView2Async();
            var core=CaptchaWeb.CoreWebView2;
            core.Settings.AreDevToolsEnabled=false;
            core.Settings.AreDefaultContextMenusEnabled=false;
            core.NavigationStarting+=(_,args)=>{
                if(!Uri.TryCreate(args.Uri,UriKind.Absolute,out var uri)) {
                    args.Cancel=true;return;
                }
                if(uri.Scheme.Equals("storepos",StringComparison.OrdinalIgnoreCase)
                    && uri.Host.Equals("turnstile",StringComparison.OrdinalIgnoreCase)) {
                    args.Cancel=true;
                    var token=uri.Query.TrimStart('?').Split('&')
                        .Select(pair=>pair.Split('=',2))
                        .Where(pair=>pair.Length==2 && pair[0]=="token")
                        .Select(pair=>WebUtility.UrlDecode(pair[1]))
                        .FirstOrDefault();
                    if(!string.IsNullOrEmpty(token)&&token.Length<=4096) {
                        CaptchaInput.Password=token;
                        CaptchaStateText.Text="✓ Cloudflare challenge passed. Complete sign in promptly.";
                    }
                    return;
                }
                if(uri.Scheme!="https" ||
                    !uri.Host.Equals("storepos.2023107337.workers.dev",StringComparison.OrdinalIgnoreCase)) {
                    args.Cancel=true;
                    CaptchaStateText.Text="Blocked navigation outside StorePOS's verified security page.";
                }
            };
            CaptchaWeb.Source=new Uri(ChallengeUrl);
        }catch(Exception e) {
            CaptchaStateText.Text="Security challenge cannot open. Install/repair Microsoft Edge WebView2 Runtime: "+e.Message;
        }
    }
    void ReloadCaptcha_Click(object sender,RoutedEventArgs e) {
        CaptchaInput.Clear();
        if(CaptchaWeb.CoreWebView2!=null) {
            CaptchaStateText.Text="Loading fresh security challenge...";
            CaptchaWeb.CoreWebView2.Navigate(ChallengeUrl+"&refresh="+Guid.NewGuid().ToString("N"));
        }else _=InitializeCaptchaAsync();
    }
    void Status(string msg) {StatusText.Text=msg;UpdateConnectivity();}
    void UpdateConnectivity() {
        ConnectionText.Text=_online?"● ONLINE":"● OFFLINE / LOCAL";
        ConnectionText.Foreground=(Brush)new BrushConverter().ConvertFrom(_online?"#61E1A1":"#F9C875")!;
        if(_shop!=null) {
            ShopName.Text=_shop.ShopName;
            OperatorText.Text=_shop.Role.ToUpperInvariant()+" • "+_shop.UserId.ToString("N")[..8];
            LicenseText.Text=_license==null?"License not verified":
                (_license.Valid?$"Licensed · {_license.Plan} · offline grace {_license.GraceDays} days":
                  "License needs activation");
            var entries=_store.GetOutbox(_shop.ShopId);
            PendingSidebar.Text=$"{entries.Count(x=>x.Status=="queued")} queued • "+
                $"{entries.Count(x=>x.Status=="review")} need review";
        }
    }
    static decimal DecimalValue(string? value,string name) {
        if(decimal.TryParse(value,NumberStyles.Number,CultureInfo.CurrentCulture,out var n) ||
           decimal.TryParse(value,NumberStyles.Number,CultureInfo.InvariantCulture,out n))return n;
        throw new InvalidOperationException("Enter a valid "+name+".");
    }
    void RequireTrading(bool inventory=false,bool manager=false) {
        if(_shop==null || _license==null)throw new InvalidOperationException("Sign in and activate your device first.");
        if(!_license.Valid)throw new InvalidOperationException("A valid StorePOS license is required.");
        // Always enforce cached license expiry and monotonic wall-clock policy,
        // including when Windows still shows a previously online state.
        if(!_license.AllowsOffline(DateTimeOffset.UtcNow))
            throw new InvalidOperationException("License expired, offline grace elapsed, or device clock moved backward. Reconnect for verification.");
        if(_store.TrustedEpoch(_shop.ShopId)==null)
            throw new InvalidOperationException("Sign in online once to cache the catalog and shop generation.");
        var role=_shop.Role;
        if(manager&&!AuditRules.CanEdit(role))throw new InvalidOperationException("Manager permission required.");
        if(inventory&&!AuditRules.CanStock(role))throw new InvalidOperationException("Inventory access denied.");
        if(!inventory&&!manager&&!AuditRules.CanSell(role))
            throw new InvalidOperationException("Cashier access denied.");
        var now=DateTimeOffset.UtcNow;
        _license=_license with {LastSeenUtc=now};
        SaveIdentity();
    }
    void SaveIdentity() {
        if(_shop!=null && _license!=null && _cloud.Session!=null)
            _vault.Save(_cloud.Session,_shop,_license,_deviceId);
    }
    void ShowError(Exception ex) {
        Status("⚠ "+ex.Message);
        MessageBox.Show(this,ex.Message,"StorePOS",MessageBoxButton.OK,MessageBoxImage.Warning);
    }
    void OpenWorkspace() {
        LoginOverlay.Visibility=Visibility.Collapsed;
        Pages.SelectedIndex=_license?.Valid==true?0:3;
        RefreshLocal();
        UpdateConnectivity();
    }
    void RefreshLocal() {
        if(_shop==null)return;
        var data=_store.GetProducts(_shop.ShopId);
        var query=SearchInput.Text.Trim();
        ProductsGrid.ItemsSource=data.Where(p=>p.IsActive &&
            (query.Length==0 || p.Name.Contains(query,StringComparison.OrdinalIgnoreCase) ||
             p.Sku.Contains(query,StringComparison.OrdinalIgnoreCase) ||
             p.Barcode?.Contains(query,StringComparison.OrdinalIgnoreCase)==true)).ToList();
        InventoryGrid.ItemsSource=data;
        OutboxGrid.ItemsSource=_store.GetOutbox(_shop.ShopId);
        UpdateTotal();UpdateConnectivity();
    }
    void UpdateTotal() {
        CartCount.Text=_cart.Count+" product line(s)";
        var total=_cart.Sum(x=>x.Total);
        var rate=_shop==null?0:_store.TaxRate(_shop.ShopId);
        var tax=decimal.Round(total*rate/100,2,MidpointRounding.AwayFromZero);
        TotalText.Text=$"TOTAL  PHP {total+tax:N2}";
    }
    async Task<bool> CheckOnline() {
        if(_shop==null)return false;
        try {
            await _cloud.EnsureFresh();
            var checkedLicense=await _cloud.ValidateLicense(_shop.ShopId,_deviceId);
            _license=checkedLicense;
            _online=true;
            SaveIdentity();
            if(!_license.Valid) {
                Pages.SelectedIndex=3;
                Status("License validation failed: activate your StorePOS Windows device online.");
                return false;
            }
            return true;
        }catch(Exception ex) when(ex is HttpRequestException or TaskCanceledException or CloudException) {
            _online=false; UpdateConnectivity();return false;
        }
    }
    async Task RefreshCatalog() {
        if(_shop==null || !_online || !_license!.Valid)return;
        if(_store.HasOutstanding(_shop.ShopId)) {
            Status("Local pending/review transactions retained. Cloud catalog refresh blocked.");
            return;
        }
        var cloud=await _cloud.DownloadCatalog(_shop.ShopId);
        _store.SaveTrustedCatalog(_shop.ShopId,cloud.Epoch,cloud.Products,cloud.TaxRate);
        RefreshLocal();
        Status($"Catalog synchronized: {cloud.Products.Count} products.");
        SaveIdentity();
    }
    async void SignIn_Click(object sender,RoutedEventArgs e) {
        SignInButton.IsEnabled=false;LoginError.Text="";
        try {
            var session=await _cloud.SignIn(EmailInput.Text.Trim(),PasswordInput.Password,
                CaptchaInput.Password.Trim());
            var shop=await _cloud.GetStorePosShop();
            _shop=shop;_online=true;
            _license=await _cloud.ValidateLicense(shop.ShopId,_deviceId);
            SaveIdentity();
            if(_license.Valid)await RefreshCatalog();
            OpenWorkspace();
            Status(_license.Valid?"StorePOS Windows signed in.": "Authenticated. Activate this Windows device in Settings.");
        }catch(Exception ex) {
            _online=false;
            LoginError.Text=ex.Message+"\nIf CAPTCHA is enabled for this project, the Windows sign-in flow needs a valid hosted Turnstile token.";
            UpdateConnectivity();
        }finally {SignInButton.IsEnabled=true; PasswordInput.Clear();}
    }
    void OpenOffline_Click(object sender,RoutedEventArgs e) {
        try {
            var stored=_vault.Load()??throw new InvalidOperationException("No encrypted session is saved on this PC.");
            if(stored.DeviceId!=_deviceId)
                throw new InvalidOperationException("Saved license belongs to another Windows installation.");
            if(!stored.License.AllowsOffline(DateTimeOffset.UtcNow))
                throw new InvalidOperationException("Offline access not authorized or grace expired. Connect once to renew.");
            if(_store.TrustedEpoch(stored.Shop.ShopId)==null ||
               _store.GetProducts(stored.Shop.ShopId).Count==0)
                throw new InvalidOperationException("No trusted catalog on this device yet.");
            _shop=stored.Shop;_license=stored.License;_cloud.SetSession(stored.Session);_online=false;
            OpenWorkspace();
            Status("Opened signed-in cached shop OFFLINE. Sales and inventory are stored locally.");
        }catch(Exception ex){LoginError.Text=ex.Message;}
    }
    async Task SyncBestEffort() {
        if(_shop==null || !await _syncLock.WaitAsync(0))return;
        try {
            if(!await CheckOnline())return;
            foreach(var op in _store.GetOutbox(_shop.ShopId).Where(x=>x.Status=="queued")) {
                // Do not attribute another cashier's sale/adjustment to this user.
                if(_cloud.Session?.UserId!=op.ActorId)continue;
                try {
                    await _cloud.Replay(op);
                    _store.UpdateQueue(op.Id,"synced",null);
                }catch(CloudException ex) when(ex.Transient) {
                    _online=false;Status("Cloud temporarily unavailable. Local operations preserved.");break;
                }catch(HttpRequestException) {
                    _online=false;Status("Offline again. Cloud sync will retry.");break;
                }catch(TaskCanceledException) {
                    _online=false;Status("Cloud timeout; retry with same idempotency key.");break;
                }catch(Exception ex) {
                    _store.UpdateQueue(op.Id,"review",ex.Message);
                    Status("Conflicting inventory/sale preserved for manager review: "+op.Id);
                    // Stop the batch; later operations may depend on this one.
                    break;
                }
            }
            if(_online && !_store.HasOutstanding(_shop.ShopId))await RefreshCatalog();
            else RefreshLocal();
            SaveIdentity();
        }catch(Exception ex){Status("Sync retry pending: "+ex.Message);}
        finally {_syncLock.Release();UpdateConnectivity();}
    }
    async void SyncNow_Click(object sender,RoutedEventArgs e)=>await SyncBestEffort();
    void OpenPos(object sender,RoutedEventArgs e)=>Pages.SelectedIndex=0;
    void OpenInventory(object sender,RoutedEventArgs e)=>Pages.SelectedIndex=1;
    void OpenHistory(object sender,RoutedEventArgs e){Pages.SelectedIndex=2;RefreshLocal();}
    void OpenSettings(object sender,RoutedEventArgs e)=>Pages.SelectedIndex=3;

    void FindProducts_Click(object sender,RoutedEventArgs e)=>RefreshLocal();
    void SearchInput_KeyDown(object sender,KeyEventArgs e) {
        if(e.Key!=Key.Enter)return;
        RefreshLocal();
        if(ProductsGrid.Items.Count==1 && ProductsGrid.Items[0] is Product p)
            ProductsGrid.SelectedItem=p;
        if(ProductsGrid.SelectedItem is Product)AddToCart_Click(sender,new RoutedEventArgs());
    }
    void AddToCart_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading();
            var p=ProductsGrid.SelectedItem as Product ??
                throw new InvalidOperationException("Select a product or scan its barcode first.");
            AuditRules.RequireSimple(p);
            var qty=DecimalValue(QuantityInput.Text,"quantity");
            if(qty<=0 || decimal.Truncate(qty)!=qty)
                throw new InvalidOperationException("Use a positive whole-number quantity.");
            if(_cart.Any(c=>c.ProductId==p.Id)) {
                var existing=_cart.First(x=>x.ProductId==p.Id);
                _cart.Remove(existing); qty+=existing.Qty;
            }
            if(p.TrackStock && qty>p.Stock)
                throw new InvalidOperationException("Insufficient local quantity.");
            _cart.Add(new CartLine(p.Id,p.Sku,p.Name,p.Price,qty));
        }catch(Exception ex){ShowError(ex);}
    }
    void RemoveCart_Click(object sender,RoutedEventArgs e) {
        if(CartGrid.SelectedItem is CartLine item)_cart.Remove(item);
    }
    async void Checkout_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading();
            CheckoutButton.IsEnabled=false;
            var tendered=DecimalValue(TenderInput.Text,"cash tendered");
            var receipt=_store.QueueCashSale(_shop!,_cart.ToList(),tendered);
            _cart.Clear();
            ReceiptText.Text=receipt.Receipt;
            RefreshLocal();
            Status("CASH SALE SAVED LOCALLY: "+receipt.Id+". Printing and sync are independent.");
            if(AutoPrintInput.IsChecked==true) {
                try {
                    PrintSavedReceipt(receipt);
                }catch(Exception printFailure) {
                    MessageBox.Show(this,
                        "Sale SAVED. Printer failed: "+printFailure.Message+
                        "\nReprint via Sales & Sync. Do NOT charge the customer twice.",
                        "Receipt printer",MessageBoxButton.OK,MessageBoxImage.Warning);
                }
            }
            await SyncBestEffort();
        }catch(Exception ex){ShowError(ex);}
        finally {CheckoutButton.IsEnabled=true;}
    }
    int WidthMm=>PaperWidthInput.SelectedIndex==0?58:80;
    void PrintSavedReceipt(QueueItem item) {
        if(string.IsNullOrWhiteSpace(item.Receipt))
            throw new InvalidOperationException("This operation has no saved receipt.");
        ReceiptPrinter.PrintRaw(PrinterInput.Text.Trim(),item.Receipt,WidthMm);
    }
    void Reprint_Click(object sender,RoutedEventArgs e) {
        try {
            if(OutboxGrid.SelectedItem is not QueueItem sale)
                throw new InvalidOperationException("Select a saved cash sale from history first.");
            PrintSavedReceipt(sale);
            Status("Reprint sent to Windows spooler. This does not create a new sale.");
        }catch(Exception ex){ShowError(ex);}
    }
    void AdjustStock_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading(inventory:true);
            var p=InventoryGrid.SelectedItem as Product ??
                throw new InvalidOperationException("Choose an inventory product first.");
            var delta=DecimalValue(StockDeltaInput.Text,"stock change");
            var kind=(StockReasonInput.SelectedItem as ComboBoxItem)?.Content?.ToString()??"adjustment";
            _store.QueueStock(_shop!,p.Id,delta,kind,StockNoteInput.Text.Trim());
            RefreshLocal();Status("Inventory adjustment queued locally. Sync when online.");
            _=SyncBestEffort();
        }catch(Exception ex){ShowError(ex);}
    }
    void Stocktake_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading(inventory:true,manager:true);
            var p=InventoryGrid.SelectedItem as Product ??
                throw new InvalidOperationException("Select the counted product first.");
            var count=DecimalValue(CountInput.Text,"physical count");
            _store.QueuePhysicalCount(_shop!,p.Id,count);
            RefreshLocal();Status("Physical count saved. Cloud rejects stale stock for manager review.");
            _=SyncBestEffort();
        }catch(Exception ex){ShowError(ex);}
    }
    void AddProduct_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading(inventory:true,manager:true);
            var price=DecimalValue(PriceInput.Text,"selling price");
            var opening=DecimalValue(OpeningInput.Text,"opening stock");
            _store.QueueCreate(_shop!,SkuInput.Text,NameInput.Text,price,opening);
            RefreshLocal();Status("New product saved locally (stable UUID).");
            _=SyncBestEffort();
        }catch(Exception ex){ShowError(ex);}
    }
    void EditProduct_Click(object sender,RoutedEventArgs e) {
        try {
            RequireTrading(inventory:true,manager:true);
            var p=InventoryGrid.SelectedItem as Product ??
                throw new InvalidOperationException("Select the product to edit.");
            var name=string.IsNullOrWhiteSpace(NameInput.Text)?p.Name:NameInput.Text;
            var price=string.IsNullOrWhiteSpace(PriceInput.Text)?p.Price:DecimalValue(PriceInput.Text,"price");
            _store.QueueEdit(_shop!,p.Id,name,price);
            RefreshLocal();Status("Product edit queued, pending conflict-aware cloud update.");
            _=SyncBestEffort();
        }catch(Exception ex){ShowError(ex);}
    }
    void SavePrinter_Click(object sender,RoutedEventArgs e) {
        _store.SetSetting("printer",PrinterInput.Text.Trim());
        _store.SetSetting("paper",WidthMm.ToString());
        _store.SetSetting("auto_print",(AutoPrintInput.IsChecked==true).ToString().ToLowerInvariant());
        Status("Printer name/paper saved locally. Test reprint before live checkout.");
    }
    async void Activate_Click(object sender,RoutedEventArgs e) {
        try {
            if(_shop==null)throw new InvalidOperationException("Sign in online first.");
            var key=ActivationInput.Password.Trim();
            if(key.Length==0)throw new InvalidOperationException("Enter the StorePOS license key.");
            _license=await _cloud.ValidateLicense(_shop.ShopId,_deviceId,true,key);
            if(!_license.Valid)throw new InvalidOperationException("License not activated: "+_license.Status);
            _online=true;SaveIdentity();ActivationInput.Clear();
            await RefreshCatalog();Pages.SelectedIndex=0;
            Status("Windows device license activated successfully.");
        }catch(Exception ex){ShowError(ex);}
    }
    void SignOut_Click(object sender,RoutedEventArgs e) {
        if(_shop!=null && _store.HasOutstanding(_shop.ShopId) &&
           MessageBox.Show(this,
               "Local changes are still pending. Signing out keeps them but only the original staff can sync. Continue?",
               "Pending transactions",MessageBoxButton.YesNo)!=MessageBoxResult.Yes)return;
        _vault.Clear();_cloud.SetSession(null);_shop=null;_license=null;_online=false;_cart.Clear();
        LoginOverlay.Visibility=Visibility.Visible;PasswordInput.Clear();
        Status("Signed out. Local sales/receipts are preserved, not deleted.");
    }
}
