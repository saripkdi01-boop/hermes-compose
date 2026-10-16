#!/usr/bin/env python3
"""Tambah JsonStore.init(this) ke HermesApp. Tangani onCreate ada/tidak ada."""
import re, sys

p = "app/src/main/java/id/sukiapps/hermes/di/HermesApp.kt"
c = open(p).read()

if "JsonStore.init" in c:
    print("Sudah ada — skip.")
    sys.exit(0)

# Import
if "import id.sukiapps.hermes.data.store.JsonStore" not in c:
    c = c.replace(
        "import android.app.Application\n",
        "import android.app.Application\nimport id.sukiapps.hermes.data.store.JsonStore\n",
        1
    )

# Kasus 1: ada super.onCreate()
if "super.onCreate()" in c:
    c = c.replace(
        "super.onCreate()\n",
        "super.onCreate()\n        JsonStore.init(this)\n",
        1
    )
    print("Ditambah setelah super.onCreate().")
# Kasus 2: ada onCreate tapi tanpa super (tidak mungkin, tapi tangani)
elif "override fun onCreate()" in c:
    c = c.replace(
        "override fun onCreate() {",
        "override fun onCreate() {\n        super.onCreate()\n        JsonStore.init(this)",
        1
    )
    print("Ditambah di onCreate() yang ada.")
# Kasus 3: tidak ada onCreate sama sekali — buat baru
else:
    # Cari akhir class: pola "}\n" terakhir sebelum akhir file
    # Sisipkan onCreate sebelum kurung kurawal tutup terakhir
    idx = c.rstrip().rfind("}")
    assert idx > 0, "akhir class tidak ketemu"
    new_method = """
    override fun onCreate() {
        super.onCreate()
        JsonStore.init(this)
    }
"""
    c = c[:idx] + new_method + c[idx:]
    print("onCreate() baru dibuat.")

open(p, "w").write(c)
print("OK.")
