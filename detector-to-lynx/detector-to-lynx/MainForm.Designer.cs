namespace detector_to_lynx
{
    partial class MainForm
    {
        private System.ComponentModel.IContainer components = null;

        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
                components.Dispose();
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        private void InitializeComponent()
        {
            components = new System.ComponentModel.Container();

            // ── Session group ────────────────────────────────────────────────
            sessionGroupBox = new GroupBox();
            sessionCodeLabel = new Label();
            sessionCodeTextBox = new TextBox();
            joinButton = new Button();
            sessionStatusLabel = new Label();

            // ── Detections group ─────────────────────────────────────────────
            detectionsGroupBox = new GroupBox();
            detectionsListBox = new ListBox();
            selectedTimeLabel = new Label();
            matchingTimeLabel = new Label();
            matchingTimeTextBox = new TextBox();
            calibrateButton = new Button();
            clearCalibrationsButton = new Button();
            calibrationSummaryLabel = new Label();
            sendToLynxButton = new Button();

            // ── Settings group ───────────────────────────────────────────────
            settingsGroupBox = new GroupBox();
            lynxPortLabel = new Label();
            lynxPortTextBox = new TextBox();

            // ── Status strip ─────────────────────────────────────────────────
            statusStrip = new StatusStrip();
            toolStripStatusLabel = new ToolStripStatusLabel();

            // ════════════════════════════════════════════════════════════════
            // sessionGroupBox
            // ════════════════════════════════════════════════════════════════
            sessionGroupBox.SuspendLayout();
            sessionGroupBox.Text = "Session";
            sessionGroupBox.Location = new Point(12, 12);
            sessionGroupBox.Size = new Size(560, 70);
            sessionGroupBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            sessionCodeLabel.Text = "Session Code:";
            sessionCodeLabel.Location = new Point(10, 26);
            sessionCodeLabel.Size = new Size(90, 23);
            sessionCodeLabel.TextAlign = ContentAlignment.MiddleLeft;

            sessionCodeTextBox.Location = new Point(106, 24);
            sessionCodeTextBox.Size = new Size(60, 23);
            sessionCodeTextBox.MaxLength = 4;
            sessionCodeTextBox.CharacterCasing = CharacterCasing.Upper;
            sessionCodeTextBox.Font = new Font("Consolas", 11f, FontStyle.Bold);

            joinButton.Text = "Join Session";
            joinButton.Location = new Point(178, 23);
            joinButton.Size = new Size(110, 27);
            joinButton.Click += joinButton_Click;

            sessionStatusLabel.Text = "Not connected";
            sessionStatusLabel.Location = new Point(300, 26);
            sessionStatusLabel.Size = new Size(240, 23);
            sessionStatusLabel.TextAlign = ContentAlignment.MiddleLeft;
            sessionStatusLabel.ForeColor = SystemColors.GrayText;

            sessionGroupBox.Controls.Add(sessionCodeLabel);
            sessionGroupBox.Controls.Add(sessionCodeTextBox);
            sessionGroupBox.Controls.Add(joinButton);
            sessionGroupBox.Controls.Add(sessionStatusLabel);
            sessionGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // detectionsGroupBox
            // ════════════════════════════════════════════════════════════════
            detectionsGroupBox.SuspendLayout();
            detectionsGroupBox.Text = "Detections";
            detectionsGroupBox.Location = new Point(12, 90);
            detectionsGroupBox.Size = new Size(560, 310);
            detectionsGroupBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;

            detectionsListBox.Location = new Point(10, 22);
            detectionsListBox.Size = new Size(538, 154);
            detectionsListBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            detectionsListBox.Font = new Font("Consolas", 10f);
            detectionsListBox.SelectedIndexChanged += detectionsListBox_SelectedIndexChanged;

            selectedTimeLabel.Text = "Selected: —";
            selectedTimeLabel.Location = new Point(10, 184);
            selectedTimeLabel.Size = new Size(538, 23);
            selectedTimeLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            selectedTimeLabel.TextAlign = ContentAlignment.MiddleLeft;

            matchingTimeLabel.Text = "FinishLynx Matching Time:";
            matchingTimeLabel.Location = new Point(10, 213);
            matchingTimeLabel.Size = new Size(160, 23);
            matchingTimeLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            matchingTimeLabel.TextAlign = ContentAlignment.MiddleLeft;

            matchingTimeTextBox.Location = new Point(172, 213);
            matchingTimeTextBox.Size = new Size(110, 23);
            matchingTimeTextBox.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            matchingTimeTextBox.Font = new Font("Consolas", 10f);
            matchingTimeTextBox.TextChanged += matchingTimeTextBox_TextChanged;

            calibrateButton.Text = "Calibrate";
            calibrateButton.Location = new Point(292, 211);
            calibrateButton.Size = new Size(85, 27);
            calibrateButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            calibrateButton.Enabled = false;
            calibrateButton.Click += calibrateButton_Click;

            clearCalibrationsButton.Text = "Clear";
            clearCalibrationsButton.Location = new Point(385, 211);
            clearCalibrationsButton.Size = new Size(65, 27);
            clearCalibrationsButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            clearCalibrationsButton.Enabled = false;
            clearCalibrationsButton.Click += clearCalibrationsButton_Click;

            calibrationSummaryLabel.Text = "Calibration offset: none";
            calibrationSummaryLabel.Location = new Point(10, 242);
            calibrationSummaryLabel.Size = new Size(538, 23);
            calibrationSummaryLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            calibrationSummaryLabel.TextAlign = ContentAlignment.MiddleLeft;

            sendToLynxButton.Text = "Send to FinishLynx";
            sendToLynxButton.Location = new Point(408, 270);
            sendToLynxButton.Size = new Size(140, 28);
            sendToLynxButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            sendToLynxButton.Enabled = false;
            sendToLynxButton.Click += sendToLynxButton_Click;

            detectionsGroupBox.Controls.Add(detectionsListBox);
            detectionsGroupBox.Controls.Add(selectedTimeLabel);
            detectionsGroupBox.Controls.Add(matchingTimeLabel);
            detectionsGroupBox.Controls.Add(matchingTimeTextBox);
            detectionsGroupBox.Controls.Add(calibrateButton);
            detectionsGroupBox.Controls.Add(clearCalibrationsButton);
            detectionsGroupBox.Controls.Add(calibrationSummaryLabel);
            detectionsGroupBox.Controls.Add(sendToLynxButton);
            detectionsGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // settingsGroupBox
            // ════════════════════════════════════════════════════════════════
            settingsGroupBox.SuspendLayout();
            settingsGroupBox.Text = "Settings";
            settingsGroupBox.Location = new Point(12, 410);
            settingsGroupBox.Size = new Size(560, 55);
            settingsGroupBox.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

            lynxPortLabel.Text = "FinishLynx Remote Port:";
            lynxPortLabel.Location = new Point(10, 20);
            lynxPortLabel.Size = new Size(150, 23);
            lynxPortLabel.TextAlign = ContentAlignment.MiddleLeft;

            lynxPortTextBox.Location = new Point(162, 18);
            lynxPortTextBox.Size = new Size(70, 23);
            lynxPortTextBox.TextChanged += lynxPortTextBox_TextChanged;

            settingsGroupBox.Controls.Add(lynxPortLabel);
            settingsGroupBox.Controls.Add(lynxPortTextBox);
            settingsGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // statusStrip
            // ════════════════════════════════════════════════════════════════
            toolStripStatusLabel.Text = "Ready";
            statusStrip.Items.Add(toolStripStatusLabel);

            // ════════════════════════════════════════════════════════════════
            // MainForm
            // ════════════════════════════════════════════════════════════════
            AutoScaleDimensions = new SizeF(7F, 15F);
            AutoScaleMode = AutoScaleMode.Font;
            ClientSize = new Size(584, 495);
            MinimumSize = new Size(500, 480);
            Text = "Detector to Lynx";
            Controls.Add(sessionGroupBox);
            Controls.Add(detectionsGroupBox);
            Controls.Add(settingsGroupBox);
            Controls.Add(statusStrip);
        }

        #endregion

        private GroupBox sessionGroupBox;
        private Label sessionCodeLabel;
        private TextBox sessionCodeTextBox;
        private Button joinButton;
        private Label sessionStatusLabel;

        private GroupBox detectionsGroupBox;
        private ListBox detectionsListBox;
        private Label selectedTimeLabel;
        private Label matchingTimeLabel;
        private TextBox matchingTimeTextBox;
        private Button calibrateButton;
        private Button clearCalibrationsButton;
        private Label calibrationSummaryLabel;
        private Button sendToLynxButton;

        private GroupBox settingsGroupBox;
        private Label lynxPortLabel;
        private TextBox lynxPortTextBox;

        private StatusStrip statusStrip;
        private ToolStripStatusLabel toolStripStatusLabel;
    }
}
