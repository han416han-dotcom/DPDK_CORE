# DPDK Core Analyzer — AGENTS.md

## Overview
Single-module Spring Boot 3.2.5 + Java 17 Maven project. Vue 3 + Element Plus + Vite 5 frontend bundled into the fat jar as `static/`. Core dump → GDB → Python → structured analysis pipeline.

## Entrypoints
- **Backend**: `com.dpdk.core.DpdkCoreApplication` (`src/main/java/com/dpdk/core/`)
- **Frontend**: `frontend/src/main.js` (mounts `App.vue`)
- **Pipeline**: `Pipeline.java` auto-discovers `Parser` beans sorted by `ParseStage` enum: Preprocessor → GdbLogParser → SymbolResolver → DpdkAnalyzer → Aggregator

## Build & Run
```bash
# Build everything (includes Vue frontend build via exec-maven-plugin)
mvn clean package -DskipTests

# Run backend only
mvn spring-boot:run

# Hot-reload frontend (standalone dev server, proxies /api to :8080)
cd frontend && npm run dev
```

## Database
- **Dev profile** (default): MySQL at `10.103.142.107:3306/dpdk_core` (root/root) — H2 is declared in pom but dev profile uses MySQL
- **Prod profile**: `--spring.profiles.active=prod` → MySQL at `localhost:3306/dpdk_core` (root/root)
- Schema auto-created via `spring.sql.init.mode=always` + `classpath:schema.sql` (NOT Flyway)
- `schema.sql` is H2/MySQL compatible (CREATE TABLE IF NOT EXISTS)

## Dev Profile Quirks
- `app.gdb.enabled: false` — GDB is Linux-only; Windows devs must rely on pre-uploaded GDB logs
- `app.parser.auto-parse: false` — parsing is manual in dev
- `app.python.cmd: python` (not `python3`) on Windows
- CORS allows `localhost:5173` (Vite dev server)

## Configuration (application.yml)
| Key | Default |
|-----|---------|
| `app.upload.dir` | `./data/uploads` (dev), `/opt/dpdk-core/uploads` (prod) |
| `spring.servlet.multipart.max-file-size` | 1GB |
| `spring.servlet.multipart.max-request-size` | 2GB |
| `app.python.cmd` | `python3` (Linux), `python` (Windows dev) |
| `app.gdb.timeout-seconds` | 120 |
| `app.parser.confidence-threshold` | 30 |

## Python Scripts
Bundled in jar at `scripts/`, invoked via `PythonExecutor.java`. System requires:
```
pip install pyelftools>=0.31
```
- `parse_gdb_log.py` — parses structured GDB log into JSON threads/frames
- `parse_elf.py` — ELF symbol table extraction (supports `dynsym_only` for stripped binaries)
- `resolve_addr.py` — cross-references addresses with symbol table

## GDB Script
Bundled in jar at `gdb/generate_dpdk_core_log.gdb`. Produces section-delimited output parsed by `parse_gdb_log.py`. Key: `thread apply all bt full` (NOT `frame apply all bt 0` — that caused GDB hangs). The script is loaded from classpath at runtime, not from filesystem.

## API Endpoints (`/api`)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/files/upload` | Upload file (file + fileType) |
| GET | `/files/list` | List uploaded files |
| DELETE | `/files/{id}` | Delete file |
| POST | `/tasks/create` | Create parse task |
| GET | `/tasks/list` | List tasks |
| GET | `/tasks/{id}` | Task detail |
| GET | `/tasks/{id}/result` | Parse result (ParseResultVO) |
| DELETE | `/tasks/{id}` | Delete task |

## Frontend Routes
- `/` — UploadPage (3-step upload wizard: GDB_LOG / CORE_DUMP / EXECUTABLE)
- `/tasks` — TaskListPage
- `/tasks/:id` — TaskDetailPage (FlameGraph, DiagnosisPanel, ThreadDistChart, DpdkRatioChart)

## Important Gotchas
- **No unit tests** exist anywhere in the project
- **No checkstyle/spotbugs/PMD** — no code quality gates
- **Lombok** is used throughout — IDE must support annotation processing
- **GDB crash history**: `frame apply all -s -c "bt 0"` caused GDB to hang/hang; replaced with `set backtrace limit 0` + `thread apply all bt full`
- **ELF strip handling**: When `.symtab` is empty, `SymbolResolver` retries with `.dynsym` (dynamic symbol table, survives strip). If both empty, logs WARN and continues
- **PIE binary resolution**: `SymbolResolver.findSymbol()` falls back to base-offset matching for PIE executables where symbol addresses are pre-relocation offsets
- **lcore detection**: Scans ALL frames (not just stack bottom) for `eal_worker_thread_loop` to handle `start_thread`/`clone3` wrappers
- **0x0 crash address**: When `fault_address == 0x0` (normal for SIGABRT/SIGFPE), falls back to thread #0 frame #0 PC
- **Hash dedup stale file bug**: `FileStorageServiceImpl` now checks `Files.exists()` before returning hash-matched records. If the physical file was deleted, it re-uploads and updates the DB record in-place (preserving FK relationships). `ParseTaskServiceImpl` also validates physical file existence before starting the pipeline.
- **GDB failure → binary fed to text parsers**: `CoreDumpProcessor` now clears `gdbLogPath` and returns `false` when GDB fails, preventing downstream parsers from reading the raw ELF core dump as text.
- **Python stderr pipe deadlock**: `PythonExecutor` reads stderr in a separate thread concurrently with stdout, preventing subprocess hang when stderr output exceeds OS pipe buffer (64KB).
- **favicon.ico 404**: `WebMvcConfig` redirects `/favicon.ico` to `/favicon.svg` to suppress harmless error logs.

## Crash Pattern Diagnosis (v2)
7 种崩溃类型 + 子模式的智能诊断，分四层优先级检测：
1. **寄存器级**：fault_address=0x0 → 空指针；异常标记模式(0xdeadbeef等) → 内存踩踏
2. **栈帧匹配**：abort 链向下搜索触发函数，mempool/ring/driver/copy/free 函数分类 + 子模式
3. **跨线程竞争**：多个线程在 spinlock/ring 函数中 → 锁竞争/Ring错用
4. **信号 fallback**：SIGFPE/SIGBUS/SIGILL 辅助

| 类型 | 子模式 | 可信度 |
|------|--------|--------|
| 空指针解引用 | - | 95% |
| 内存池异常 | double_free / memory_stomp / pool_exhaustion / mbuf_free_crash | 75-85% |
| 驱动冲突 | pmd_crash / non_aligned / device_init | 65-80% |
| 多核竞争 | spinlock_contention / ring_misuse / race_condition | 75% |
| 缓冲区溢出 | - | 65% |
| 断言失败 | - | 60-80% |
| 释放后使用 | use_after_free / double_free | 55% |

涉及文件：`CrashPatternAnalyzer.java`、`ParseResultVO.Diagnosis`、`DiagnosisPanel.vue`、诊断数据存储在 `parse_logs` 表 (stage=`CRASH_DIAGNOSE`)。

## Key Directories
| Path | Contents |
|------|----------|
| `src/main/java/com/dpdk/core/` | Java source (config/controller/mapper/model/parser/service) |
| `frontend/src/` | Vue 3 SPA source |
| `scripts/` | Python scripts (bundled into jar) |
| `gdb/` | GDB automation scripts (bundled into jar) |
| `core_test/` | Test C/C++ sources + compiled ELF binaries for testing |
| `data/` | H2 dev database files, upload directory |
