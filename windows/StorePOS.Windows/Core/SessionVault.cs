using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace StorePOS.Windows.Core;

/// <summary>Per-Windows-user DPAPI encryption for tokens + cached authorization.
/// No owner secrets, passwords, or Supabase service keys are written to disk.</summary>
public sealed class SessionVault
{
    readonly string _path;
    static readonly byte[] Entropy=Encoding.UTF8.GetBytes("Azurate.StorePOS.Windows.v1");
    public SessionVault(string root) { _path=Path.Combine(root,"identity.dpapi"); }
    public void Save(AuthSession session,ShopContext shop,LicenseSnapshot license,string deviceId) {
        var json=JsonSerializer.Serialize(new PersistedIdentity(session,shop,license,deviceId),StorePOSConfig.Json);
        var bytes=ProtectedData.Protect(Encoding.UTF8.GetBytes(json),Entropy,DataProtectionScope.CurrentUser);
        File.WriteAllBytes(_path+".tmp",bytes);
        File.Move(_path+".tmp",_path,true);
    }
    public PersistedIdentity? Load() {
        if (!File.Exists(_path)) return null;
        try {
            var bytes=ProtectedData.Unprotect(File.ReadAllBytes(_path),Entropy,DataProtectionScope.CurrentUser);
            return JsonSerializer.Deserialize<PersistedIdentity>(bytes,StorePOSConfig.Json);
        }catch(CryptographicException) {return null;}
    }
    public void Clear() { if(File.Exists(_path)) File.Delete(_path); }
}
public sealed record PersistedIdentity(AuthSession Session,ShopContext Shop,LicenseSnapshot License,string DeviceId);
