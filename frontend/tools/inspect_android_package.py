"""Read APK/AAB native ELF and APK ZIP alignment without modifying the package.

Usage: python tools/inspect_android_package.py path/to/app.apk path/to/app.aab
This static inspection does not replace installation on a 16 KB device.
"""
import hashlib
import json
import pathlib
import struct
import sys
import zipfile


def load_segments(data):
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError('Expected a little-endian ELF file')
    if data[4] == 2:
        offset = struct.unpack_from('<Q', data, 32)[0]
        entry_size, count = struct.unpack_from('<HH', data, 54)
        layout = '<IIQQQQQQ'
        align_index, offset_index, address_index = 7, 2, 3
    elif data[4] == 1:
        offset = struct.unpack_from('<I', data, 28)[0]
        entry_size, count = struct.unpack_from('<HH', data, 42)
        layout = '<IIIIIIII'
        align_index, offset_index, address_index = 7, 1, 2
    else:
        raise ValueError('Unknown ELF class')
    result = []
    for index in range(count):
        row = struct.unpack_from(layout, data, offset + index * entry_size)
        if row[0] == 1:  # PT_LOAD
            result.append({'alignment': row[align_index], 'offset': row[offset_index], 'virtual_address': row[address_index]})
    if not result:
        raise ValueError('No ELF load segments')
    return result


def inspect(path):
    raw = path.read_bytes()
    libraries = []
    with zipfile.ZipFile(path) as package:
        for entry in package.infolist():
            if not entry.filename.endswith('.so'):
                continue
            data = package.read(entry)
            segments = load_segments(data)
            is_64 = data[4] == 2
            name_len, extra_len = struct.unpack_from('<HH', raw, entry.header_offset + 26)
            data_offset = entry.header_offset + 30 + name_len + extra_len
            page_compatible = all(s['alignment'] >= 16384 and (s['virtual_address'] - s['offset']) % 16384 == 0 for s in segments)
            libraries.append({
                'path': entry.filename, 'sha256': hashlib.sha256(data).hexdigest(),
                'elf_bits': 64 if is_64 else 32, 'load_segments': segments,
                'elf_16kb': page_compatible,
                'zip_compressed': entry.compress_type != zipfile.ZIP_STORED,
                'zip_data_offset': data_offset,
                'apk_zip_16kb': data_offset % 16384 == 0 if path.suffix.lower() == '.apk' and entry.compress_type == zipfile.ZIP_STORED else None,
            })
    required = [lib for lib in libraries if lib['elf_bits'] == 64]
    passed = all(lib['elf_16kb'] and lib['apk_zip_16kb'] is not False for lib in required)
    return {'package': str(path), 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw), 'native_libraries': libraries,
            'required_64bit_static_16kb': 'PASS' if passed else 'FAIL', 'scope': 'Static ELF and uncompressed APK ZIP inspection only; AAB ZIP offsets are not installation offsets.'}


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Provide APK/AAB paths')
    reports = [inspect(pathlib.Path(arg)) for arg in sys.argv[1:]]
    print(json.dumps(reports, indent=2, ensure_ascii=True))
    raise SystemExit(any(r['required_64bit_static_16kb'] == 'FAIL' for r in reports))
