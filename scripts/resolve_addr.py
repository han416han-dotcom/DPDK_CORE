#!/usr/bin/env python3
"""
DPDK Core Analyzer - 地址符号解析器
将 GDB 日志中的地址与 ELF 符号表交叉引用。

输入: {"frames": [...], "symbols": [...]}
      frames: GDB 日志解析出的栈帧列表
      symbols: ELF 符号表
输出: 地址解析后的帧列表
"""

import sys
import json


def resolve_addresses(frames: list, symbols: list) -> list:
    """将地址与符号表匹配, 解析出函数名"""
    if not frames or not symbols:
        return frames

    # 构建符号索引: address -> symbol (按地址排序)
    sym_list = []
    for sym in symbols:
        try:
            addr = int(sym.get('address', '0x0'), 16)
            sym_list.append({
                'addr': addr,
                'name': sym.get('name', ''),
                'size': sym.get('size', 0),
            })
        except ValueError:
            continue

    sym_list.sort(key=lambda x: x['addr'])

    resolved_count = 0
    for frame in frames:
        addr_str = frame.get('address')
        if not addr_str:
            continue

        try:
            target_addr = int(addr_str, 16)
        except ValueError:
            continue

        # 二分查找最近的符号
        matched = _find_nearest_symbol(target_addr, sym_list)
        if matched:
            offset = target_addr - matched['addr']
            if not frame.get('function_name') or frame['function_name'] in ('??',):
                frame['function_name'] = matched['name']
                frame['offset_in_func'] = f'0x{offset:x}'
                frame['resolved'] = True
                frame['confidence'] = 95 if offset == 0 else 85
                resolved_count += 1

    return frames


def _find_nearest_symbol(addr: int, sym_list: list) -> dict | None:
    """二分查找包含 addr 的最近符号"""
    if not sym_list:
        return None

    lo, hi = 0, len(sym_list) - 1
    best = None

    while lo <= hi:
        mid = (lo + hi) // 2
        if sym_list[mid]['addr'] <= addr:
            best = sym_list[mid]
            lo = mid + 1
        else:
            hi = mid - 1

    if best is None:
        return None

    # 检查偏移是否在合理范围内 (默认 1MB)
    offset = addr - best['addr']
    max_offset = max(best['size'], 0x100000) if best['size'] > 0 else 0x100000
    if 0 <= offset < max_offset:
        return best

    return None


def main():
    try:
        raw = sys.stdin.read().strip()
        if not raw:
            print(json.dumps({"status": "error", "error": "无输入"}))
            sys.exit(1)

        inp = json.loads(raw)
        frames = inp.get('frames', [])
        symbols = inp.get('symbols', [])

        resolved = resolve_addresses(frames, symbols)

        print(json.dumps({
            'status': 'ok',
            'frames': resolved,
            'stats': {
                'total': len(frames),
                'resolved': sum(1 for f in resolved if f.get('resolved')),
            }
        }, ensure_ascii=False, indent=2))

    except Exception as e:
        print(json.dumps({"status": "error", "error": str(e)}))
        sys.exit(1)


if __name__ == '__main__':
    main()
