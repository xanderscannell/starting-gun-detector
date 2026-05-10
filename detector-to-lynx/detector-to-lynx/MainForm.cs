using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using Microsoft.Extensions.Logging;

namespace detector_to_lynx
{
    public partial class MainForm : Form
    {
        private readonly ILogger<MainForm> _logger = Program.LoggerFactory.CreateLogger<MainForm>();
        private readonly FirestoreService _firestoreService = new();
        private System.Windows.Forms.Timer? _pollTimer;
        private string? _sessionCode;
        private List<DetectionEntry> _lastDetections = [];
        private int _consecutivePollFailures = 0;

        private readonly LifDirectoryMonitor _lifMonitor = new();
        private MatchResult? _lastMatchResult;
        private List<ManualStartEntry> _manualStartEntries = [];

        // Maps DataGridView row index → DetectionEntry (null = Lynx-only row).
        private readonly Dictionary<int, DetectionEntry?> _rowToDetection = [];

        public MainForm()
        {
            InitializeComponent();
            LoadSavedSettings();
            AppendVersionToTitle();
            UpdateCalibrationSummaryLabel();

            _lifMonitor.StartTimesChanged += OnLynxStartTimesChanged;
        }

        private void LoadSavedSettings()
        {
            sessionCodeTextBox.Text = SavedSettingsManager.SessionCode;
            lynxPortTextBox.Text = SavedSettingsManager.LynxRemotePortString;
            matchWindowTextBox.Text = SavedSettingsManager.MatchWindowSeconds.ToString("G", CultureInfo.InvariantCulture);

            var dir = SavedSettingsManager.LynxResultsDirectory;
            if (!string.IsNullOrWhiteSpace(dir))
            {
                lynxResultsDirTextBox.Text = dir;
                _lifMonitor.SetDirectory(dir);
                UpdateLynxResultsStatus();
            }
        }

        private void AppendVersionToTitle()
        {
            Version? version = Assembly.GetExecutingAssembly().GetName().Version;
            if (version != null)
            {
                var versionString = version.Major + "." + version.Minor +
                                    (version.Build > 0 ? "." + version.Build : "");
                Text += " v" + versionString;
                _logger.LogInformation("detector-to-lynx v{Version} started", versionString);
            }
        }

        // ─── Join / Leave ────────────────────────────────────────────────────

        private async void joinButton_Click(object sender, EventArgs e)
        {
            if (_sessionCode != null)
            {
                LeaveSession();
                return;
            }

            var code = sessionCodeTextBox.Text.Trim().ToUpperInvariant();
            if (code.Length != 4)
            {
                MessageBox.Show("Please enter a 4-character session code.", "Invalid Code",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            joinButton.Enabled = false;
            UpdateStatus("Connecting to session...");

            bool valid;
            try
            {
                valid = await _firestoreService.ValidateSessionAsync(code);
            }
            catch (Exception ex)
            {
                joinButton.Enabled = true;
                UpdateStatus("Connection failed.", error: true);
                MessageBox.Show($"Could not connect to Firebase:\n{ex.Message}", "Error",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            if (!valid)
            {
                joinButton.Enabled = true;
                UpdateStatus("Session not found.", error: true);
                MessageBox.Show($"No session with code '{code}' was found.\n\nMake sure the Android app has created the session.",
                    "Session Not Found", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            _sessionCode = code;
            SavedSettingsManager.SessionCode = code;
            joinButton.Enabled = true;
            joinButton.Text = "Leave Session";
            sessionCodeTextBox.Enabled = false;
            sessionStatusLabel.Text = $"Connected: {code}";
            sessionStatusLabel.ForeColor = Color.Green;
            UpdateStatus($"Joined session {code}.");
            _logger.LogInformation("Joined session {SessionCode}", code);
            StartPolling();
        }

        private void LeaveSession()
        {
            StopPolling();
            _sessionCode = null;
            _lastDetections.Clear();
            _lastMatchResult = null;
            _rowToDetection.Clear();
            calibrationGridView.Rows.Clear();
            joinButton.Text = "Join Session";
            sessionCodeTextBox.Enabled = true;
            sessionStatusLabel.Text = "Not connected";
            sessionStatusLabel.ForeColor = SystemColors.GrayText;
            selectedTimeLabel.Text = "Selected: —";
            sendToLynxButton.Enabled = false;
            UpdateCalibrationSummaryLabel();
            UpdateStatus("Left session.");
            _logger.LogInformation("Left session");
        }

        // ─── Polling ─────────────────────────────────────────────────────────

        private void StartPolling()
        {
            _pollTimer = new System.Windows.Forms.Timer { Interval = 2000 };
            _pollTimer.Tick += async (_, _) => await PollDetectionsAsync();
            _pollTimer.Start();
            // Poll immediately on join
            _ = PollDetectionsAsync();
        }

        private void StopPolling()
        {
            _pollTimer?.Stop();
            _pollTimer?.Dispose();
            _pollTimer = null;
        }

        private async Task PollDetectionsAsync()
        {
            if (_sessionCode == null) return;

            List<DetectionEntry> detections;
            try
            {
                detections = await _firestoreService.GetDetectionsAsync(_sessionCode);
            }
            catch (Exception ex)
            {
                _consecutivePollFailures++;
                _logger.LogError(ex, "Poll failed for session {SessionCode} (consecutive failures: {Count})", _sessionCode, _consecutivePollFailures);
                return;
            }

            _consecutivePollFailures = 0;

            if (detections.Count == _lastDetections.Count &&
                detections.Select(d => d.ClientTimestamp).SequenceEqual(
                    _lastDetections.Select(d => d.ClientTimestamp)))
                return;

            _logger.LogInformation(
                "Detections updated for session {SessionCode}: {Count} total, newest {Newest}",
                _sessionCode, detections.Count,
                detections.Count > 0 ? detections[0].Timestamp : "none");
            _lastDetections = detections;
            RebuildCalibrationGrid();
        }

        // ─── Lynx directory / matching ───────────────────────────────────────

        private void browseResultsDirButton_Click(object sender, EventArgs e)
        {
            using var dialog = new FolderBrowserDialog
            {
                Description = "Select the FinishLynx results directory",
                UseDescriptionForTitle = true,
                SelectedPath = SavedSettingsManager.LynxResultsDirectory
            };

            if (dialog.ShowDialog(this) != DialogResult.OK) return;

            var path = dialog.SelectedPath;
            SavedSettingsManager.LynxResultsDirectory = path;
            lynxResultsDirTextBox.Text = path;
            _lifMonitor.SetDirectory(path);
            UpdateLynxResultsStatus();
            RebuildCalibrationGrid();
        }

        private void OnLynxStartTimesChanged(IReadOnlyList<LynxStartEntry> entries)
        {
            if (InvokeRequired)
            {
                BeginInvoke(() => OnLynxStartTimesChanged(entries));
                return;
            }
            UpdateLynxResultsStatus();
            RebuildCalibrationGrid();
        }

        private void UpdateLynxResultsStatus()
        {
            var entries = _lifMonitor.CurrentStartTimes;
            var dir = SavedSettingsManager.LynxResultsDirectory;

            if (string.IsNullOrWhiteSpace(dir))
            {
                lynxResultsStatusLabel.Text = "Not watching";
                lynxResultsStatusLabel.ForeColor = SystemColors.GrayText;
            }
            else if (!Directory.Exists(dir))
            {
                lynxResultsStatusLabel.Text = "Directory not found";
                lynxResultsStatusLabel.ForeColor = Color.OrangeRed;
            }
            else
            {
                lynxResultsStatusLabel.Text =
                    $"Watching: {entries.Count} .lif file{(entries.Count != 1 ? "s" : "")} with start times";
                lynxResultsStatusLabel.ForeColor = entries.Count > 0 ? Color.Green : SystemColors.GrayText;
            }
        }

        private void matchWindowTextBox_TextChanged(object sender, EventArgs e)
        {
            if (double.TryParse(matchWindowTextBox.Text.Trim(), NumberStyles.Any,
                    CultureInfo.InvariantCulture, out var seconds) && seconds > 0)
            {
                matchWindowTextBox.BackColor = SystemColors.Window;
                SavedSettingsManager.MatchWindowSeconds = seconds;
                RebuildCalibrationGrid();
            }
            else
            {
                matchWindowTextBox.BackColor = Color.MistyRose;
            }
        }

        // ─── Manual start times ──────────────────────────────────────────────

        private void addManualTimeButton_Click(object sender, EventArgs e) => AddManualTime();

        private void manualTimeTextBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Return)
            {
                e.Handled = true;
                e.SuppressKeyPress = true;
                AddManualTime();
            }
        }

        private void AddManualTime()
        {
            var text = manualTimeTextBox.Text.Trim();
            if (!TryParseTimeOfDay(text, out var ts))
            {
                manualTimeTextBox.BackColor = Color.MistyRose;
                return;
            }
            manualTimeTextBox.BackColor = SystemColors.Window;
            manualTimeTextBox.Clear();
            _manualStartEntries.Add(new ManualStartEntry(ts));
            manualTimesListBox.Items.Add(FormatTimeSpan(ts));
            RebuildCalibrationGrid();
        }

        private void removeManualTimeButton_Click(object sender, EventArgs e)
        {
            var idx = manualTimesListBox.SelectedIndex;
            if (idx < 0) return;
            _manualStartEntries.RemoveAt(idx);
            manualTimesListBox.Items.RemoveAt(idx);
            removeManualTimeButton.Enabled = manualTimesListBox.SelectedIndex >= 0;
            RebuildCalibrationGrid();
        }

        private void manualTimesListBox_SelectedIndexChanged(object sender, EventArgs e)
        {
            removeManualTimeButton.Enabled = manualTimesListBox.SelectedIndex >= 0;
        }

        // ─── Calibration grid ────────────────────────────────────────────────

        private void RebuildCalibrationGrid()
        {
            var manualTimeSet = _manualStartEntries.Select(e => e.StartTime).ToHashSet();
            var lynxTimes = _lifMonitor.CurrentStartTimes
                .Select(e => e.StartTime)
                .Concat(_manualStartEntries.Select(e => e.StartTime))
                .Distinct()
                .ToList();

            var detectionTimes = new List<TimeSpan>();
            foreach (var d in _lastDetections)
            {
                if (TryParseTimeOfDay(d.Timestamp, out var ts))
                    detectionTimes.Add(ts);
            }

            var windowMs = SavedSettingsManager.MatchWindowSeconds * 1000.0;
            var match = CalibrationMatcher.Match(detectionTimes, lynxTimes, windowMs);
            _lastMatchResult = match;

            // Preserve selected detection timestamp before rebuild.
            var selectedTimestamp = GetSelectedDetectionTimestamp();

            calibrationGridView.SuspendLayout();
            calibrationGridView.Rows.Clear();
            _rowToDetection.Clear();

            foreach (var row in match.Rows.AsEnumerable().Reverse())
            {
                var detectionCell = row.Detection.HasValue ? FormatTimeSpan(row.Detection.Value) : "—";
                var lynxCell = row.LynxStart.HasValue ? FormatTimeSpan(row.LynxStart.Value) : "—";
                var diffCell = row.IsMatched && row.ResidualMs(match.OffsetMs) is double res
                    ? FormatResidual(res)
                    : "—";

                // Resolve matching DetectionEntry for this row.
                DetectionEntry? entry = null;
                if (row.Detection.HasValue)
                {
                    var formatted = FormatTimeSpan(row.Detection.Value);
                    entry = _lastDetections.FirstOrDefault(d => d.Timestamp == formatted);
                }

                var deviceCell = entry?.DisplayName ?? string.Empty;

                int rowIdx = calibrationGridView.Rows.Add(detectionCell, lynxCell, diffCell, deviceCell);
                _rowToDetection[rowIdx] = entry;

                // Tint rows based on Lynx time source.
                if (row.LynxStart.HasValue && manualTimeSet.Contains(row.LynxStart.Value))
                    calibrationGridView.Rows[rowIdx].DefaultCellStyle.BackColor = Color.FromArgb(255, 248, 220);  // amber = manual
                else if (!row.Detection.HasValue)
                    calibrationGridView.Rows[rowIdx].DefaultCellStyle.BackColor = Color.FromArgb(245, 245, 255);  // blue = .lif only
            }

            calibrationGridView.ResumeLayout();

            // Restore selection.
            if (selectedTimestamp != null)
            {
                foreach (DataGridViewRow r in calibrationGridView.Rows)
                {
                    if (r.Cells["colDetection"].Value?.ToString() == selectedTimestamp)
                    {
                        r.Selected = true;
                        break;
                    }
                }
            }

            UpdateCalibrationSummaryLabel();
        }

        private void calibrationGridView_SelectionChanged(object sender, EventArgs e)
        {
            var detection = GetSelectedDetectionEntry();
            if (detection != null)
            {
                selectedTimeLabel.Text = $"Selected: {detection.Timestamp}";
                sendToLynxButton.Enabled = true;
            }
            else
            {
                selectedTimeLabel.Text = "Selected: —";
                sendToLynxButton.Enabled = false;
            }
        }

        private DetectionEntry? GetSelectedDetectionEntry()
        {
            if (calibrationGridView.SelectedRows.Count == 0) return null;
            var rowIdx = calibrationGridView.SelectedRows[0].Index;
            _rowToDetection.TryGetValue(rowIdx, out var entry);
            return entry; // null if Lynx-only row or index not found
        }

        private string? GetSelectedDetectionTimestamp() => GetSelectedDetectionEntry()?.Timestamp;

        // ─── Send to FinishLynx ───────────────────────────────────────────────

        private void sendToLynxButton_Click(object sender, EventArgs e)
        {
            var detection = GetSelectedDetectionEntry();
            if (detection == null) return;
            _ = CreateStartAsync(detection.Timestamp);
        }

        private async Task CreateStartAsync(string timeString)
        {
            var lynxService = new FinishLynxRemoteService(SavedSettingsManager.LynxRemotePort);
            FinishLynxReply status;
            string response;
            var averageOffsetMs = _lastMatchResult?.OffsetMs ?? 0.0;
            var adjustedTimeString = ApplyOffsetToTimeString(timeString, averageOffsetMs);
            var hasCalibration = (_lastMatchResult?.PairCount ?? 0) > 0;

            sendToLynxButton.Enabled = false;
            UpdateStatus("Sending to FinishLynx...");

            try
            {
                (status, response) = await lynxService.StartCreateAsync(adjustedTimeString);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to connect to FinishLynx for StartCreate");
                sendToLynxButton.Enabled = GetSelectedDetectionEntry() != null;
                UpdateStatus("Failed to connect to FinishLynx.", error: true);
                MessageBox.Show(
                    $"Failed to connect to FinishLynx Remote Control:\n{ex.Message}\n\n" +
                    "Make sure FinishLynx is running with Remote Control enabled, " +
                    "and that the port number matches.",
                    "Connection Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            if (status == FinishLynxReply.Ok)
            {
                sendToLynxButton.Enabled = GetSelectedDetectionEntry() != null;
                _logger.LogInformation(
                    "Start created in FinishLynx: {AdjustedTime} (original: {OriginalTime}, calibrated: {HasCalibration})",
                    adjustedTimeString, timeString, hasCalibration);
                UpdateStatus(hasCalibration
                    ? $"Start created in FinishLynx: {adjustedTimeString} (calibrated from {timeString})"
                    : $"Start created in FinishLynx: {adjustedTimeString}");
                return;
            }

            // TOD start failed — try offset fallback.
            double offsetSeconds;
            try
            {
                offsetSeconds = ComputeOffset(adjustedTimeString, DateTime.Now);
            }
            catch
            {
                sendToLynxButton.Enabled = GetSelectedDetectionEntry() != null;
                UpdateStatus("Failed to create start.", error: true);
                MessageBox.Show(
                    $"Could not parse timestamp '{timeString}' and the time-of-day start was rejected by FinishLynx.\n\nResponse: {response}",
                    "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            _logger.LogInformation("TOD start rejected by FinishLynx; trying offset fallback: {Offset:F3}s", offsetSeconds);

            try
            {
                (status, response) = await lynxService.StartCreateAsync(null, offsetSeconds.ToString("F3"));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to connect to FinishLynx for offset fallback StartCreate");
                sendToLynxButton.Enabled = GetSelectedDetectionEntry() != null;
                UpdateStatus("Failed to connect to FinishLynx.", error: true);
                MessageBox.Show($"Failed to connect to FinishLynx:\n{ex.Message}",
                    "Connection Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            sendToLynxButton.Enabled = GetSelectedDetectionEntry() != null;

            if (status == FinishLynxReply.Ok)
            {
                UpdateStatus(hasCalibration
                    ? $"Start created in FinishLynx (offset): {adjustedTimeString} (calibrated from {timeString})"
                    : $"Start created in FinishLynx (offset): {adjustedTimeString}");
            }
            else
            {
                UpdateStatus("Failed to create start.", error: true);
                MessageBox.Show($"FinishLynx rejected the start command.\n\nResponse: {response}",
                    "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        // ─── Calibration summary ─────────────────────────────────────────────

        private void UpdateCalibrationSummaryLabel()
        {
            var match = _lastMatchResult;
            if (match == null || match.PairCount == 0)
            {
                calibrationSummaryLabel.Text = "Calibration offset: none";
                return;
            }

            var offsetSec = match.OffsetMs / 1000.0;
            var residuals = match.MatchedRows
                .Select(r => r.ResidualMs(match.OffsetMs))
                .Where(v => v.HasValue)
                .Select(v => v!.Value)
                .ToList();

            if (residuals.Count == 0)
            {
                calibrationSummaryLabel.Text =
                    $"Calibration offset: {offsetSec:+0.000;-0.000;0.000}s ({match.PairCount} pairs)";
                return;
            }

            var maxAbs = residuals.Max(Math.Abs);
            var avgAbs = residuals.Average(Math.Abs);
            calibrationSummaryLabel.Text =
                $"Offset: {offsetSec:+0.000;-0.000;0.000}s ({match.PairCount} pairs)  " +
                $"Residuals: max ±{maxAbs:F0}ms  avg ±{avgAbs:F0}ms";
        }

        // ─── Helpers ─────────────────────────────────────────────────────────

        private static string FormatTimeSpan(TimeSpan t) =>
            t.ToString(@"hh\:mm\:ss\.fff", CultureInfo.InvariantCulture);

        private static string FormatResidual(double ms)
        {
            if (Math.Abs(ms) < 1000)
                return $"{ms:+0;-0;0}ms";
            return $"{ms / 1000.0:+0.000;-0.000;0.000}s";
        }

        private static bool TryParseTimeOfDay(string value, out TimeSpan timeOfDay)
        {
            return TimeSpan.TryParseExact(
                       value,
                       [@"hh\:mm\:ss\.fff", @"h\:mm\:ss\.fff", @"hh\:mm\:ss", @"h\:mm\:ss"],
                       CultureInfo.InvariantCulture,
                       out timeOfDay)
                   || TimeSpan.TryParse(value, CultureInfo.InvariantCulture, out timeOfDay);
        }

        public static string ApplyOffsetToTimeString(string timeString, double offsetMilliseconds)
        {
            if (!TryParseTimeOfDay(timeString, out var timeOfDay))
                throw new FormatException($"Cannot parse time string: {timeString}");

            var adjusted = timeOfDay.Add(TimeSpan.FromMilliseconds(offsetMilliseconds));
            var day = TimeSpan.FromDays(1);

            while (adjusted < TimeSpan.Zero)
                adjusted += day;

            while (adjusted >= day)
                adjusted -= day;

            return adjusted.ToString(@"hh\:mm\:ss\.fff", CultureInfo.InvariantCulture);
        }

        /// <summary>
        /// Computes the elapsed seconds between a HH:mm:ss.fff timestamp string and <paramref name="now"/>.
        /// Returns 0 if the timestamp is in the future. Throws <see cref="FormatException"/> if unparseable.
        /// </summary>
        public static double ComputeOffset(string timeString, DateTime now)
        {
            if (!TryParseTimeOfDay(timeString, out var timeOfDay))
                throw new FormatException($"Cannot parse time string: {timeString}");

            var startDateTime = new DateTime(now.Year, now.Month, now.Day,
                timeOfDay.Hours, timeOfDay.Minutes, timeOfDay.Seconds, timeOfDay.Milliseconds);

            if (startDateTime > now)
                return 0;

            return (now - startDateTime).TotalSeconds;
        }

        // ─── Settings ────────────────────────────────────────────────────────

        private void lynxPortTextBox_TextChanged(object sender, EventArgs e)
        {
            SavedSettingsManager.LynxRemotePortString = lynxPortTextBox.Text.Trim();
        }

        // ─── Status strip ────────────────────────────────────────────────────

        private void UpdateStatus(string message, bool error = false)
        {
            var timestamp = DateTime.Now.ToString("HH:mm:ss");
            toolStripStatusLabel.Text = $"[{timestamp}] {message}";
            toolStripStatusLabel.BackColor = error ? Color.LightCoral : SystemColors.Control;
        }

        // ─── Lifecycle ───────────────────────────────────────────────────────

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            try
            {
                StopPolling();
                _lifMonitor.Dispose();
            }
            catch { }
            base.OnFormClosing(e);
        }
    }
}

