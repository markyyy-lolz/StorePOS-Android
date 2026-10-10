using System.IO;
using StorePOS.Windows.Core;
using StorePOS.Windows.Services;
using Xunit;

namespace StorePOS.Windows.Tests;

public sealed class OfflineJournalTests
{
    static (LocalStore Db,ShopContext Context,Product Product) Setup() {
        var folder=Path.Combine(Path.GetTempPath(),"StorePOS.Windows.Tests",Guid.NewGuid().ToString("N"));
        var shop=Guid.NewGuid();
        var p=new Product(Guid.NewGuid(),shop,"TEST-MILK","Test Milk","10001111",65,28,20,5);
        var db=new LocalStore(folder);
        db.SaveTrustedCatalog(shop,Guid.NewGuid(),new[]{p},0);
        return (db,new ShopContext(shop,"Test Store","cashier",Guid.NewGuid()),p);
    }

    [Fact]
    public void CashCheckoutAtomicallyCommitsJournalReceiptAndStock() {
        var (db,ctx,p)=Setup();
        var op=db.QueueCashSale(ctx,new[]{new CartLine(p.Id,p.Sku,p.Name,p.Price,2)},200m);
        var reloaded=new LocalStore(db.Root);
        Assert.Equal(18m,reloaded.Find(ctx.ShopId,p.Id).Stock);
        var saved=Assert.Single(reloaded.GetOutbox(ctx.ShopId));
        Assert.Equal("queued",saved.Status);
        Assert.Equal(op.Id,saved.Id);
        Assert.Equal("sale",saved.Kind);
        Assert.Contains(saved.Id.ToString(),saved.Receipt);
        Assert.Contains("OFFLINE / PROVISIONAL RECEIPT",saved.Receipt);
    }

    [Fact]
    public void NegativeStockSaleIsNotRecordedOrCharged() {
        var (db,ctx,p)=Setup();
        Assert.Throws<InvalidOperationException>(()=>db.QueueCashSale(
            ctx,new[]{new CartLine(p.Id,p.Sku,p.Name,p.Price,21)},2000));
        Assert.Empty(db.GetOutbox(ctx.ShopId));
        Assert.Equal(20m,db.Find(ctx.ShopId,p.Id).Stock);
    }

    [Fact]
    public void RestockAdjustmentHasOriginalActorAndRemainsAfterRestart() {
        var (db,ctx,p)=Setup();
        var staff=ctx with {Role="inventory"};
        var op=db.QueueStock(staff,p.Id,5m,"return","Test supplier");
        var restart=new LocalStore(db.Root);
        Assert.Equal(25m,restart.Find(ctx.ShopId,p.Id).Stock);
        var eventRecord=Assert.Single(restart.GetOutbox(ctx.ShopId));
        Assert.Equal(op.Id,eventRecord.Id);
        Assert.Equal(ctx.UserId,eventRecord.ActorId);
        Assert.Contains("product_id",eventRecord.Payload);
        Assert.Contains("delta",eventRecord.Payload);
    }

    [Fact]
    public void SyncedJournalCannotBeRevertedToNeedsReview() {
        var (db,ctx,p)=Setup();
        var op=db.QueueCashSale(ctx,new[]{new CartLine(p.Id,p.Sku,p.Name,p.Price,1)},70m);
        db.UpdateQueue(op.Id,"synced",null);
        db.UpdateQueue(op.Id,"review","Late network error");
        var entry=Assert.Single(db.GetOutbox(ctx.ShopId));
        Assert.Equal("synced",entry.Status);
        Assert.Contains("OFFLINE",entry.Receipt);
    }

    [Fact]
    public void UnsyncedChangePreventsCatalogOverwriteOnReconnect() {
        var (db,ctx,p)=Setup();
        db.QueueCashSale(ctx,new[]{new CartLine(p.Id,p.Sku,p.Name,p.Price,1)},70m);
        Assert.Throws<InvalidOperationException>(()=>db.SaveTrustedCatalog(
            ctx.ShopId,Guid.NewGuid(),new[]{p with {Stock=999}},0));
        Assert.Equal(19m,db.Find(ctx.ShopId,p.Id).Stock);
    }
    [Fact]
    public void OfflineProductCreationReplaysBeforeSaleOfNewProduct()
    {
        var (db,original,p)=Setup();
        var ctx=original with {Role="manager"};
        var created=db.QueueCreate(ctx,"NEW-1","New Product",35m,4m);
        var newProduct=db.GetProducts(ctx.ShopId).Single(x=>x.Sku=="NEW-1");
        var cash=db.QueueCashSale(ctx,new[]{
            new CartLine(newProduct.Id,newProduct.Sku,newProduct.Name,newProduct.Price,1)
        },50m);
        var events=db.GetOutbox(ctx.ShopId);
        Assert.Equal(2,events.Count);
        Assert.Equal(created.Id,events[0].Id);
        Assert.Equal("create",events[0].Kind);
        Assert.Equal(cash.Id,events[1].Id);
        Assert.Equal("sale",events[1].Kind);
        Assert.Equal(3m,db.Find(ctx.ShopId,newProduct.Id).Stock);
    }

}
