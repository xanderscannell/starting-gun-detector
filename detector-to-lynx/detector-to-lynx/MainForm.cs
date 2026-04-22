using System.Diagnostics;
using System.Reflection;

namespace detector_to_lynx
{
    public partial class MainForm : Form
    {
        private readonly FirestoreService _firestoreService = new();
        private System.Windows.Forms.Timer? _pollTimer;
        private string? _sessionCode;
        private List<DetectionEntry> _lastDetections = [];

        public MainForm()
        {
            InitializeComponent();
            LoadSavedSettings();
            AppendVersionToTitle();
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
            detectionsListBox.Items.Clear();
            joinButton.Text = "Join Session";
            sessionCodeTextBox.Enabled = true;
            sessionStatusLabel.Text = "Not connected";
            sessionStatusLabel.ForeColor = SystemColors.GrayText;
            selectedTimeLabel.Text = "Selected: —";
            sendToLynxButton.Enabled = false;
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
        }

        // ─── Send to FinishLynx ───────────────────────────────────────────────

        private void sendToLynxButton_Click(object sender, EventArgs e)
        {
            var selected = detectionsListBox.SelectedItem as string;
            if (selected == null) return;

            var timestamp = selected.Split(" — ")[0].Trim();
            _ = CreateStartAsync(timestamp);
        }

        private async Task CreateStartAsync(string timeString)
        {
            var lynxService = new FinishLynxRemoteService(SavedSettingsManager.LynxRemotePort);
            FinishLynxReply status;
            string response;

            sendToLynxButton.Enabled = false;
            UpdateStatus("Sending to FinishLynx...");

            try
            {
                (status, response) = await lynxService.StartCreateAsync(timeString);
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
                UpdateStatus($"Start created in FinishLynx: {timeString}");
                return;
            }

            // TOD start failed — try offset fallback
            double offsetSeconds;
            try
            {
                offsetSeconds = ComputeOffset(timeString, DateTime.Now);
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
                UpdateStatus($"Start created in FinishLynx (offset): {timeString}");
            }
            else
            {
                UpdateStatus("Failed to create start.", error: true);
                MessageBox.Show($"FinishLynx rejected the start command.\n\nResponse: {response}",
                    "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        /// <summary>
        /// Computes the elapsed seconds between a HH:mm:ss.fff timestamp string and <paramref name="now"/>.
        /// Returns 0 if the timestamp is in the future. Throws <see cref="FormatException"/> if unparseable.
        /// </summary>
        public static double ComputeOffset(string timeString, DateTime now)
        {
            if (!TimeSpan.TryParse(timeString, out var timeOfDay))
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

