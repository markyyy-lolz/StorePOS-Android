using System.IO;
using Microsoft.Data.Sqlite;
using StorePOS.Windows.Core;
using StorePOS.Windows.Services;
using Xunit;

namespace StorePOS.Windows.Tests;

public sealed class BarcodeAndBackupTests
{
    [Fact]
    public void ScannerQuickAddPersistsBarcodeCostUnitAndOutbox()
    {
        var shop=Guid.NewGuid();
        var directory=Path.Combine(Path.GetTempPath(),"StorePOS.WinTests",Guid.NewGuid().ToString("N"));
        var store=new LocalStore(directory);
        store.SaveTrustedCatalog(shop,Guid.NewGuid(),Array.Empty<Product>(),0);
        var manager=new ShopContext(shop,"Sherine Store","manager",Guid.NewGuid());
        var created=store.QueueCreate(manager,"8851012000014","Canned Milk",49.50m,10m,
            barcode:"8851012000014",cost:32m,unit:"can");
        var item=Assert.Single(new LocalStore(directory).GetProducts(shop));
        Assert.Equal("8851012000014",item.Barcode);
        Assert.Equal(32m,item.Cost);
        Assert.Equal("can",item.Unit);
        Assert.Equal(created.Id,Assert.Single(store.GetOutbox(shop)).Id);
        Assert.Throws<InvalidOperationException>(()=>
            store.QueueCreate(manager,"OTHER-SKU","Duplicate Barcode",40m,4m,
                barcode:"8851012000014"));
        Assert.Single(store.GetOutbox(shop));
    }

    [Fact]
    public void ConsistentBackupPreservesUnsyncedDataWithoutReset()
    {
        var shop=Guid.NewGuid();
        var directory=Path.Combine(Path.GetTempPath(),"StorePOS.WinTests",Guid.NewGuid().ToString("N"));
        var store=new LocalStore(directory);
        var product=new Product(Guid.NewGuid(),shop,"MILK","Milk","8851012000045",48m,31m,12m,2m);
        store.SaveTrustedCatalog(shop,Guid.NewGuid(),new[]{product},0);
        var manager=new ShopContext(shop,"Sherine Store","manager",Guid.NewGuid());
        store.QueueCashSale(manager,new[]{new CartLine(product.Id,product.Sku,product.Name,48m,1)},50m);
        var file=Path.Combine(Path.GetTempPath(),"StorePOS-backup-"+Guid.NewGuid().ToString("N")+".sqlite3");
        try {
            store.BackupDatabase(file);
            using var db=new SqliteConnection(new SqliteConnectionStringBuilder{
                DataSource=file,Mode=SqliteOpenMode.ReadOnly
            }.ToString());
            db.Open();
            using var check=db.CreateCommand();
            check.CommandText="PRAGMA integrity_check;";
            Assert.Equal("ok",check.ExecuteScalar()?.ToString());
            using var count=db.CreateCommand();
            count.CommandText="SELECT COUNT(*) FROM outbox WHERE status='queued'";
            Assert.Equal(1L,Convert.ToInt64(count.ExecuteScalar()));
            Assert.Equal(11m,store.Find(shop,product.Id).Stock);
            Assert.Single(store.GetOutbox(shop));
        } finally { if(File.Exists(file))File.Delete(file); }
    }
}
