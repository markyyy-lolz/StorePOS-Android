using System.Globalization;
using System.IO;
using System.Text;

namespace StorePOS.Windows.Services;

/// <summary>
/// Creates a narrow, multipage PDF from the SAME persisted receipt text used
/// for Windows RAW ESC/POS printing. No external PDF engine or Python runtime.
/// </summary>
public static class ReceiptPdfExporter
{
    public static void Save(string destination, string savedReceipt, int paperWidthMm)
    {
        if (paperWidthMm is not (58 or 80)) throw new ArgumentOutOfRangeException(nameof(paperWidthMm));
        if (string.IsNullOrWhiteSpace(savedReceipt)) throw new ArgumentException("Receipt is empty.");
        var columns = paperWidthMm == 58 ? 32 : 42;
        var lines = savedReceipt.Replace("\r\n", "\n").Replace('\r', '\n')
            .Split('\n')
            .SelectMany(s => s.Length == 0
                ? new[] { "" }
                : Enumerable.Range(0, (s.Length + columns - 1) / columns)
                    .Select(i => s.Substring(i * columns, Math.Min(columns, s.Length - i * columns))))
            .ToArray();
        var pageWidth = paperWidthMm * 72.0 / 25.4;
        const double margin = 8.0;
        var font = Math.Min(8.5, (pageWidth - margin * 2) / (columns * 0.6));
        var leading = font * 1.4;
        const int linesPerPage = 190;
        var pages = lines.Chunk(linesPerPage).ToArray();
        var pageIds = Enumerable.Range(0, pages.Length).Select(i => 4 + i * 2).ToArray();
        var objects = new List<string>
        {
            "<< /Type /Catalog /Pages 2 0 R >>",
            $"<< /Type /Pages /Kids [{string.Join(" ",pageIds.Select(i=>$"{i} 0 R"))}] /Count {pageIds.Length} >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>"
        };
        foreach(var page in pages)
        {
            var height = Math.Max(45, page.Length * leading + margin * 2 + font);
            objects.Add($"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {N(pageWidth)} {N(height)}] /Resources << /Font << /F1 3 0 R >> >> /Contents {objects.Count + 2} 0 R >>");
            var commands = new StringBuilder($"BT /F1 {N(font)} Tf 1 0 0 1 {N(margin)} {N(height - margin - font)} Tm\n");
            foreach(var line in page)
            {
                var ascii = new string(line.Select(ch => ch >= 32 && ch <= 126 ? ch : '?').ToArray());
                var escaped = ascii.Replace("\\","\\\\").Replace("(","\\(").Replace(")","\\)");
                commands.Append('(').Append(escaped).Append($") Tj\n0 -{N(leading)} Td\n");
            }
            commands.Append("ET\n");
            var stream = commands.ToString();
            objects.Add($"<< /Length {Encoding.ASCII.GetByteCount(stream)} >>\nstream\n{stream}endstream");
        }
        using var output = new MemoryStream();
        void Write(string s) { var data = Encoding.ASCII.GetBytes(s); output.Write(data); }
        Write("%PDF-1.4\n");
        var offsets = new List<long> { 0 };
        for(int i=0;i<objects.Count;i++)
        {
            offsets.Add(output.Position);
            Write($"{i+1} 0 obj\n{objects[i]}\nendobj\n");
        }
        var xrefOffset = output.Position;
        Write($"xref\n0 {objects.Count+1}\n0000000000 65535 f \n");
        foreach(var offset in offsets.Skip(1)) Write($"{offset:D10} 00000 n \n");
        Write($"trailer\n<< /Size {objects.Count+1} /Root 1 0 R >>\nstartxref\n{xrefOffset}\n%%EOF\n");
        var path = Path.GetFullPath(destination);
        File.WriteAllBytes(path, output.ToArray());
    }
    static string N(double d) => d.ToString("0.###",CultureInfo.InvariantCulture);
}
