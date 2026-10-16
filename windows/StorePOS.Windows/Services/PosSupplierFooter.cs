namespace StorePOS.Windows.Services;

/// <summary>
/// Azurate's POS software support details, independent of the merchant's
/// name, address, registered seller identity and original sale data.
/// </summary>
public static class PosSupplierFooter
{
    public const string Name = "AZURATE SOFTWARE SOLUTION";
    public const string Address = "PULO SAN ROQUE, PAOMBONG, BULACAN";
    public const string Phone = "09241134301";

    public static IReadOnlyList<string> CenteredLines(int columns)
    {
        if (columns < 32) throw new ArgumentOutOfRangeException(nameof(columns));
        var labels = columns < Address.Length
            ? new[] { "POS SYSTEM SUPPLIED", "& SUPPORTED BY", Name,
                "PULO SAN ROQUE,", "PAOMBONG, BULACAN",
                "TECHNICAL SUPPORT / INQUIRIES", Phone }
            : new[] { "POS SYSTEM SUPPLIED & SUPPORTED BY", Name, Address,
                "TECHNICAL SUPPORT / INQUIRIES", Phone };
        return labels.Select(s =>
        {
            if (s.Length > columns) throw new InvalidOperationException("Supplier detail exceeds paper width.");
            return s.PadLeft((columns + s.Length) / 2);
        }).ToArray();
    }
}
