#!/usr/bin/env python3
"""Tambah JsonStore.init(this) ke HermesApp.onCreate(). Idempoten."""
import re, sys

p = "app/src/main/java/id/sukiapps/hermes/di/HermesApp.kt"
c = open(p).read()

if "JsonStore.init" in c:
    print("Sudah ada JsonStore.init — skip.")
    sys.exit(0)

# Tambah import
old_imp = "import android.app.Application\n"
assert old_imp in c, "import Application tidak ketemu"
c = c.replace(old_imp, old_imp + "import id.sukiapps.hermes.data.store.JsonStore\n", 1)

# Tambah init setelah super.onCreate()
old_super = "        super.onCreate()\n"
assert old_super in c, "super.onCreate() tidak ketemu"
c = c.replace(old_super, old_super + "        JsonStore.init(this)\n", 1)

open(p, "w").write(c)
print("HermesApp.kt dipatch: JsonStore.init(this) ditambahkan.")
