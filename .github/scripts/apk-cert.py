#!/usr/bin/env python3
"""Print the SHA-256 (lowercase hex) of the first signer certificate in an APK's v3 or v2 signature block.

Used by the Build APK workflow to make sure every published APK is signed with the DEBUG_KEYSTORE_B64 key.
Standard library only; reads the APK Signing Block directly (https://source.android.com/docs/security/features/apksigning/v2).
"""
import hashlib
import struct
import sys


def signer_certs(path):
    data = open(path, 'rb').read()
    eocd = data.rfind(b'PK\x05\x06')
    if eocd < 0:
        raise SystemExit('not a zip/APK: ' + path)
    cd_off = struct.unpack('<I', data[eocd + 16:eocd + 20])[0]
    if data[cd_off - 16:cd_off] != b'APK Sig Block 42':
        raise SystemExit('no APK Signing Block (APK is unsigned or v1-only): ' + path)
    size = struct.unpack('<Q', data[cd_off - 24:cd_off - 16])[0]
    pos, end, found = cd_off - size - 8 + 8, cd_off - 24, {}
    while pos < end:
        length, = struct.unpack('<Q', data[pos:pos + 8])
        block_id, = struct.unpack('<I', data[pos + 8:pos + 12])
        value = data[pos + 12:pos + 8 + length]
        pos += 8 + length
        if block_id in (0x7109871a, 0xf05368c0):  # v2, v3
            signer_len, = struct.unpack('<I', value[4:8])
            signer = value[8:8 + signer_len]
            sd_len, = struct.unpack('<I', signer[:4])
            signed = signer[4:4 + sd_len]
            digests_len, = struct.unpack('<I', signed[:4])
            r = 4 + digests_len
            certs_len, = struct.unpack('<I', signed[r:r + 4])
            certs, k, out = signed[r + 4:r + 4 + certs_len], 0, []
            while k < len(certs):
                n, = struct.unpack('<I', certs[k:k + 4])
                out.append(hashlib.sha256(certs[k + 4:k + 4 + n]).hexdigest())
                k += 4 + n
            found['v3' if block_id == 0xf05368c0 else 'v2'] = out
    if not found:
        raise SystemExit('no v2/v3 signature found: ' + path)
    return (found.get('v3') or found['v2'])[0]


if __name__ == '__main__':
    print(signer_certs(sys.argv[1]))
