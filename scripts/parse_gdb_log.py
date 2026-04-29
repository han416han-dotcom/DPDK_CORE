#!/usr/bin/env python3
"""
DPDK Core Analyzer - GDB log parser.

Input:
  JSON on stdin: {"log_path": "/path/to/gdb.log"}
  or a direct path argument.

Output:
  Structured JSON on stdout.

Supported log styles:
1. Standard project format with explicit markers:
     SECTION:THREAD_INFO
     END_SECTION:THREAD_INFO
2. Simpler heading-based format:
     ====================================
     === INFO THREADS ===
     ====================================
"""

import json
import os
import re
import sys


RE_THREAD_HEADER = re.compile(r'^Thread\s+(\d+)\s+\((.+)\):\s*$')
RE_THREAD_HEADER_SIMPLE = re.compile(r'^Thread\s+(\d+):\s*$')

RE_FRAME_FULL = re.compile(
    r'^#(\d+)\s+(0x[0-9a-fA-F]+)\s+in\s+(\S+)\s+\((.*?)\)\s+at\s+(.+?):(\d+)$'
)
RE_FRAME_NO_LINE = re.compile(
    r'^#(\d+)\s+(0x[0-9a-fA-F]+)\s+in\s+(\S+)\s+\((.*?)\)\s*$'
)
RE_FRAME_UNKNOWN = re.compile(
    r'^#(\d+)\s+(0x[0-9a-fA-F]+)\s+in\s+\?\?\s*\(\)\s*$'
)
RE_FRAME_FROM = re.compile(
    r'^#(\d+)\s+(0x[0-9a-fA-F]+)\s+in\s+(\S+)\s+\((.*?)\)\s+from\s+(.+)$'
)
RE_FRAME_NO_ADDR = re.compile(
    r'^#(\d+)\s+in\s+(\S+)\s+\((.*?)\)\s+at\s+(.+?):(\d+)$'
)

FRAME_PATTERNS = [
    ('full', RE_FRAME_FULL),
    ('from', RE_FRAME_FROM),
    ('no_line', RE_FRAME_NO_LINE),
    ('unknown', RE_FRAME_UNKNOWN),
    ('no_addr', RE_FRAME_NO_ADDR),
]

RE_LCORE_NAME = re.compile(r'[lL]core[-\s]?(\d+)')
RE_LCORE_WORKER = re.compile(r'[wW]orker[-\s]?(\d+)')

SIGNAL_MAP = {
    1: 'SIGHUP', 2: 'SIGINT', 3: 'SIGQUIT', 4: 'SIGILL',
    5: 'SIGTRAP', 6: 'SIGABRT', 7: 'SIGBUS', 8: 'SIGFPE',
    9: 'SIGKILL', 10: 'SIGUSR1', 11: 'SIGSEGV', 12: 'SIGUSR2',
    13: 'SIGPIPE', 14: 'SIGALRM', 15: 'SIGTERM', 16: 'SIGSTKFLT',
    17: 'SIGCHLD', 18: 'SIGCONT', 19: 'SIGSTOP', 20: 'SIGTSTP',
    21: 'SIGTTIN', 22: 'SIGTTOU', 23: 'SIGURG', 24: 'SIGXCPU',
    25: 'SIGXFSZ', 26: 'SIGVTALRM', 27: 'SIGPROF', 28: 'SIGWINCH',
    29: 'SIGIO', 30: 'SIGPWR', 31: 'SIGSYS',
}

LEGACY_SECTION_TITLES = {
    'INFO THREADS': 'THREAD_INFO',
    'THREAD INFO': 'THREAD_INFO',
    'ALL THREAD BT': 'ALL_THREAD_BT',
    'THREAD APPLY ALL BT FULL': 'ALL_THREAD_BT',
    'THREAD APPLY ALL BACKTRACE FULL': 'ALL_THREAD_BT',
    'BACKTRACE': 'ALL_THREAD_BT',
    'REGISTERS': 'REGISTERS',
    'INFO REGISTERS': 'REGISTERS',
    'CRASH INFO': 'CRASH_INFO',
    'SIGINFO': 'CRASH_INFO',
    'MEMORY MAPPING': 'MEMORY_MAPPING',
    'INFO PROC MAPPINGS': 'MEMORY_MAPPING',
}

CRASH_FUNC_HINTS = {
    '__GI_raise', '__raise', 'raise', 'abort', '__GI_abort',
    'rte_panic', '__rte_panic', 'gsignal', 'pthread_kill', 'tgkill'
}

DPDK_PREFIXES = [
    'rte_', 'dpdk_', 'eal_', 'rte_panic', '__rte_panic',
    'rte_ring', 'rte_mempool', 'rte_mbuf', 'rte_malloc',
    'rte_free', 'rte_realloc', 'rte_pktmbuf', 'rte_eth',
    'rte_lcore', 'rte_eal', 'rte_timer', 'rte_alarm',
    'rte_interrupt', 'rte_ip', 'rte_udp', 'rte_tcp',
    'rte_sched', 'rte_hash', 'rte_lpm', 'rte_acl',
]


class GdbLogParser:
    def __init__(self):
        self.log_path = None
        self.raw_lines = []
        self.sections = {}
        self.section_order = []
        self.threads = []
        self.crash_info = {}
        self.registers = {}
        self.thread_map = {}
        self.errors = []

    def parse(self, log_path: str) -> dict:
        self.log_path = log_path
        if not os.path.exists(log_path):
            return self._error_result(f'File not found: {log_path}')

        with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
            self.raw_lines = f.readlines()

        try:
            self._split_sections()
            self._parse_thread_info()
            self._parse_all_thread_bt()
            self._parse_registers()
            self._parse_crash_info()
            self._parse_memory_mapping()
            self._identify_crash_thread()
        except Exception as e:
            self.errors.append({"msg": f"Parse exception: {str(e)}", "level": "ERROR"})

        return self._build_result()

    def _split_sections(self):
        self.sections = {}
        self.section_order = []

        if not self._split_standard_sections():
            self._split_legacy_sections()

    def _split_standard_sections(self) -> bool:
        current_section = 'HEADER'
        current_lines = []
        found_marker = False

        for raw_line in self.raw_lines:
            line = raw_line.rstrip('\n')

            if re.search(r'END_SECTION:(\w+)', line):
                found_marker = True
                self._save_section(current_section, current_lines)
                current_section = 'HEADER'
                current_lines = []
                continue

            start_m = re.search(r'(?<!END_)SECTION:(\w+)', line)
            if start_m:
                found_marker = True
                self._save_section(current_section, current_lines)
                current_section = start_m.group(1)
                current_lines = []
                continue

            if line.strip():
                current_lines.append(line)

        self._save_section(current_section, current_lines)
        return found_marker

    def _split_legacy_sections(self):
        current_section = 'HEADER'
        current_lines = []

        for raw_line in self.raw_lines:
            line = raw_line.rstrip('\n')
            legacy_section = self._match_legacy_section(line)
            if legacy_section:
                self._save_section(current_section, current_lines)
                current_section = legacy_section
                current_lines = []
                continue

            if self._is_legacy_separator(line):
                continue

            if line.strip():
                current_lines.append(line)

        self._save_section(current_section, current_lines)

    def _save_section(self, section_name: str, lines: list):
        if not section_name or not lines:
            return

        if section_name in self.sections:
            self.sections[section_name].extend(lines)
            return

        self.sections[section_name] = list(lines)
        if section_name not in self.section_order:
            self.section_order.append(section_name)

    def _match_legacy_section(self, line: str):
        title_m = re.match(r'^\s*=+\s*(.*?)\s*=+\s*$', line)
        if not title_m:
            return None

        normalized = self._normalize_legacy_title(title_m.group(1))
        if not normalized:
            return None

        return LEGACY_SECTION_TITLES.get(normalized)

    def _normalize_legacy_title(self, title: str) -> str:
        normalized = re.sub(r'[^A-Za-z0-9]+', ' ', title.upper()).strip()
        return re.sub(r'\s+', ' ', normalized)

    def _is_legacy_separator(self, line: str) -> bool:
        return re.match(r'^\s*[=\-]{6,}\s*$', line) is not None

    def _parse_thread_info(self):
        lines = self.sections.get('THREAD_INFO', [])
        for line in lines:
            match = re.match(
                r'^[\*\s]+\s*(\d+)\s+(Thread\s+)?(0x[0-9a-fA-F]+)\s+\(.*?\)\s+(.*)$',
                line
            )
            if not match:
                continue

            thread_id = match.group(1)
            os_thread_id = match.group(3)
            self.thread_map[thread_id] = {
                'thread_id': thread_id,
                'os_thread_id': os_thread_id,
                'crash_thread': line.lstrip().startswith('*'),
            }

    def _parse_all_thread_bt(self):
        lines = self.sections.get('ALL_THREAD_BT', [])
        if not lines:
            raw_fallback = [line.rstrip('\n') for line in self.raw_lines if line.strip()]
            if any(self._match_thread_header(line.strip()) for line in raw_fallback) or \
                    any(line.lstrip().startswith('#0') for line in raw_fallback):
                lines = raw_fallback
            else:
                self.errors.append({"msg": "ALL_THREAD_BT section 为空", "level": "WARN"})
                return

        current_thread = None
        current_frames = []
        bt_depth_limit = 1024

        for line in lines:
            stripped = line.strip()

            thread_info = self._match_thread_header(stripped)
            if thread_info:
                if current_thread is not None:
                    self._finalize_thread(current_thread, current_frames)
                current_thread = thread_info
                current_frames = []
                continue

            if current_thread is None and not self.threads and stripped.startswith('#0'):
                current_thread = {
                    'thread_id': '1',
                    'thread_name': 'main-thread',
                    'os_thread_id': None,
                    'is_lcore': False,
                    'lcore_id': None,
                }
                current_frames = []

            if current_thread is not None:
                frame = self._match_frame(stripped)
                if frame and len(current_frames) < bt_depth_limit:
                    current_frames.append(frame)
                    continue

            if 'signal' in stripped.lower() and 'signal_name' not in self.crash_info:
                sig_match = re.search(
                    r'Signal\s+(\w+)\s+\(([^)]+)\).*address\s+(0x[0-9a-fA-F]+)',
                    stripped
                )
                if sig_match:
                    self.crash_info = {
                        'signal_name': sig_match.group(1),
                        'signal_desc': sig_match.group(2),
                        'fault_address': sig_match.group(3),
                    }

        if current_thread is not None:
            self._finalize_thread(current_thread, current_frames)

    def _match_thread_header(self, line: str):
        match = RE_THREAD_HEADER.match(line)
        if match:
            return self._parse_thread_desc(match.group(1), match.group(2))

        match = RE_THREAD_HEADER_SIMPLE.match(line)
        if match:
            return {
                'thread_id': match.group(1),
                'thread_name': None,
                'os_thread_id': None,
                'is_lcore': False,
                'lcore_id': None,
            }

        return None

    def _parse_thread_desc(self, thread_id: str, desc: str) -> dict:
        info = {
            'thread_id': thread_id,
            'thread_name': None,
            'os_thread_id': None,
            'is_lcore': False,
            'lcore_id': None,
        }

        os_match = re.search(r'(0x[0-9a-fA-F]+)\s*\(LWP\s+(\d+)', desc)
        if os_match:
            info['os_thread_id'] = os_match.group(2)
        else:
            addr_match = re.search(r'(0x[0-9a-fA-F]+)', desc)
            if addr_match:
                info['os_thread_id'] = addr_match.group(1)

        lcore_match = RE_LCORE_NAME.search(desc)
        if lcore_match:
            info['is_lcore'] = True
            info['lcore_id'] = int(lcore_match.group(1))
            info['thread_name'] = desc.strip()
            return info

        worker_match = RE_LCORE_WORKER.search(desc)
        if worker_match:
            info['is_lcore'] = True
            info['lcore_id'] = int(worker_match.group(1))
            info['thread_name'] = desc.strip()
            return info

        info['thread_name'] = desc.strip()[:200]
        return info

    def _match_frame(self, line: str):
        for pattern_name, pattern in FRAME_PATTERNS:
            match = pattern.match(line)
            if match:
                return self._build_frame(pattern_name, match)
        return None

    def _build_frame(self, pattern_name: str, match: re.Match) -> dict:
        frame = {
            'frame_index': int(match.group(1)) if pattern_name != 'unknown' else 0,
            'raw_line': match.group(0),
            'address': None,
            'function_name': None,
            'source_file': None,
            'source_line': None,
            'offset_in_func': None,
            'args': None,
            'resolved': False,
            'is_dpdk_func': False,
            'confidence': 0,
        }

        if pattern_name == 'full':
            frame['address'] = match.group(2)
            frame['function_name'] = match.group(3)
            frame['args'] = self._parse_args(match.group(4))
            frame['source_file'] = match.group(5)
            frame['source_line'] = int(match.group(6))
            frame['resolved'] = True
            frame['confidence'] = 100

        elif pattern_name == 'from':
            frame['address'] = match.group(2)
            frame['function_name'] = match.group(3)
            frame['args'] = self._parse_args(match.group(4))
            frame['source_file'] = match.group(5)
            frame['resolved'] = True
            frame['confidence'] = 90

        elif pattern_name == 'no_line':
            frame['address'] = match.group(2)
            frame['function_name'] = match.group(3)
            frame['args'] = self._parse_args(match.group(4))
            frame['resolved'] = True
            frame['confidence'] = 85

        elif pattern_name == 'unknown':
            frame['address'] = match.group(2)
            frame['function_name'] = '??'
            frame['confidence'] = 0

        elif pattern_name == 'no_addr':
            frame['function_name'] = match.group(2)
            frame['args'] = self._parse_args(match.group(3))
            frame['source_file'] = match.group(4)
            frame['source_line'] = int(match.group(5))
            frame['confidence'] = 60

        if frame['function_name']:
            frame['is_dpdk_func'] = self._is_dpdk_function(frame['function_name'])

        return frame

    def _parse_args(self, args_str: str) -> str:
        if not args_str or not args_str.strip():
            return '[]'

        parts = []
        depth = 0
        current = []
        for ch in args_str:
            if ch in '([':
                depth += 1
                current.append(ch)
            elif ch in ')]':
                depth -= 1
                current.append(ch)
            elif ch == ',' and depth == 0:
                parts.append(''.join(current).strip())
                current = []
            else:
                current.append(ch)

        if current:
            parts.append(''.join(current).strip())

        parts = [part for part in parts if part and part != '<optimized out>']
        return json.dumps(parts, ensure_ascii=False)

    def _is_dpdk_function(self, func_name: str) -> bool:
        return any(func_name.startswith(prefix) for prefix in DPDK_PREFIXES)

    def _finalize_thread(self, thread_info: dict, frames: list):
        if not frames:
            return
        thread_info['frames'] = frames
        thread_info['stack_depth'] = len(frames)
        self.threads.append(thread_info)

    def _parse_registers(self):
        lines = self.sections.get('REGISTERS', [])
        if not lines:
            lines = [line.rstrip('\n') for line in self.raw_lines if line.strip()]

        regs = {}
        for line in lines:
            parts = line.strip().split()
            if len(parts) >= 2 and parts[0].isascii() and parts[1].startswith('0x'):
                regs[parts[0]] = parts[1]
        self.registers = regs

    def _parse_crash_info(self):
        self._parse_siginfo_section()
        if not self.crash_info.get('signal_name'):
            self._parse_crash_info_from_header()

        fault_address = self.crash_info.get('fault_address')
        if not fault_address or fault_address.strip('0x') == '' or \
                fault_address in ('0x0', '0x0000000000000000'):
            pc = self._get_first_frame_pc()
            if pc:
                self.crash_info['fault_address'] = pc

    def _parse_siginfo_section(self):
        for line in self.sections.get('CRASH_INFO', []):
            if 'si_signo' in line:
                signo_match = re.search(r'=\s*(\d+)', line)
                if signo_match:
                    signo = int(signo_match.group(1))
                    self.crash_info['si_signo'] = signo
                    self.crash_info['signal_name'] = SIGNAL_MAP.get(signo, f'SIG?({signo})')
                else:
                    sig_name_match = re.search(r'=\s*(SIG\w+)', line)
                    if sig_name_match:
                        self.crash_info['signal_name'] = sig_name_match.group(1)

            if 'si_addr' in line:
                addr_match = re.search(r'=\s*(0x[0-9a-fA-F]+)', line)
                if addr_match:
                    self.crash_info['fault_address'] = addr_match.group(1)

    def _parse_crash_info_from_header(self):
        lines = self.sections.get('HEADER', []) + [line.rstrip('\n') for line in self.raw_lines]

        for line in lines:
            match = re.search(r'Program terminated with signal (\S+),?\s*(.*)', line)
            if match:
                self.crash_info['signal_name'] = match.group(1)
                if match.group(2):
                    self.crash_info['signal_desc'] = match.group(2).strip().rstrip('.')
                break

        for line in lines:
            if line.lstrip().startswith('#0'):
                addr_match = re.search(r'(0x[0-9a-fA-F]+)', line)
                if addr_match and 'fault_address' not in self.crash_info:
                    self.crash_info['fault_address'] = addr_match.group(1)
                    break

    def _get_first_frame_pc(self):
        if not self.threads:
            return None
        frames = self.threads[0].get('frames', [])
        if not frames:
            return None
        return frames[0].get('address')

    def _parse_memory_mapping(self):
        return

    def _identify_crash_thread(self):
        for thread_id, info in self.thread_map.items():
            if not info.get('crash_thread'):
                continue
            for thread in self.threads:
                if thread.get('thread_id') == thread_id:
                    thread['crash_thread'] = True
                    break

        if not any(thread.get('crash_thread') for thread in self.threads):
            for thread in self.threads:
                frames = thread.get('frames') or []
                if not frames:
                    continue
                top_func = frames[0].get('function_name', '')
                if top_func in CRASH_FUNC_HINTS or \
                        any(hint in top_func for hint in ['raise', 'abort', 'panic']):
                    thread['crash_thread'] = True
                    if not self.crash_info.get('signal_name'):
                        self.crash_info['signal_name'] = 'SIGNAL'
                    break

        if len(self.threads) == 1 and not self.threads[0].get('crash_thread'):
            self.threads[0]['crash_thread'] = True

    def _build_result(self) -> dict:
        return {
            'status': 'ok',
            'parser_version': '1.1',
            'crash_info': self.crash_info if self.crash_info else None,
            'threads': self._format_threads(),
            'registers': self.registers,
            'errors': self.errors,
            'stats': {
                'total_threads': len(self.threads),
                'total_frames': sum(thread.get('stack_depth', 0) for thread in self.threads),
            }
        }

    def _format_threads(self) -> list:
        result = []
        for thread in self.threads:
            result.append({
                'thread_id': thread.get('thread_id'),
                'thread_name': thread.get('thread_name'),
                'os_thread_id': thread.get('os_thread_id'),
                'is_lcore': thread.get('is_lcore', False),
                'lcore_id': thread.get('lcore_id'),
                'crash_thread': thread.get('crash_thread', False),
                'stack_depth': thread.get('stack_depth', 0),
                'frames': thread.get('frames', []),
            })
        return result

    def _error_result(self, msg: str) -> dict:
        return {
            'status': 'error',
            'error': msg,
            'threads': [],
            'crash_info': None,
            'registers': {},
            'errors': [{"msg": msg, "level": "ERROR"}],
            'stats': {'total_threads': 0, 'total_frames': 0},
        }


def main():
    try:
        if len(sys.argv) > 1:
            log_path = sys.argv[1]
        else:
            raw = sys.stdin.read().strip()
            if not raw:
                print(json.dumps({"status": "error", "error": "No input"}))
                sys.exit(1)
            try:
                inp = json.loads(raw)
                log_path = inp.get('log_path')
            except json.JSONDecodeError:
                log_path = raw

        if not log_path:
            print(json.dumps({"status": "error", "error": "Missing log_path"}))
            sys.exit(1)

        parser = GdbLogParser()
        result = parser.parse(log_path)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    except Exception as e:
        print(json.dumps({
            'status': 'error',
            'error': f'Parser exception: {str(e)}',
            'threads': [],
            'crash_info': None,
            'registers': {},
            'errors': [{"msg": str(e), "level": "ERROR"}],
            'stats': {"total_threads": 0, "total_frames": 0},
        }, ensure_ascii=False))
        sys.exit(1)


if __name__ == '__main__':
    main()
