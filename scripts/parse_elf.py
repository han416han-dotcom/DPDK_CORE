#!/usr/bin/env python3
"""
DPDK Core Analyzer - ELF 可执行文件符号解析器
从 ELF 文件中提取符号表、段信息。

输入: {"elf_path": "/path/to/app"}
输出: 结构化 JSON (stdout)
"""

import sys
import json
import os

# 尝试导入 pyelftools, 如果不可用则提供降级方案
try:
    from elftools.elf.elffile import ELFFile
    from elftools.elf.constants import SH_TYPE, P_TYPE
    HAS_ELFTOOLS = True
except ImportError:
    HAS_ELFTOOLS = False


def parse_elf(elf_path: str, dynsym_only: bool = False) -> dict:
    """解析 ELF 文件, 提取符号和段信息
    Args:
        elf_path: ELF 文件路径
        dynsym_only: 仅读取 .dynsym (用于 strip 后的可执行文件)
    """
    if not os.path.exists(elf_path):
        return {'status': 'error', 'error': f'文件不存在: {elf_path}'}

    if not HAS_ELFTOOLS:
        return _parse_elf_fallback(elf_path)

    try:
        with open(elf_path, 'rb') as f:
            elf = ELFFile(f)

            result = {
                'status': 'ok',
                'elf_class': 'ELF64' if elf.elf_class == 2 else 'ELF32',
                'endian': 'little' if elf.little_endian else 'big',
                'symbols': [],
                'segments': [],
                'sections': [],
                'stats': {'symbol_count': 0, 'segment_count': 0},
                'dynsym_only': dynsym_only,
            }

            # 1. 加载符号表
            seen_symbols = set()
            sections_to_check = ['.dynsym'] if dynsym_only else ['.symtab', '.dynsym']
            for symsec_name in sections_to_check:
                symsec = elf.get_section_by_name(symsec_name)
                if symsec is None:
                    continue
                for sym in symsec.iter_symbols():
                    sym_addr = sym['st_value']
                    sym_name = sym.name
                    if not sym_name or sym_addr == 0:
                        continue
                    # 去重
                    dedup_key = (sym_name, sym_addr)
                    if dedup_key in seen_symbols:
                        continue
                    seen_symbols.add(dedup_key)

                    result['symbols'].append({
                        'name': sym_name,
                        'address': f'0x{sym_addr:016x}',
                        'size': sym['st_size'],
                        'type': _symbol_type_str(sym['st_info']['type']),
                        'bind': _symbol_bind_str(sym['st_info']['bind']),
                    })

            # 2. 段信息
            for seg in elf.iter_segments():
                seg_type = seg['p_type']
                if seg_type in ('PT_LOAD', 'PT_NOTE', 'PT_GNU_EH_FRAME',
                                'PT_GNU_STACK', 'PT_GNU_RELRO'):
                    result['segments'].append({
                        'type': seg_type,
                        'vaddr': f'0x{seg["p_vaddr"]:016x}',
                        'memsz': seg['p_memsz'],
                        'filesz': seg['p_filesz'],
                        'flags': _segment_flags_str(seg['p_flags']),
                        'offset': seg['p_offset'],
                    })

            # 3. Section 信息
            for sec in elf.iter_sections():
                sec_name = sec.name
                if sec_name and not sec_name.startswith('.plt') and \
                   not sec_name.startswith('.rela'):
                    result['sections'].append({
                        'name': sec_name,
                        'addr': f'0x{sec["sh_addr"]:016x}' if sec['sh_addr'] else None,
                        'size': sec['sh_size'],
                    })

            result['stats'] = {
                'symbol_count': len(result['symbols']),
                'segment_count': len(result['segments']),
            }

            return result

    except Exception as e:
        return {'status': 'error', 'error': f'ELF 解析异常: {str(e)}'}


def _symbol_type_str(t: int) -> str:
    """符号类型映射"""
    types = {
        0: 'NOTYPE', 1: 'OBJECT', 2: 'FUNC', 3: 'SECTION',
        4: 'FILE', 5: 'COMMON', 6: 'TLS',
        10: 'GNU_IFUNC',
    }
    return types.get(t, f'UNKNOWN({t})')


def _symbol_bind_str(b: int) -> str:
    """符号绑定映射"""
    binds = {
        0: 'LOCAL', 1: 'GLOBAL', 2: 'WEAK',
        10: 'GNU_UNIQUE',
    }
    return binds.get(b, f'UNKNOWN({b})')


def _segment_flags_str(flags: int) -> str:
    """段标志字符串"""
    s = ''
    if flags & 1: s += 'X'  # PF_X
    if flags & 2: s += 'W'  # PF_W
    if flags & 4: s += 'R'  # PF_R
    return s or '---'


def _parse_elf_fallback(elf_path: str) -> dict:
    """降级方案: 当 pyelftools 不可用时, 仅读取 ELF 头部基本信息"""
    try:
        with open(elf_path, 'rb') as f:
            magic = f.read(4)
            if magic != b'\x7fELF':
                return {'status': 'error', 'error': '不是有效的 ELF 文件'}

            # 读取基本头部信息
            f.seek(0)
            header = f.read(64)
            ei_class = header[4]    # 1=32bit, 2=64bit
            ei_data = header[5]     # 1=little, 2=big
            e_type = int.from_bytes(header[16:18], 'little')
            e_machine = int.from_bytes(header[18:20], 'little')

            machines = {
                0x3E: 'x86_64', 0x28: 'ARM', 0xB7: 'AArch64',
                0x32: 'IA-64', 0x14: 'PowerPC', 0x15: 'PowerPC64',
            }

            result = {
                'status': 'ok',
                'elf_class': 'ELF64' if ei_class == 2 else 'ELF32',
                'endian': 'little' if ei_data == 1 else 'big',
                'machine': machines.get(e_machine, f'0x{e_machine:04x}'),
                'symbols': [],
                'segments': [],
                'sections': [],
                'fallback': True,
                'stats': {'symbol_count': 0, 'segment_count': 0},
                'warning': 'pyelftools 未安装, 使用降级解析模式。符号表为空。',
            }
            return result

    except Exception as e:
        return {'status': 'error', 'error': f'ELF 降级解析失败: {str(e)}'}


def main():
    try:
        dynsym_only = False
        if len(sys.argv) > 1:
            elf_path = sys.argv[1]
        else:
            raw = sys.stdin.read().strip()
            if not raw:
                print(json.dumps({"status": "error", "error": "无输入"}))
                sys.exit(1)
            try:
                inp = json.loads(raw)
                elf_path = inp.get('elf_path')
                dynsym_only = inp.get('dynsym_only', False)
            except json.JSONDecodeError:
                elf_path = raw.strip()

        if not elf_path:
            print(json.dumps({"status": "error", "error": "未指定 elf_path"}))
            sys.exit(1)

        result = parse_elf(elf_path, dynsym_only=dynsym_only)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    except Exception as e:
        print(json.dumps({"status": "error", "error": str(e)}))
        sys.exit(1)


if __name__ == '__main__':
    main()
