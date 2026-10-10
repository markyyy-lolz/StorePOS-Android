using System.Text.Json;
using System.Text.Json.Serialization;

namespace StorePOS.Windows.Core;

public static class StorePOSConfig
{
    // Supabase publishable key is deliberately public; NEVER place a service_role key here.
    public const string Url = "https://qgyzdoltjlryjthxxscw.supabase.co";
    public const string PublishableKey = "sb_publishable_mCjtfE-W75s1yyUdw2NY2g_z6ic5DIc";
    public const string AppVersion = "1.0.0";
    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) {
        PropertyNameCaseInsensitive = true, WriteIndented = false
    };
}

public sealed record Product(
    Guid Id, Guid ShopId, string Sku, string Name, string? Barcode,
    decimal Price, decimal Cost, decimal Stock, decimal Reorder,
    bool IsActive = true, bool TrackStock = true,
    string? CategoryId = null, string? Brand = null,
    string? PartNumber = null, string Unit = "pc",
    string ItemType = "product", bool Complex = false
)
{
    public string Display => $"{Name}  •  {Sku}";
    public string StockLabel => $"{Stock:0.##} {Unit}";
    public object ToCloud() => new {
        id=Id,shop_id=ShopId,sku=Sku,name=Name,barcode=Barcode,
        selling_price=Price,cost_price=Cost,stock_quantity=Stock,
        reorder_level=Reorder,is_active=IsActive,track_stock=TrackStock,
        category_id=CategoryId,brand=Brand,part_number=PartNumber,
        unit=Unit,item_type=ItemType
    };
}
public sealed record CartLine(Guid ProductId, string Sku, string Name, decimal UnitPrice, decimal Qty) {
    public decimal Total => decimal.Round(Qty*UnitPrice,2);
}
public sealed record LocalSale(
    Guid ClientKey, Guid ShopId, Guid ActorId, Guid CatalogEpoch,
    List<CartLine> Items, decimal Tendered, decimal TaxAmount,
    DateTimeOffset LocalTime, string ReceiptNumber
) { public decimal Total => Items.Sum(i=>i.Total)+TaxAmount; }
public sealed record QueueItem(
    Guid Id, Guid ShopId, Guid ActorId, Guid Epoch, string Kind,
    string Payload, string Receipt, string Status, string? Error,
    DateTimeOffset CreatedAt
);
public sealed record ShopContext(Guid ShopId,string ShopName,string Role,Guid UserId);
public sealed record LicenseSnapshot(
    bool Valid, string? Plan, string Status,
    DateTimeOffset CheckedAt, DateTimeOffset? ExpiresAt, int GraceDays,
    DateTimeOffset LastSeenUtc
)
{
    public bool AllowsOffline(DateTimeOffset now) {
        if (!Valid || now < LastSeenUtc.AddMinutes(-5)) return false;
        var graceEnd=CheckedAt.AddDays(Math.Clamp(GraceDays,0,30));
        return now <= graceEnd && (!ExpiresAt.HasValue || now <= ExpiresAt.Value);
    }
}
public sealed record AuthSession(
    string AccessToken,string RefreshToken,Guid UserId,string Email,
    DateTimeOffset ExpiresAt
);
public static class AuditRules
{
    public static bool CanSell(string role) =>
        role is "owner" or "admin" or "manager" or "cashier";
    public static bool CanStock(string role) =>
        role is "owner" or "admin" or "manager" or "inventory";
    public static bool CanEdit(string role) =>
        role is "owner" or "admin" or "manager";
    public static void RequireSimple(Product p) {
        if (!p.IsActive || p.Complex) throw new InvalidOperationException(
            "Inactive, packed, serialized, batch or weighed items require online checkout.");
    }
}
