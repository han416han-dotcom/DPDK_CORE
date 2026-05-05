package com.dpdk.core.service.impl;

import com.dpdk.core.config.AutoScanProperties;
import com.dpdk.core.model.dto.AutoScanCandidateVO;
import com.dpdk.core.model.dto.AutoScanCreateRequest;
import com.dpdk.core.model.dto.AutoScanResultVO;
import com.dpdk.core.model.dto.TaskCreateRequest;
import com.dpdk.core.model.entity.ParseTask;
import com.dpdk.core.model.entity.UploadFile;
import com.dpdk.core.service.AutoScanService;
import com.dpdk.core.service.FileStorageService;
import com.dpdk.core.service.ParseTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AutoScanServiceImpl implements AutoScanService {

    private static final Logger log = LoggerFactory.getLogger(AutoScanServiceImpl.class);

    private static final int ELF_TYPE_EXEC = 2;
    private static final int ELF_TYPE_DYN = 3;
    private static final int ELF_TYPE_CORE = 4;

    private static final Set<String> CORE_EXTENSIONS = Set.of("core", "dump");

    private final AutoScanProperties properties;
    private final FileStorageService fileStorageService;
    private final ParseTaskService parseTaskService;

    public AutoScanServiceImpl(AutoScanProperties properties,
                               FileStorageService fileStorageService,
                               ParseTaskService parseTaskService) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.parseTaskService = parseTaskService;
    }

    @Override
    public AutoScanResultVO scanCandidates() {
        ensureEnabled();
        Path scanRoot = resolveScanRoot();
        AutoScanResultVO result = new AutoScanResultVO();
        result.setScanRoot(scanRoot.toString());

        List<Path> sourceFiles = collectCoreFiles(scanRoot, result.getWarnings());
        List<ExecCandidate> execCandidates = collectExecCandidates(scanRoot, result.getWarnings());
        List<AutoScanCandidateVO> candidates = new ArrayList<>();

        for (Path source : sourceFiles) {
            SourceDescriptor descriptor = describeCoreSource(source);
            MatchResult match = findBestMatch(descriptor, execCandidates);

            AutoScanCandidateVO vo = new AutoScanCandidateVO();
            vo.setSourceName(source.getFileName().toString());
            vo.setSourcePath(source.toAbsolutePath().normalize().toString());
            vo.setSourceType("CORE_DUMP");
            vo.setTaskNameSuggestion(descriptor.taskNameSuggestion);
            vo.setMatched(match.execCandidate != null);
            vo.setMatchScore(match.score);
            vo.setMatchRule(match.rule);
            if (match.execCandidate != null) {
                vo.setExecName(match.execCandidate.path.getFileName().toString());
                vo.setExecPath(match.execCandidate.path.toAbsolutePath().normalize().toString());
            }
            candidates.add(vo);
        }

        candidates.sort(Comparator.comparing(AutoScanCandidateVO::getSourcePath));
        result.setCandidates(candidates);
        result.setTotalSources(candidates.size());
        result.setMatchedSources((int) candidates.stream().filter(c -> Boolean.TRUE.equals(c.getMatched())).count());
        return result;
    }

    @Override
    public ParseTask createTaskFromScan(AutoScanCreateRequest request) {
        ensureEnabled();

        Path sourcePath = normalizePath(request.getSourcePath());
        validateCorePath(sourcePath);

        if (StringUtils.hasText(request.getSourceType())
                && !"CORE_DUMP".equals(request.getSourceType().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("自动扫描模式只支持 CORE_DUMP");
        }

        Path execPath;
        if (StringUtils.hasText(request.getExecPath())) {
            execPath = normalizePath(request.getExecPath());
            validateExecPath(execPath);
        } else {
            SourceDescriptor descriptor = describeCoreSource(sourcePath);
            MatchResult match = findBestMatch(descriptor, collectExecCandidates(resolveScanRoot(), new ArrayList<>()));
            if (match.execCandidate == null) {
                throw new IllegalArgumentException("未能自动匹配到 ELF，请传入 execPath");
            }
            execPath = match.execCandidate.path;
        }

        UploadFile sourceFile = fileStorageService.registerLocalFile(sourcePath, "CORE_DUMP");
        UploadFile execFile = fileStorageService.registerLocalFile(execPath, "EXECUTABLE");

        TaskCreateRequest taskCreateRequest = new TaskCreateRequest();
        taskCreateRequest.setGdbLogFileId(sourceFile.getId());
        taskCreateRequest.setExecFileId(execFile.getId());
        taskCreateRequest.setTaskName(StringUtils.hasText(request.getTaskName())
                ? request.getTaskName()
                : suggestTaskName(sourcePath));

        return parseTaskService.createAndStart(taskCreateRequest);
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Auto scan is disabled");
        }
    }

    private Path resolveScanRoot() {
        if (!StringUtils.hasText(properties.getScanRoot())) {
            throw new IllegalStateException("app.auto-scan.scan-root is not configured");
        }
        Path root = normalizePath(properties.getScanRoot());
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Auto scan root does not exist: " + root);
        }
        return root;
    }

    private Path normalizePath(String raw) {
        return Path.of(raw).toAbsolutePath().normalize();
    }

    private void validateCorePath(Path sourcePath) {
        Path scanRoot = resolveScanRoot();
        if (!sourcePath.startsWith(scanRoot)) {
            throw new IllegalArgumentException("sourcePath is outside scan root: " + sourcePath);
        }
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("sourcePath does not exist or is not a regular file: " + sourcePath);
        }
        if (!isCoreFile(sourcePath)) {
            throw new IllegalArgumentException("sourcePath is not a core dump: " + sourcePath);
        }
    }

    private void validateExecPath(Path execPath) {
        if (!Files.exists(execPath) || !Files.isRegularFile(execPath)) {
            throw new IllegalArgumentException("execPath does not exist or is not a regular file: " + execPath);
        }
        int elfType = detectElfType(execPath);
        if (elfType != ELF_TYPE_EXEC && elfType != ELF_TYPE_DYN) {
            throw new IllegalArgumentException("execPath is not an executable ELF/shared object: " + execPath);
        }
    }

    private List<Path> collectCoreFiles(Path scanRoot, List<String> warnings) {
        try (Stream<Path> stream = Files.walk(scanRoot, properties.getMaxDepth())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isCoreFile)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            warnings.add("Failed to scan core files: " + e.getMessage());
            return List.of();
        }
    }

    private boolean isCoreFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = extensionOf(fileName);
        if (CORE_EXTENSIONS.contains(ext) || fileName.startsWith("core")) {
            return true;
        }
        return detectElfType(path) == ELF_TYPE_CORE;
    }

    private List<ExecCandidate> collectExecCandidates(Path scanRoot, List<String> warnings) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(scanRoot);
        for (String configuredRoot : properties.getElfSearchRoots()) {
            if (!StringUtils.hasText(configuredRoot)) {
                continue;
            }
            Path root = Path.of(configuredRoot);
            if (!root.isAbsolute()) {
                root = scanRoot.resolve(configuredRoot);
            }
            root = root.toAbsolutePath().normalize();
            if (Files.exists(root) && Files.isDirectory(root)) {
                roots.add(root);
            }
        }

        List<ExecCandidate> execCandidates = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> stream = Files.walk(root, properties.getMaxDepth())) {
                stream.filter(Files::isRegularFile)
                        .forEach(path -> {
                            int elfType = detectElfType(path);
                            if (elfType == ELF_TYPE_EXEC || elfType == ELF_TYPE_DYN) {
                                execCandidates.add(new ExecCandidate(path, normalizeArtifactName(path.getFileName().toString())));
                            }
                        });
            } catch (IOException e) {
                warnings.add("Failed to scan ELF root " + root + ": " + e.getMessage());
            }
        }

        return execCandidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.path.toAbsolutePath().normalize().toString(),
                        candidate -> candidate,
                        (a, b) -> a
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(candidate -> candidate.path.toString()))
                .collect(Collectors.toList());
    }

    private SourceDescriptor describeCoreSource(Path sourcePath) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(normalizeArtifactName(sourcePath.getFileName().toString()));
        aliases.add(normalizeArtifactName(stripDecorators(sourcePath.getFileName().toString())));
        aliases.removeIf(String::isBlank);

        return new SourceDescriptor(sourcePath, new ArrayList<>(aliases), suggestTaskName(sourcePath));
    }

    private MatchResult findBestMatch(SourceDescriptor source, List<ExecCandidate> execCandidates) {
        MatchResult best = new MatchResult(null, 0, "UNMATCHED");
        for (ExecCandidate exec : execCandidates) {
            MatchResult current = scoreMatch(source, exec);
            if (current.score > best.score) {
                best = current;
            }
        }
        return best.score >= 60 ? best : new MatchResult(null, 0, "UNMATCHED");
    }

    private MatchResult scoreMatch(SourceDescriptor source, ExecCandidate exec) {
        int bestScore = 0;
        String bestRule = "UNMATCHED";

        for (String alias : source.aliases) {
            if (alias.isBlank()) {
                continue;
            }
            if (alias.equals(exec.normalizedName)) {
                bestScore = Math.max(bestScore, 120);
                bestRule = "NORMALIZED_NAME_EXACT";
            } else if (exec.normalizedName.startsWith(alias) || alias.startsWith(exec.normalizedName)) {
                bestScore = Math.max(bestScore, 90);
                bestRule = "NORMALIZED_NAME_PREFIX";
            } else if (exec.normalizedName.contains(alias) || alias.contains(exec.normalizedName)) {
                bestScore = Math.max(bestScore, 72);
                bestRule = "NORMALIZED_NAME_CONTAINS";
            }
        }

        if (sameDirectory(source.path, exec.path)) {
            bestScore += 12;
            if ("UNMATCHED".equals(bestRule)) {
                bestRule = "SAME_DIRECTORY";
            }
        }

        return new MatchResult(exec, bestScore, bestRule);
    }

    private boolean sameDirectory(Path sourcePath, Path execPath) {
        return sourcePath.getParent() != null
                && execPath.getParent() != null
                && sourcePath.getParent().toAbsolutePath().normalize()
                .equals(execPath.getParent().toAbsolutePath().normalize());
    }

    private String suggestTaskName(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String fileNameOnly(String rawPath) {
        String sanitized = rawPath.replace("\\", "/");
        int slash = sanitized.lastIndexOf('/');
        return slash >= 0 ? sanitized.substring(slash + 1) : sanitized;
    }

    private String stripDecorators(String rawName) {
        String name = fileNameOnly(rawName);
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        stem = stem.replaceFirst("(?i)^core[._-]*", "");
        stem = stem.replaceFirst("(?i)[._-](core|dump|crash)$", "");
        stem = stem.replaceFirst("(?i)(?:[._-]\\d{4,})+$", "");
        return stem;
    }

    private String normalizeArtifactName(String rawName) {
        String stem = stripDecorators(rawName).toLowerCase(Locale.ROOT);
        stem = stem.replaceAll("[^a-z0-9]+", "_");
        stem = stem.replaceAll("_+", "_");
        stem = stem.replaceAll("^_|_$", "");
        return stem;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private int detectElfType(Path path) {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] header = in.readNBytes(18);
            if (header.length < 18) {
                return -1;
            }
            if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
                return -1;
            }
            boolean littleEndian = header[5] == 1;
            int lo = header[16] & 0xFF;
            int hi = header[17] & 0xFF;
            return littleEndian ? (hi << 8) | lo : (lo << 8) | hi;
        } catch (IOException e) {
            return -1;
        }
    }

    private static class SourceDescriptor {
        private final Path path;
        private final List<String> aliases;
        private final String taskNameSuggestion;

        private SourceDescriptor(Path path, List<String> aliases, String taskNameSuggestion) {
            this.path = path;
            this.aliases = aliases;
            this.taskNameSuggestion = taskNameSuggestion;
        }
    }

    private static class ExecCandidate {
        private final Path path;
        private final String normalizedName;

        private ExecCandidate(Path path, String normalizedName) {
            this.path = path;
            this.normalizedName = normalizedName;
        }
    }

    private static class MatchResult {
        private final ExecCandidate execCandidate;
        private final int score;
        private final String rule;

        private MatchResult(ExecCandidate execCandidate, int score, String rule) {
            this.execCandidate = execCandidate;
            this.score = score;
            this.rule = rule;
        }
    }
}
