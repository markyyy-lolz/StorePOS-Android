using StorePOS.Windows.Core;
using StorePOS.Windows.Services;
using Xunit;

namespace StorePOS.Windows.Tests;

public sealed class OfflineRulesTests
{
    [Fact]
    public void OfflineReceiptIsProvisionalAndContainsImmutableId()
    {
        var id=Guid.NewGuid();
        var sale=new LocalSale(id,Guid.NewGuid(),Guid.NewGuid(),Guid.NewGuid(),
            new List<CartLine>{new(Guid.NewGuid(),"MILK-10","Milk",42.50m,2)},
            100m,0m,DateTimeOffset.UtcNow,"OFF-WIN-TEST");
        var receipt=ReceiptPrinter.Format("Sherine Grocery",sale);
        Assert.Contains("OFFLINE / PROVISIONAL RECEIPT",receipt);
        Assert.Contains(id.ToString(),receipt);
        Assert.Contains("PHP 85.00",receipt);
        Assert.Contains("PHP 15.00",receipt);
    }
    [Fact]
    public void ExpiredLicenseCannotUseOfflineGraceToSell()
    {
        var now=DateTimeOffset.UtcNow;
        var expired=new LicenseSnapshot(true,"monthly","active",
            now.AddDays(-1),now.AddMinutes(-1),7,now.AddHours(-1));
        Assert.False(expired.AllowsOffline(now));
    }
    [Fact]
    public void ClockRollbackBlocksOfflineAccess()
    {
        var now=DateTimeOffset.UtcNow;
        var state=new LicenseSnapshot(true,"monthly","active",
            now.AddHours(-2),now.AddDays(5),7,now);
        Assert.False(state.AllowsOffline(now.AddHours(-1)));
        Assert.True(state.AllowsOffline(now.AddMinutes(1)));
    }
    [Fact]
    public void CashierCannotChangeInventoryOrPrices()
    {
        Assert.True(AuditRules.CanSell("cashier"));
        Assert.False(AuditRules.CanStock("cashier"));
        Assert.False(AuditRules.CanEdit("inventory"));
        Assert.True(AuditRules.CanStock("inventory"));
    }
    [Fact]
    public void ComplexProductsAreNotEligibleForOfflineCash()
    {
        var complex=new Product(Guid.NewGuid(),Guid.NewGuid(),"LOT-01","Serial Item",
            null,120,50,3,2,Complex:true);
        Assert.Throws<InvalidOperationException>(()=>AuditRules.RequireSimple(complex));
    }
}
