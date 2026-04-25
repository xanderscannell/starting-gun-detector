using System.Diagnostics;
using System.Reflection;
using System.Globalization;

namespace detector_to_lynx
{
    public partial class MainForm : Form
    {
        private readonly FirestoreService _firestoreService = new();
        private System.Windows.Forms.Timer? _pollTimer;
        private string? _sessionCode;
        private List<DetectionEntry> _lastDetections = [];
        private readonly Dictionary<long, double> _calibrationOffsetsMsByClientTimestamp = [];

        public MainForm()
        {
            InitializeComponent();
            LoadSavedSettings();
            AppendVersionToTitle();
            UpdateCalibrationSummaryLabel();
            UpdateCalibrationControlsState();
        }

        private void LoadSavedSettings()
        {
            sessionCodeTextBox.Text = SavedSettingsManager.SessionCode;
            lynxPortTextBox.Text = SavedSettingsManager.LynxRemotePortString;
        }

        private void AppendVersionToTitle()
        {
            Version? version = Assembly.GetExecutingAssembly().GetName().Version;
            if (version != null)
            {
                Text += " v" + version.Major + "." + version.Minor +
                        (version.Build > 0 ? "." + version.Build : "");
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
            StartPolling();
        }

        private void LeaveSession()
        {
            StopPolling();
            _sessionCode = null;
            _lastDetections.Clear();
            _calibrationOffsetsMsByClientTimestamp.Clear();
            detectionsListBox.Items.Clear();
            joinButton.Text = "Join Session";
            sessionCodeTextBox.Enabled = true;
            sessionStatusLabel.Text = "Not connected";
            sessionStatusLabel.ForeColor = SystemColors.GrayText;
            selectedTimeLabel.Text = "Selected: —";
            matchingTimeTextBox.Text = string.Empty;
            sendToLynxButton.Enabled = false;
            UpdateCalibrationSummaryLabel();
            UpdateCalibrationControlsState();
            UpdateStatus("Left session.");
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
                Debug.Print("Poll failed: " + ex.Message);
                return; // Silently skip failed polls; status will update on next success
            }

            // Update list only when content changes
            if (detections.Count == _lastDetections.Count &&
                detections.Select(d => d.ClientTimestamp).SequenceEqual(
                    _lastDetections.Select(d => d.ClientTimestamp)))
                return;

            _lastDetections = detections;

            var selectedTimestamp = (detectionsListBox.SelectedItem as string)?.Split(" — ")[0].Trim();

            detectionsListBox.BeginUpdate();
            detectionsListBox.Items.Clear();
            foreach (var d in detections)
                detectionsListBox.Items.Add($"{d.Timestamp} — {d.DisplayName}");
            detectionsListBox.EndUpdate();

            // Restore selection if it still exists
            if (selectedTimestamp != null)
            {
                for (int i = 0; i < detectionsListBox.Items.Count; i++)
                {
                    if (detectionsListBox.Items[i].ToString()?.StartsWith(selectedTimestamp) == true)
                    {
                        detectionsListBox.SelectedIndex = i;
                        break;
                    }
                }
            }
        }

        // ─── Detection selection ─────────────────────────────────────────────

        private void detectionsListBox_SelectedIndexChanged(object sender, EventArgs e)
        {
            var selected = detectionsListBox.SelectedItem as string;
            if (selected != null)
            {
                var timestamp = selected.Split(" — ")[0].Trim();
                selectedTimeLabel.Text = $"Selected: {timestamp}";
                sendToLynxButton.Enabled = true;
            }
            else
            {
                selectedTimeLabel.Text = "Selected: —";
                sendToLynxButton.Enabled = false;
            }

            UpdateCalibrationControlsState();
        }

        private void matchingTimeTextBox_TextChanged(object sender, EventArgs e)
        {
            UpdateCalibrationControlsState();
        }

        private void calibrateButton_Click(object sender, EventArgs e)
        {
            if (!TryGetSelectedDetection(out var selectedDetection))
                return;

            var detectionTime = selectedDetection.Timestamp;
            var finishLynxTime = matchingTimeTextBox.Text.Trim();

            double offsetMs;
            try
            {
                offsetMs = ComputeCalibrationOffsetMilliseconds(detectionTime, finishLynxTime);
            }
            catch (FormatException)
            {
                MessageBox.Show(
                    "Please enter a valid FinishLynx time in HH:mm:ss.fff format.",
                    "Invalid Time",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning
                );
                UpdateCalibrationControlsState();
                return;
            }

            _calibrationOffsetsMsByClientTimestamp[selectedDetection.ClientTimestamp] = offsetMs;

            UpdateCalibrationSummaryLabel();
            UpdateCalibrationControlsState();

            var signedSeconds = offsetMs / 1000.0;
            UpdateStatus($"Calibration saved: {signedSeconds:+0.000;-0.000;0.000}s");
        }

        private void clearCalibrationsButton_Click(object sender, EventArgs e)
        {
            _calibrationOffsetsMsByClientTimestamp.Clear();
            UpdateCalibrationSummaryLabel();
            UpdateCalibrationControlsState();
            UpdateStatus("Calibration offsets cleared.");
        }

        // ─── Send to FinishLynx ───────────────────────────────────────────────

        private void sendToLynxButton_Click(object sender, EventArgs e)
        {
            if (!TryGetSelectedDetection(out var selectedDetection))
                return;

            _ = CreateStartAsync(selectedDetection.Timestamp);
        }

        private async Task CreateStartAsync(string timeString)
        {
            var lynxService = new FinishLynxRemoteService(SavedSettingsManager.LynxRemotePort);
            FinishLynxReply status;
            string response;
            var averageOffsetMs = GetAverageCalibrationOffsetMilliseconds();
            var adjustedTimeString = ApplyOffsetToTimeString(timeString, averageOffsetMs);
            var hasCalibration = _calibrationOffsetsMsByClientTimestamp.Count > 0;

            sendToLynxButton.Enabled = false;
            UpdateStatus("Sending to FinishLynx...");

            try
            {
                (status, response) = await lynxService.StartCreateAsync(adjustedTimeString);
            }
            catch (Exception ex)
            {
                sendToLynxButton.Enabled = detectionsListBox.SelectedIndex >= 0;
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
                sendToLynxButton.Enabled = detectionsListBox.SelectedIndex >= 0;
                if (hasCalibration)
                {
                    UpdateStatus($"Start created in FinishLynx: {adjustedTimeString} (calibrated from {timeString})");
                }
                else
                {
                    UpdateStatus($"Start created in FinishLynx: {adjustedTimeString}");
                }
                return;
            }

            // TOD start failed — try offset fallback
            double offsetSeconds;
            try
            {
                offsetSeconds = ComputeOffset(adjustedTimeString, DateTime.Now);
            }
            catch
            {
                sendToLynxButton.Enabled = detectionsListBox.SelectedIndex >= 0;
                UpdateStatus("Failed to create start.", error: true);
                MessageBox.Show(
                    $"Could not parse timestamp '{timeString}' and the time-of-day start was rejected by FinishLynx.\n\nResponse: {response}",
                    "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            Debug.Print($"TOD start rejected; trying offset fallback: {offsetSeconds:F3}s");

            try
            {
                (status, response) = await lynxService.StartCreateAsync(null, offsetSeconds.ToString("F3"));
            }
            catch (Exception ex)
            {
                sendToLynxButton.Enabled = detectionsListBox.SelectedIndex >= 0;
                UpdateStatus("Failed to connect to FinishLynx.", error: true);
                MessageBox.Show($"Failed to connect to FinishLynx:\n{ex.Message}",
                    "Connection Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            sendToLynxButton.Enabled = detectionsListBox.SelectedIndex >= 0;

            if (status == FinishLynxReply.Ok)
            {
                if (hasCalibration)
                {
                    UpdateStatus($"Start created in FinishLynx (offset): {adjustedTimeString} (calibrated from {timeString})");
                }
                else
                {
                    UpdateStatus($"Start created in FinishLynx (offset): {adjustedTimeString}");
                }
            }
            else
            {
                UpdateStatus("Failed to create start.", error: true);
                MessageBox.Show($"FinishLynx rejected the start command.\n\nResponse: {response}",
                    "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private bool TryGetSelectedDetection(out DetectionEntry detection)
        {
            var index = detectionsListBox.SelectedIndex;
            if (index >= 0 && index < _lastDetections.Count)
            {
                detection = _lastDetections[index];
                return true;
            }

            detection = default!;
            return false;
        }

        private double GetAverageCalibrationOffsetMilliseconds()
        {
            if (_calibrationOffsetsMsByClientTimestamp.Count == 0)
                return 0;

            return _calibrationOffsetsMsByClientTimestamp.Values.Average();
        }

        private void UpdateCalibrationSummaryLabel()
        {
            if (_calibrationOffsetsMsByClientTimestamp.Count == 0)
            {
                calibrationSummaryLabel.Text = "Calibration offset: none";
                return;
            }

            var avgSeconds = GetAverageCalibrationOffsetMilliseconds() / 1000.0;
            calibrationSummaryLabel.Text = $"Calibration offset: {avgSeconds:+0.000;-0.000;0.000}s ({_calibrationOffsetsMsByClientTimestamp.Count} samples)";
        }

        private void UpdateCalibrationControlsState()
        {
            var hasSelection = TryGetSelectedDetection(out _);
            var hasValidMatchingTime = IsValidTimeOfDay(matchingTimeTextBox.Text.Trim());

            calibrateButton.Enabled = hasSelection && hasValidMatchingTime;
            clearCalibrationsButton.Enabled = _calibrationOffsetsMsByClientTimestamp.Count > 0;
        }

        private static bool IsValidTimeOfDay(string value)
        {
            return TryParseTimeOfDay(value, out _);
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

        public static double ComputeCalibrationOffsetMilliseconds(string detectionTimeString, string finishLynxTimeString)
        {
            if (!TryParseTimeOfDay(detectionTimeString, out var detection))
                throw new FormatException($"Cannot parse time string: {detectionTimeString}");

            if (!TryParseTimeOfDay(finishLynxTimeString, out var finishLynx))
                throw new FormatException($"Cannot parse time string: {finishLynxTimeString}");

            var difference = finishLynx - detection;

            if (difference > TimeSpan.FromHours(12))
                difference -= TimeSpan.FromDays(1);
            else if (difference < TimeSpan.FromHours(-12))
                difference += TimeSpan.FromDays(1);

            return difference.TotalMilliseconds;
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
                timeOfDay.Hours, timeOfDay.Minutes, timeOfDay.Seconds);

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
            }
            catch { }
            base.OnFormClosing(e);
        }
    }
}

