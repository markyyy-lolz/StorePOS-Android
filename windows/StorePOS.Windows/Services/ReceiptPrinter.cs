using System.Runtime.InteropServices;
using System.Text;
using StorePOS.Windows.Core;

namespace StorePOS.Windows.Services;

/// <summary>
/// Prints directly through the Windows printer spooler as ESC/POS RAW.
/// Printing can occur after SQLite commits even with no internet connection.
/// A failed print NEVER rolls back or duplicates a successful cash sale.
/// </summary>
public static class ReceiptPrinter
{
    static string Align(string value,int width,bool right=false) {
        value=value.Length>width?value[..width]:value;
        return right?value.PadLeft(width):value.PadRight(width);
    }
    public static string Format(string shopName,LocalSale sale,int width=42) {
        var b=new StringBuilder();
        b.AppendLine(shopName.ToUpperInvariant());
        b.AppendLine("StorePOS Windows | CASH SALE");
        b.AppendLine(new string('-',width));
        b.AppendLine("OFFLINE / PROVISIONAL RECEIPT");
        b.AppendLine("Not a cloud-issued fiscal receipt");
        b.AppendLine(sale.ReceiptNumber);
        b.AppendLine(sale.LocalTime.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss"));
        b.AppendLine(new string('-',width));
        foreach(var item in sale.Items) {
            b.AppendLine(item.Name.Length>width?item.Name[..width]:item.Name);
            b.AppendLine(Align($"{item.Qty:0.##} x PHP {item.UnitPrice:0.00}",width-13)
                +Align($"PHP {item.Total:0.00}",13,true));
        }
        b.AppendLine(new string('-',width));
        var sub=sale.Items.Sum(x=>x.Total);
        b.AppendLine(Align("SUBTOTAL",width-13)+Align($"PHP {sub:0.00}",13,true));
        b.AppendLine(Align("TAX",width-13)+Align($"PHP {sale.TaxAmount:0.00}",13,true));
        b.AppendLine(Align("TOTAL",width-13)+Align($"PHP {sale.Total:0.00}",13,true));
        b.AppendLine(Align("CASH",width-13)+Align($"PHP {sale.Tendered:0.00}",13,true));
        b.AppendLine(Align("CHANGE",width-13)+Align($"PHP {sale.Tendered-sale.Total:0.00}",13,true));
        b.AppendLine(new string('-',width));
        b.AppendLine("LOCAL TRANSACTION ID:");
        b.AppendLine(sale.ClientKey.ToString());
        b.AppendLine("Pending cloud validation and reconciliation.");
        b.AppendLine("Keep this receipt until confirmed.");
        b.AppendLine("Powered by StorePOS / Azurate");
        b.AppendLine();
        b.AppendLine();
        return b.ToString();
    }
    public static void PrintRaw(string windowsPrinterName,string receipt,int paperWidth=80) {
        if(string.IsNullOrWhiteSpace(windowsPrinterName))
            throw new InvalidOperationException("Choose a Windows-installed receipt printer first.");
        if(paperWidth is not (58 or 80))throw new ArgumentException("Paper width must be 58 or 80 mm");
        var content=receipt.Replace("\r\n","\n").Replace("\n","\r\n");
        // Explicit ASCII avoids corrupt multibyte sequences on simple ESC/POS printers.
        var body=Encoding.ASCII.GetBytes(content);
        var bytes=new byte[body.Length+10];
        bytes[0]=0x1B;bytes[1]=0x40; // ESC @ initialization
        Buffer.BlockCopy(body,0,bytes,2,body.Length);
        bytes[^8]=0x0A;bytes[^7]=0x0A;bytes[^6]=0x0A;
        bytes[^5]=0x1D;bytes[^4]=0x56;bytes[^3]=0x00; // full cut
        using var handle=Open(windowsPrinterName);
        var doc=new DOC_INFO_1 {pDocName="StorePOS Windows Receipt",pDatatype="RAW",pOutputFile=null};
        if(!StartDocPrinter(handle,1,ref doc))throw PrinterError("Cannot start print job");
        try {
            if(!StartPagePrinter(handle))throw PrinterError("Cannot start printer page");
            try {
                if(!WritePrinter(handle,bytes,bytes.Length,out var written) || written!=bytes.Length)
                    throw PrinterError("Printer did not accept all bytes");
            }finally {EndPagePrinter(handle);}
        }finally {EndDocPrinter(handle);}
    }
    static SafePrinterHandle Open(string printer) {
        if(!OpenPrinter(printer,out var handle,nint.Zero) || handle==null || handle.IsInvalid)
            throw PrinterError("Cannot open Windows printer");
        return handle;
    }
    static Exception PrinterError(string message)=>
        new InvalidOperationException(message+" (Windows spooler code "+Marshal.GetLastWin32Error()+").");
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)]
    struct DOC_INFO_1 {
        [MarshalAs(UnmanagedType.LPWStr)] public string? pDocName;
        [MarshalAs(UnmanagedType.LPWStr)] public string? pOutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string? pDatatype;
    }
    sealed class SafePrinterHandle:SafeHandle {
        public SafePrinterHandle():base(nint.Zero,true){}
        public override bool IsInvalid=>handle==nint.Zero;
        protected override bool ReleaseHandle()=>ClosePrinter(handle);
    }
    [DllImport("winspool.drv",EntryPoint="OpenPrinterW",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool OpenPrinter(string name,out SafePrinterHandle handle,nint defaults);
    [DllImport("winspool.drv",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool ClosePrinter(nint h);
    [DllImport("winspool.drv",EntryPoint="StartDocPrinterW",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool StartDocPrinter(SafePrinterHandle h,int level,ref DOC_INFO_1 doc);
    [DllImport("winspool.drv",SetLastError=true)]
    static extern bool EndDocPrinter(SafePrinterHandle h);
    [DllImport("winspool.drv",SetLastError=true)]
    static extern bool StartPagePrinter(SafePrinterHandle h);
    [DllImport("winspool.drv",SetLastError=true)]
    static extern bool EndPagePrinter(SafePrinterHandle h);
    [DllImport("winspool.drv",SetLastError=true)]
    static extern bool WritePrinter(SafePrinterHandle h,byte[] data,int size,out int written);
}
