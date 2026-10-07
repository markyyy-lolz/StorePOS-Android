from pathlib import Path

repo = Path(__file__).resolve().parents[1]
path = repo / "app/src/main/java/com/storepos/app/data/StoreRepository.kt"
text = path.read_text(encoding="utf-8")

replacements = {
    '.insert(input.copy(stockQuantity = 0.0))\n            .decodeSingle<Product>()':
        '.insert(input.copy(stockQuantity = 0.0)) { select() }\n            .decodeSingle<Product>()',
    'client.from("customers").insert(input).decodeSingle()':
        'client.from("customers").insert(input) { select() }.decodeSingle()',
    'client.from("motorcycles").insert(input).decodeSingle()':
        'client.from("motorcycles").insert(input) { select() }.decodeSingle()',
    'client.from("job_orders").insert(input).decodeSingle()':
        'client.from("job_orders").insert(input) { select() }.decodeSingle()',
    'client.from("suppliers").insert(input).decodeSingle()':
        'client.from("suppliers").insert(input) { select() }.decodeSingle()',
    'client.from("expenses").insert(input).decodeSingle()':
        'client.from("expenses").insert(input) { select() }.decodeSingle()',
    'client.from("appointments").insert(input).decodeSingle()':
        'client.from("appointments").insert(input) { select() }.decodeSingle()',
    'client.from("service_reminders").insert(input).decodeSingle()':
        'client.from("service_reminders").insert(input) { select() }.decodeSingle()',
    'client.from("warranty_claims").insert(input).decodeSingle()':
        'client.from("warranty_claims").insert(input) { select() }.decodeSingle()',
    '        ).decodeSingle()\n    }\n\n    suspend fun deleteHeldSale':
        '        ) { select() }.decodeSingle()\n    }\n\n    suspend fun deleteHeldSale',
}

changed = []
missing = []
for old, new in replacements.items():
    if new in text:
        continue
    if old in text:
        text = text.replace(old, new, 1)
        changed.append(old.splitlines()[0][:80])
    else:
        missing.append(old.splitlines()[0][:80])

if missing:
    raise SystemExit("Patch safety check failed; expected patterns missing:\n- " + "\n- ".join(missing))

path.write_text(text, encoding="utf-8")

gradle = repo / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
if 'versionCode = 14' in g:
    g = g.replace('versionCode = 14', 'versionCode = 15', 1)
if 'versionName = "1.4.0"' in g:
    g = g.replace('versionName = "1.4.0"', 'versionName = "1.4.1"', 1)
if 'versionCode = 15' not in g or 'versionName = "1.4.1"' not in g:
    raise SystemExit("Version bump safety check failed")
gradle.write_text(g, encoding="utf-8")

(repo / "RELEASE_NOTES.md").write_text("""# StorePOS v1.4.1 — Cloud Insert Response Hotfix

- Fixed a false **incomplete cloud response** error after successfully creating products.
- Product creation now explicitly requests the inserted row from Supabase before decoding it.
- Applied the same response-mode fix to customer, motorcycle, job order, supplier, expense, appointment, service reminder, warranty claim, and held-sale inserts.
- Prevents records from being saved successfully in the cloud while the Android UI incorrectly reports a failure.
- Keeps opening-stock initialization working after a new product is created.

This is a patch release for StorePOS Android v1.4.x.
""", encoding="utf-8")

changelog = repo / "CHANGELOG.md"
old = changelog.read_text(encoding="utf-8")
if "## v1.4.1" not in old:
    if old.startswith("# Changelog\n"):
        old = old[len("# Changelog\n"):].lstrip("\n")
    entry = """# Changelog

## v1.4.1
- Fixed Supabase insert operations decoding empty `return=minimal` responses.
- Added explicit `select()` return representation for inserts that immediately decode the created row.
- Fixes the Inventory message: `StorePOS received an incomplete cloud response. Please retry once.` after a successful product insert.

"""
    changelog.write_text(entry + old, encoding="utf-8")

print(f"Applied StorePOS v1.4.1 hotfix; {len(changed)} insert patterns changed.")
