using System.IO;
using System.Text;
using StorePOS.Windows.Core;
using StorePOS.Windows.Services;
using Xunit;

namespace StorePOS.Windows.Tests;

public class ReceiptOutputTests
{
    static LocalSale Sample() => new(
        Guid.NewGuid(),Guid.NewGuid(),Guid.NewGuid(),Guid.NewGuid(),
        new List<CartLine> { new(Guid.NewGuid(),"SKU100","Milk",39m,2m) },
        100m,0m,DateTimeOffset.UtcNow,"OFF-WIN-TEST");

    [Theory]
    [InlineData(32)]
    [InlineData(42)]
    public void SupplierContactIsReadableOnThermalPaper(int width)
    {
        var text = ReceiptPrinter.Format("Sherine Store",Sample(),width);
        Assert.Contains(PosSupplierFooter.Name,text);
        Assert.Contains(PosSupplierFooter.Phone,text);
        Assert.DoesNotContain("Powered by StorePOS",text);
        Assert.All(PosSupplierFooter.CenteredLines(width),line=>Assert.True(line.Length<=width));
        Assert.Contains(width==32?"PAOMBONG, BULACAN":PosSupplierFooter.Address,text);
    }

    [Theory]
    [InlineData(58)]
    [InlineData(80)]
    public void PdfUsesSavedReceiptAndNarrowPageWidth(int mm)
    {
        var text = ReceiptPrinter.Format("Sherine Store",Sample(),mm==58?32:42);
        var path = Path.Combine(Path.GetTempPath(),"StorePOS-"+Guid.NewGuid().ToString("N")+".pdf");
        try {
            ReceiptPdfExporter.Save(path,text,mm);
            var pdf = Encoding.ASCII.GetString(File.ReadAllBytes(path));
            Assert.StartsWith("%PDF-1.4",pdf);
            Assert.Contains(PosSupplierFooter.Phone,pdf);
            Assert.Contains("%%EOF",pdf);
            Assert.Contains("/MediaBox",pdf);
            Assert.Contains("OFF-WIN-TEST",pdf);
        } finally { if(File.Exists(path)) File.Delete(path); }
    }
}
