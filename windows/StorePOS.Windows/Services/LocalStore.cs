using System.IO;
using System.Text.Json;
using Microsoft.Data.Sqlite;
using StorePOS.Windows.Core;

namespace StorePOS.Windows.Services;

/// <summary>
/// Per-user SQLite WAL store. The immutable outbox and optimistic product
/// cache must ALWAYS commit in the same transaction before printing/sync.
/// </summary>
public sealed class LocalStore
{
    readonly string _database;
    public string Root {get;}
    public LocalStore(string? isolatedDataDirectory=null) {
        Root=isolatedDataDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Azurate","StorePOS.Windows");
        Directory.CreateDirectory(Root);
        _database=Path.Combine(Root,"storepos.sqlite3");
        using var db=Connect();
        using var cmd=db.CreateCommand();
        cmd.CommandText="""
            CREATE TABLE IF NOT EXISTS metadata (
              key TEXT PRIMARY KEY, value TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS products(
              id TEXT PRIMARY KEY,shop_id TEXT NOT NULL,
              sku TEXT NOT NULL,barcode TEXT,name TEXT NOT NULL,
              payload TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_win_products_shop ON products(shop_id);
            CREATE TABLE IF NOT EXISTS outbox(
              id TEXT PRIMARY KEY,shop_id TEXT NOT NULL,actor_id TEXT NOT NULL,
              epoch TEXT NOT NULL,kind TEXT NOT NULL,
              payload TEXT NOT NULL,receipt TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL DEFAULT 'queued',
              error TEXT,created_utc TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_win_outbox_shop_time
              ON outbox(shop_id,status,created_utc);
            """;
        cmd.ExecuteNonQuery();
    }
    SqliteConnection Connect() {
        var db=new SqliteConnection(new SqliteConnectionStringBuilder {
            DataSource=_database,Mode=SqliteOpenMode.ReadWriteCreate,
            Cache=SqliteCacheMode.Shared
        }.ToString());
        db.Open();
        using var pragma=db.CreateCommand();
        pragma.CommandText="PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;";
        pragma.ExecuteNonQuery();
        return db;
    }
    static void Execute(SqliteConnection db,SqliteTransaction transaction,string sql,
        params (string Key,object? Value)[] parameters) {
        using var cmd=db.CreateCommand();
        cmd.Transaction=transaction;
        cmd.CommandText=sql;
        foreach(var p in parameters)cmd.Parameters.AddWithValue(p.Key,p.Value??DBNull.Value);
        cmd.ExecuteNonQuery();
    }
    public string GetOrCreateDeviceId() {
        var existing=GetSetting("device_id");
        if(!string.IsNullOrWhiteSpace(existing))return existing;
        var value="STOREPOS-WIN-"+Guid.NewGuid().ToString("N");
        SetSetting("device_id",value); return value;
    }
    public string? GetSetting(string key) {
        using var db=Connect();
        using var c=db.CreateCommand();
        c.CommandText="SELECT value FROM metadata WHERE key=$k";
        c.Parameters.AddWithValue("$k",key);
        return c.ExecuteScalar()?.ToString();
    }
    public void SetSetting(string key,string value) {
        using var db=Connect();
        using var c=db.CreateCommand();
        c.CommandText="INSERT INTO metadata(key,value) VALUES($k,$v) ON CONFLICT(key) DO UPDATE SET value=excluded.value";
        c.Parameters.AddWithValue("$k",key);c.Parameters.AddWithValue("$v",value);
        c.ExecuteNonQuery();
    }
    public Guid? TrustedEpoch(Guid shopId)=>
        Guid.TryParse(GetSetting("epoch:"+shopId),out var epoch)?epoch:null;
    public decimal TaxRate(Guid shopId)=>
        decimal.TryParse(GetSetting("tax:"+shopId),System.Globalization.NumberStyles.Any,
            System.Globalization.CultureInfo.InvariantCulture,out var v)?v:0;

    public bool HasOutstanding(Guid shopId)=>GetOutbox(shopId).Any(o=>o.Status is "queued" or "review");
    public void SaveTrustedCatalog(Guid shopId,Guid epoch,IReadOnlyList<Product> products,decimal taxRate) {
        if(epoch==Guid.Empty)throw new InvalidOperationException("Cannot trust unverified catalog.");
        using var db=Connect(); using var tx=db.BeginTransaction();
        using var check=db.CreateCommand();
        check.Transaction=tx;check.CommandText="SELECT COUNT(*) FROM outbox WHERE shop_id=$s AND status IN ('queued','review')";
        check.Parameters.AddWithValue("$s",shopId.ToString());
        if(Convert.ToInt64(check.ExecuteScalar())>0)
            throw new InvalidOperationException("Unsynced local changes must be reconciled before cloud catalog refresh.");
        Execute(db,tx,"DELETE FROM products WHERE shop_id=$s",("$s",shopId.ToString()));
        foreach(var p in products)SaveProduct(db,tx,p);
        Execute(db,tx,"INSERT INTO metadata(key,value) VALUES($k,$v) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            ("$k","epoch:"+shopId),("$v",epoch.ToString()));
        Execute(db,tx,"INSERT INTO metadata(key,value) VALUES($k,$v) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            ("$k","tax:"+shopId),("$v",taxRate.ToString(System.Globalization.CultureInfo.InvariantCulture)));
        tx.Commit();
    }
    static void SaveProduct(SqliteConnection db,SqliteTransaction tx,Product p)=>
        Execute(db,tx,"""
            INSERT INTO products(id,shop_id,sku,barcode,name,payload)
            VALUES($id,$shop,$sku,$barcode,$name,$body)
            ON CONFLICT(id) DO UPDATE SET sku=excluded.sku,
            barcode=excluded.barcode,name=excluded.name,payload=excluded.payload
            """,("$id",p.Id.ToString()),("$shop",p.ShopId.ToString()),
            ("$sku",p.Sku),("$barcode",p.Barcode),("$name",p.Name),
            ("$body",JsonSerializer.Serialize(p,StorePOSConfig.Json)));

    public List<Product> GetProducts(Guid shopId) {
        using var db=Connect();using var cmd=db.CreateCommand();
        cmd.CommandText="SELECT payload FROM products WHERE shop_id=$shop ORDER BY name COLLATE NOCASE";
        cmd.Parameters.AddWithValue("$shop",shopId.ToString());
        using var r=cmd.ExecuteReader();
        var result=new List<Product>();
        while(r.Read()) {
            var product=JsonSerializer.Deserialize<Product>(r.GetString(0),StorePOSConfig.Json);
            if(product!=null)result.Add(product);
        }
        return result;
    }
    public Product Find(Guid shopId,Guid productId)=>
        GetProducts(shopId).FirstOrDefault(p=>p.Id==productId)
        ??throw new InvalidOperationException("Product is not in the trusted local catalog.");

    static void Queue(SqliteConnection db,SqliteTransaction tx,QueueItem op)=>
        Execute(db,tx,"""
            INSERT INTO outbox(id,shop_id,actor_id,epoch,kind,payload,receipt,status,created_utc)
            VALUES($id,$shop,$actor,$epoch,$kind,$payload,$receipt,'queued',$created)
            """,("$id",op.Id.ToString()),("$shop",op.ShopId.ToString()),
            ("$actor",op.ActorId.ToString()),("$epoch",op.Epoch.ToString()),
            ("$kind",op.Kind),("$payload",op.Payload),("$receipt",op.Receipt),
            ("$created",op.CreatedAt.UtcDateTime.ToString("O")));

    static object BeforeAfter(Product p)=>p.ToCloud();
    static string ToJson(object obj)=>JsonSerializer.Serialize(obj,StorePOSConfig.Json);

    public QueueItem QueueCashSale(ShopContext ctx,IReadOnlyList<CartLine> cart,decimal tendered,int paperWidth=80) {
        if(!AuditRules.CanSell(ctx.Role))throw new InvalidOperationException("Cashier permission required.");
        var epoch=TrustedEpoch(ctx.ShopId)
            ??throw new InvalidOperationException("Download a trusted catalog while online first.");
        if(cart.Count==0 || cart.Count>200)throw new InvalidOperationException("Cart must contain 1–200 items.");
        if(cart.GroupBy(x=>x.ProductId).Any(g=>g.Count()>1))
            throw new InvalidOperationException("Combine duplicate items before checkout.");
        if(cart.Any(i=>i.Qty<=0 || i.Qty!=decimal.Truncate(i.Qty) || i.UnitPrice<0))
            throw new InvalidOperationException("Unsupported quantity or price.");
        var taxPercent=TaxRate(ctx.ShopId);
        var subtotal=cart.Sum(i=>i.Total);
        var tax=decimal.Round(subtotal*taxPercent/100,2,MidpointRounding.AwayFromZero);
        var total=subtotal+tax;
        if(tendered<total)throw new InvalidOperationException("Insufficient cash tendered.");
        var key=Guid.NewGuid();
        var receiptId="OFF-WIN-"+key.ToString("N")[..10].ToUpperInvariant();
        var sale=new LocalSale(key,ctx.ShopId,ctx.UserId,epoch,cart.ToList(),tendered,tax,
            DateTimeOffset.UtcNow,receiptId);
        if(paperWidth is not (58 or 80))
            throw new InvalidOperationException("Unsupported paper width.");
        var receipt=ReceiptPrinter.Format(ctx.ShopName,sale,paperWidth==58?32:42);
        // p_payload schema mirrors Android / Supabase's StorePOS hybrid cash RPC.
        var payload=ToJson(new {
            client_key=key,shop_id=ctx.ShopId,cashier_id=ctx.UserId,
            discount_amount=0, tax_amount=tax,
            items=cart.Select(i=>new {product_id=i.ProductId,quantity=i.Qty,unit_price=i.UnitPrice}),
            payments=new[]{new {method="cash",amount=total,tendered}}
        });
        var item=new QueueItem(key,ctx.ShopId,ctx.UserId,epoch,"sale",payload,receipt,"queued",null,sale.LocalTime);
        using var db=Connect();using var tx=db.BeginTransaction();
        foreach(var line in cart) {
            var p=Find(ctx.ShopId,line.ProductId);
            AuditRules.RequireSimple(p);
            if(p.Price!=line.UnitPrice)throw new InvalidOperationException("Local product price changed. Refresh cart.");
            if(p.TrackStock && p.Stock<line.Qty)throw new InvalidOperationException($"Insufficient local stock for {p.Name}.");
            SaveProduct(db,tx,p with {Stock=p.TrackStock?p.Stock-line.Qty:p.Stock});
        }
        Queue(db,tx,item);
        tx.Commit();
        return item;
    }

    public QueueItem QueueStock(ShopContext ctx,Guid productId,decimal delta,string reason,string? notes) {
        if(!AuditRules.CanStock(ctx.Role))throw new InvalidOperationException("Inventory permission required.");
        if(delta==0 || Math.Abs(delta)>100000000)throw new InvalidOperationException("Invalid stock change.");
        if(reason is not ("adjustment" or "opening" or "return" or "damage" or "theft"))
            throw new InvalidOperationException("Unsupported stock reason.");
        var product=Find(ctx.ShopId,productId);AuditRules.RequireSimple(product);
        if(product.Stock+delta<0)throw new InvalidOperationException("Local stock cannot become negative.");
        var epoch=TrustedEpoch(ctx.ShopId)??throw new InvalidOperationException("Cloud catalog has not been verified.");
        var id=Guid.NewGuid();
        var body=ToJson(new {product_id=productId,delta,reason,notes});
        var op=new QueueItem(id,ctx.ShopId,ctx.UserId,epoch,"adjust",body,"","queued",null,DateTimeOffset.UtcNow);
        using var db=Connect();using var tx=db.BeginTransaction();
        SaveProduct(db,tx,product with {Stock=product.Stock+delta});
        Queue(db,tx,op);tx.Commit();
        return op;
    }
    public QueueItem QueuePhysicalCount(ShopContext ctx,Guid productId,decimal counted) {
        if(!AuditRules.CanEdit(ctx.Role))throw new InvalidOperationException("Manager approval required for physical stocktake.");
        var product=Find(ctx.ShopId,productId);AuditRules.RequireSimple(product);
        if(counted<0 || counted>100000000)throw new InvalidOperationException("Invalid physical count.");
        var epoch=TrustedEpoch(ctx.ShopId)??throw new InvalidOperationException("Cloud catalog has not been verified.");
        var id=Guid.NewGuid();
        var data=ToJson(new {product_id=productId,expected_stock=product.Stock,counted_stock=counted});
        var op=new QueueItem(id,ctx.ShopId,ctx.UserId,epoch,"count",data,"","queued",null,DateTimeOffset.UtcNow);
        using var db=Connect();using var tx=db.BeginTransaction();
        SaveProduct(db,tx,product with {Stock=counted});Queue(db,tx,op);tx.Commit();
        return op;
    }
    public QueueItem QueueCreate(ShopContext ctx,string sku,string name,decimal price,decimal opening) {
        if(!AuditRules.CanEdit(ctx.Role))throw new InvalidOperationException("Manager approval required to add products.");
        if(sku.Trim().Length==0 || name.Trim().Length==0 || price<0 || opening<0)
            throw new InvalidOperationException("SKU, name, price, and stock are required.");
        if(GetProducts(ctx.ShopId).Any(p=>p.Sku.Equals(sku.Trim(),StringComparison.OrdinalIgnoreCase)))
            throw new InvalidOperationException("SKU already exists on this tablet.");
        var epoch=TrustedEpoch(ctx.ShopId)??throw new InvalidOperationException("Cloud catalog has not been verified.");
        var product=new Product(Guid.NewGuid(),ctx.ShopId,sku.Trim(),name.Trim(),null,price,0,opening,5);
        var id=Guid.NewGuid();
        var body=ToJson(new {product_id=product.Id,after=product.ToCloud()});
        var op=new QueueItem(id,ctx.ShopId,ctx.UserId,epoch,"create",body,"","queued",null,DateTimeOffset.UtcNow);
        using var db=Connect();using var tx=db.BeginTransaction();
        SaveProduct(db,tx,product);Queue(db,tx,op);tx.Commit();
        return op;
    }
    public QueueItem QueueEdit(ShopContext ctx,Guid productId,string name,decimal price) {
        if(!AuditRules.CanEdit(ctx.Role))throw new InvalidOperationException("Manager approval required.");
        var old=Find(ctx.ShopId,productId);AuditRules.RequireSimple(old);
        if(string.IsNullOrWhiteSpace(name)||price<0)throw new InvalidOperationException("Invalid product edit.");
        var updated=old with {Name=name.Trim(),Price=price};
        var epoch=TrustedEpoch(ctx.ShopId)??throw new InvalidOperationException("No verified catalog.");
        var id=Guid.NewGuid();
        var body=ToJson(new {product_id=productId,before=BeforeAfter(old),after=BeforeAfter(updated)});
        var op=new QueueItem(id,ctx.ShopId,ctx.UserId,epoch,"edit",body,"","queued",null,DateTimeOffset.UtcNow);
        using var db=Connect();using var tx=db.BeginTransaction();
        SaveProduct(db,tx,updated);Queue(db,tx,op);tx.Commit();return op;
    }
    public List<QueueItem> GetOutbox(Guid shopId) {
        using var db=Connect(); using var cmd=db.CreateCommand();
        cmd.CommandText="SELECT id,shop_id,actor_id,epoch,kind,payload,receipt,status,error,created_utc FROM outbox WHERE shop_id=$shop ORDER BY rowid ASC";
        cmd.Parameters.AddWithValue("$shop",shopId.ToString());
        using var reader=cmd.ExecuteReader();
        var items=new List<QueueItem>();
        while(reader.Read())items.Add(new QueueItem(
            Guid.Parse(reader.GetString(0)),Guid.Parse(reader.GetString(1)),
            Guid.Parse(reader.GetString(2)),Guid.Parse(reader.GetString(3)),
            reader.GetString(4),reader.GetString(5),reader.GetString(6),reader.GetString(7),
            reader.IsDBNull(8)?null:reader.GetString(8),DateTimeOffset.Parse(reader.GetString(9))
        ));
        return items;
    }
    public void UpdateQueue(Guid id,string status,string? message) {
        if(status is not ("synced" or "review" or "queued"))throw new InvalidOperationException("Invalid queue status.");
        using var db=Connect();using var cmd=db.CreateCommand();
        cmd.CommandText="UPDATE outbox SET status=$status,error=$error WHERE id=$id AND status<>'synced'";
        cmd.Parameters.AddWithValue("$id",id.ToString());
        cmd.Parameters.AddWithValue("$status",status);
        cmd.Parameters.AddWithValue("$error",message??(object)DBNull.Value);
        cmd.ExecuteNonQuery();
    }
}
