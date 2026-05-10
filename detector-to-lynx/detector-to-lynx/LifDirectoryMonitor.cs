using Microsoft.Extensions.Logging;

namespace detector_to_lynx
{
    /// <summary>A single FinishLynx start time entry read from a .lif file.</summary>
    public record LynxStartEntry(string FileName, TimeSpan StartTime);

    /// <summary>A manually-entered known gun-fire time used as a calibration reference.</summary>
    public record ManualStartEntry(TimeSpan StartTime);

    /// <summary>
    /// Monitors a directory for new / updated .lif files and raises
    /// <see cref="StartTimesChanged"/> whenever the set of parsed start times changes.
    ///
    /// Thread-safety: <see cref="SetDirectory"/> and <see cref="Dispose"/> may be called
    /// from any thread. The <see cref="StartTimesChanged"/> event is raised on a thread-pool
    /// thread; subscribers must marshal to the UI thread (e.g. <c>InvokeRequired</c> / <c>BeginInvoke</c>).
    /// </summary>
    public sealed class LifDirectoryMonitor : IDisposable
    {
        private FileSystemWatcher? _watcher;
        private string? _directory;
        private List<LynxStartEntry> _current = [];

        // Debounce: coalesce rapid file-change events (e.g. FinishLynx writing a file)
        // into a single rescan after a short delay.
        private System.Threading.Timer? _debounceTimer;
        private const int DebounceMs = 300;

        private readonly object _lock = new();
        private readonly ILogger<LifDirectoryMonitor> _logger;

        public LifDirectoryMonitor(ILoggerFactory? loggerFactory = null)
        {
            _logger = (loggerFactory ?? Program.LoggerFactory ?? Microsoft.Extensions.Logging.Abstractions.NullLoggerFactory.Instance).CreateLogger<LifDirectoryMonitor>();
        }

        /// <summary>
        /// Raised on a thread-pool thread whenever the set of start times changes.
        /// Subscribers must marshal to the UI thread themselves (e.g. InvokeRequired / BeginInvoke).
        /// </summary>
        public event Action<IReadOnlyList<LynxStartEntry>>? StartTimesChanged;

        /// <summary>The most recently scanned start times (may be empty, never null).</summary>
        public IReadOnlyList<LynxStartEntry> CurrentStartTimes
        {
            get { lock (_lock) { return _current; } }
        }

        /// <summary>
        /// Starts monitoring <paramref name="path"/>, or stops monitoring when
        /// <paramref name="path"/> is null or empty.
        /// </summary>
        public void SetDirectory(string? path)
        {
            lock (_lock)
            {
                StopWatcher();
                _directory = null;
                _current = [];

                if (string.IsNullOrWhiteSpace(path) || !Directory.Exists(path))
                    return;

                _directory = path;
                StartWatcher(path);
            }

            // Initial scan on the calling thread; result raised on UI thread.
            ScheduleRescan();
        }

        // ── FileSystemWatcher ────────────────────────────────────────────────

        private void StartWatcher(string path)
        {
            _watcher = new FileSystemWatcher(path, "*.lif")
            {
                NotifyFilter = NotifyFilters.FileName | NotifyFilters.LastWrite,
                IncludeSubdirectories = false,
                EnableRaisingEvents = true
            };

            _watcher.Created += OnFileChanged;
            _watcher.Changed += OnFileChanged;
            _watcher.Deleted += OnFileChanged;
            _watcher.Renamed += OnFileChanged;
        }

        private void StopWatcher()
        {
            _debounceTimer?.Dispose();
            _debounceTimer = null;

            if (_watcher != null)
            {
                _watcher.EnableRaisingEvents = false;
                _watcher.Dispose();
                _watcher = null;
            }
        }

        private void OnFileChanged(object sender, FileSystemEventArgs e)
        {
            ScheduleRescan();
        }

        // ── Debounced rescan ─────────────────────────────────────────────────

        private void ScheduleRescan()
        {
            lock (_lock)
            {
                // Reset the debounce timer each time a change fires.
                if (_debounceTimer == null)
                {
                    _debounceTimer = new System.Threading.Timer(
                        _ => DoRescan(),
                        null,
                        DebounceMs,
                        Timeout.Infinite);
                }
                else
                {
                    _debounceTimer.Change(DebounceMs, Timeout.Infinite);
                }
            }
        }

        private void DoRescan()
        {
            string? directory;
            lock (_lock) { directory = _directory; }

            if (directory == null) return;

            var entries = new List<LynxStartEntry>();

            try
            {
                foreach (var file in Directory.EnumerateFiles(directory, "*.lif"))
                {
                    try
                    {
                        var startTime = LifFileParser.ParseStartTime(file);
                        if (startTime.HasValue)
                            entries.Add(new LynxStartEntry(Path.GetFileName(file), startTime.Value));
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "Failed to parse .lif file {File}", Path.GetFileName(file));
                    }
                }
            }
            catch (Exception ex)
            {
                // If the directory becomes unavailable, return whatever we have.
                _logger.LogWarning(ex, "Failed to enumerate .lif files in {Directory}", directory);
            }

            entries.Sort((a, b) => a.StartTime.CompareTo(b.StartTime));

            bool changed;
            lock (_lock)
            {
                changed = !EntriesEqual(_current, entries);
                if (changed)
                    _current = entries;
            }

            if (changed)
            {
                _logger.LogInformation(
                    "Lynx .lif files changed: {Count} start time(s) found in {Directory}",
                    entries.Count, directory);
                StartTimesChanged?.Invoke((IReadOnlyList<LynxStartEntry>)entries);
            }
        }

        private static bool EntriesEqual(List<LynxStartEntry> a, List<LynxStartEntry> b)
        {
            if (a.Count != b.Count) return false;
            for (int i = 0; i < a.Count; i++)
                if (a[i] != b[i]) return false;
            return true;
        }

        // ── IDisposable ──────────────────────────────────────────────────────

        public void Dispose()
        {
            lock (_lock) { StopWatcher(); }
        }
    }
}
