"""Runner katalog tools Hermes (D1, 14.30): eksekusi skrip .py katalog via Chaquopy.

Kontrak: run_code(code) -> JSON string {"ok": bool, "output": str, "error": str}.
- stdout+stderr ditangkap via StringIO (stdout dulu, lalu stderr bila ada).
- Tanpa argumen dinamis (kontrak v1 spek D1); tanpa akses KeyVault: modul ini
  murni Python stdlib dan tidak mengimpor apa pun dari aplikasi.
- Timeout ditegakkan di sisi Kotlin (30 detik). Eksekusi berjalan in-process
  (batas yang dinyatakan di PR: timeout menghentikan penantian, bukan thread
  Python; eksekusi proses-terpisah ditunda karena Chaquopy tidak menyediakannya).
"""

import io
import json
import sys


def run_code(code):
    out_buf = io.StringIO()
    err_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out_buf, err_buf
    try:
        exec(compile(code, "<tool>", "exec"), {"__name__": "__main__"})
        return json.dumps(
            {"ok": True, "output": out_buf.getvalue(), "error": err_buf.getvalue()}
        )
    except BaseException as e:  # noqa: BLE001 - semua error tool jadi pesan, bukan crash
        return json.dumps(
            {
                "ok": False,
                "output": out_buf.getvalue(),
                "error": "%s: %s\n%s" % (type(e).__name__, e, err_buf.getvalue()),
            }
        )
    finally:
        sys.stdout, sys.stderr = old_out, old_err
