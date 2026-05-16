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
            refreshButton = new Button();
            sessionStatusLabel = new Label();

            // ── Lynx Results group ───────────────────────────────────────────
            lynxResultsGroupBox = new GroupBox();
            lynxResultsDirLabel = new Label();
            lynxResultsDirTextBox = new TextBox();
            browseResultsDirButton = new Button();
            lynxResultsStatusLabel = new Label();
            matchWindowLabel = new Label();
            matchWindowTextBox = new TextBox();
            matchWindowUnitsLabel = new Label();
            manualTimeLabel = new Label();
            manualTimeTextBox = new TextBox();
            addManualTimeButton = new Button();
            manualTimesListBox = new ListBox();
            removeManualTimeButton = new Button();

            // ── Calibration group ────────────────────────────────────────────
            calibrationGroupBox = new GroupBox();
            calibrationGridView = new DataGridView();
            colDetection = new DataGridViewTextBoxColumn();
            colLynxStart = new DataGridViewTextBoxColumn();
            colDiff = new DataGridViewTextBoxColumn();
            colDevice = new DataGridViewTextBoxColumn();
            selectedTimeLabel = new Label();
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
            sessionGroupBox.Size = new Size(660, 70);
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

            refreshButton.Text = "Refresh";
            refreshButton.Location = new Point(296, 23);
            refreshButton.Size = new Size(80, 27);
            refreshButton.Enabled = false;
            refreshButton.Click += refreshButton_Click;

            sessionStatusLabel.Text = "Not connected";
            sessionStatusLabel.Location = new Point(384, 26);
            sessionStatusLabel.Size = new Size(266, 23);
            sessionStatusLabel.TextAlign = ContentAlignment.MiddleLeft;
            sessionStatusLabel.ForeColor = SystemColors.GrayText;

            sessionGroupBox.Controls.Add(sessionCodeLabel);
            sessionGroupBox.Controls.Add(sessionCodeTextBox);
            sessionGroupBox.Controls.Add(joinButton);
            sessionGroupBox.Controls.Add(refreshButton);
            sessionGroupBox.Controls.Add(sessionStatusLabel);
            sessionGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // lynxResultsGroupBox
            // ════════════════════════════════════════════════════════════════
            lynxResultsGroupBox.SuspendLayout();
            lynxResultsGroupBox.Text = "Lynx Results Directory";
            lynxResultsGroupBox.Location = new Point(12, 90);
            lynxResultsGroupBox.Size = new Size(660, 168);
            lynxResultsGroupBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            lynxResultsDirLabel.Text = "Directory:";
            lynxResultsDirLabel.Location = new Point(10, 24);
            lynxResultsDirLabel.Size = new Size(65, 23);
            lynxResultsDirLabel.TextAlign = ContentAlignment.MiddleLeft;

            lynxResultsDirTextBox.Location = new Point(78, 22);
            lynxResultsDirTextBox.Size = new Size(460, 23);
            lynxResultsDirTextBox.ReadOnly = true;
            lynxResultsDirTextBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            lynxResultsDirTextBox.Font = new Font("Consolas", 9f);

            browseResultsDirButton.Text = "Browse...";
            browseResultsDirButton.Location = new Point(548, 21);
            browseResultsDirButton.Size = new Size(100, 27);
            browseResultsDirButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            browseResultsDirButton.Click += browseResultsDirButton_Click;

            lynxResultsStatusLabel.Text = "Not watching";
            lynxResultsStatusLabel.Location = new Point(10, 50);
            lynxResultsStatusLabel.Size = new Size(400, 20);
            lynxResultsStatusLabel.ForeColor = SystemColors.GrayText;
            lynxResultsStatusLabel.Font = new Font(Font.FontFamily, 8.5f);

            matchWindowLabel.Text = "Match window:";
            matchWindowLabel.Location = new Point(420, 50);
            matchWindowLabel.Size = new Size(88, 20);
            matchWindowLabel.TextAlign = ContentAlignment.MiddleLeft;
            matchWindowLabel.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            matchWindowTextBox.Location = new Point(511, 48);
            matchWindowTextBox.Size = new Size(50, 23);
            matchWindowTextBox.TextAlign = HorizontalAlignment.Right;
            matchWindowTextBox.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            matchWindowTextBox.TextChanged += matchWindowTextBox_TextChanged;

            matchWindowUnitsLabel.Text = "s";
            matchWindowUnitsLabel.Location = new Point(564, 50);
            matchWindowUnitsLabel.Size = new Size(20, 20);
            matchWindowUnitsLabel.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            matchWindowUnitsLabel.TextAlign = ContentAlignment.MiddleLeft;

            lynxResultsGroupBox.Controls.Add(lynxResultsDirLabel);
            lynxResultsGroupBox.Controls.Add(lynxResultsDirTextBox);
            lynxResultsGroupBox.Controls.Add(browseResultsDirButton);
            lynxResultsGroupBox.Controls.Add(lynxResultsStatusLabel);
            lynxResultsGroupBox.Controls.Add(matchWindowLabel);
            lynxResultsGroupBox.Controls.Add(matchWindowTextBox);
            lynxResultsGroupBox.Controls.Add(matchWindowUnitsLabel);

            manualTimeLabel.Text = "Manual start:";
            manualTimeLabel.Location = new Point(10, 78);
            manualTimeLabel.Size = new Size(90, 23);
            manualTimeLabel.TextAlign = ContentAlignment.MiddleLeft;

            manualTimeTextBox.Location = new Point(103, 76);
            manualTimeTextBox.Size = new Size(115, 23);
            manualTimeTextBox.Font = new Font("Consolas", 9f);
            manualTimeTextBox.PlaceholderText = "HH:mm:ss.fff";
            manualTimeTextBox.KeyDown += manualTimeTextBox_KeyDown;

            addManualTimeButton.Text = "Add";
            addManualTimeButton.Location = new Point(224, 74);
            addManualTimeButton.Size = new Size(55, 27);
            addManualTimeButton.Click += addManualTimeButton_Click;

            manualTimesListBox.Location = new Point(103, 107);
            manualTimesListBox.Size = new Size(176, 53);
            manualTimesListBox.Font = new Font("Consolas", 9f);
            manualTimesListBox.SelectionMode = SelectionMode.One;
            manualTimesListBox.SelectedIndexChanged += manualTimesListBox_SelectedIndexChanged;

            removeManualTimeButton.Text = "Remove";
            removeManualTimeButton.Location = new Point(285, 107);
            removeManualTimeButton.Size = new Size(65, 27);
            removeManualTimeButton.Enabled = false;
            removeManualTimeButton.Click += removeManualTimeButton_Click;

            lynxResultsGroupBox.Controls.Add(manualTimeLabel);
            lynxResultsGroupBox.Controls.Add(manualTimeTextBox);
            lynxResultsGroupBox.Controls.Add(addManualTimeButton);
            lynxResultsGroupBox.Controls.Add(manualTimesListBox);
            lynxResultsGroupBox.Controls.Add(removeManualTimeButton);
            lynxResultsGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // calibrationGroupBox
            // ════════════════════════════════════════════════════════════════
            calibrationGroupBox.SuspendLayout();
            calibrationGroupBox.Text = "Calibration";
            calibrationGroupBox.Location = new Point(12, 266);
            calibrationGroupBox.Size = new Size(660, 330);
            calibrationGroupBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;

            // DataGridView
            ((System.ComponentModel.ISupportInitialize)calibrationGridView).BeginInit();
            calibrationGridView.Location = new Point(10, 22);
            calibrationGridView.Size = new Size(638, 230);
            calibrationGridView.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            calibrationGridView.Font = new Font("Consolas", 10f);
            calibrationGridView.AllowUserToAddRows = false;
            calibrationGridView.AllowUserToDeleteRows = false;
            calibrationGridView.ReadOnly = true;
            calibrationGridView.SelectionMode = DataGridViewSelectionMode.FullRowSelect;
            calibrationGridView.MultiSelect = false;
            calibrationGridView.RowHeadersVisible = false;
            calibrationGridView.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
            calibrationGridView.ColumnHeadersHeightSizeMode = DataGridViewColumnHeadersHeightSizeMode.AutoSize;
            calibrationGridView.BackgroundColor = SystemColors.Window;
            calibrationGridView.BorderStyle = BorderStyle.Fixed3D;
            calibrationGridView.SelectionChanged += calibrationGridView_SelectionChanged;

            colDetection.Name = "colDetection";
            colDetection.HeaderText = "Detection";
            colDetection.FillWeight = 28;
            colDetection.DefaultCellStyle.Font = new Font("Consolas", 10f);

            colLynxStart.Name = "colLynxStart";
            colLynxStart.HeaderText = "Lynx Start";
            colLynxStart.FillWeight = 28;
            colLynxStart.DefaultCellStyle.Font = new Font("Consolas", 10f);

            colDiff.Name = "colDiff";
            colDiff.HeaderText = "Diff (adj.)";
            colDiff.FillWeight = 20;
            colDiff.DefaultCellStyle.Alignment = DataGridViewContentAlignment.MiddleRight;
            colDiff.DefaultCellStyle.Font = new Font("Consolas", 10f);

            colDevice.Name = "colDevice";
            colDevice.HeaderText = "Device";
            colDevice.FillWeight = 24;

            calibrationGridView.Columns.Add(colDetection);
            calibrationGridView.Columns.Add(colLynxStart);
            calibrationGridView.Columns.Add(colDiff);
            calibrationGridView.Columns.Add(colDevice);
            ((System.ComponentModel.ISupportInitialize)calibrationGridView).EndInit();

            selectedTimeLabel.Text = "Selected: —";
            selectedTimeLabel.Location = new Point(10, 260);
            selectedTimeLabel.Size = new Size(450, 23);
            selectedTimeLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            selectedTimeLabel.TextAlign = ContentAlignment.MiddleLeft;

            calibrationSummaryLabel.Text = "Calibration offset: none";
            calibrationSummaryLabel.Location = new Point(10, 286);
            calibrationSummaryLabel.Size = new Size(638, 20);
            calibrationSummaryLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            calibrationSummaryLabel.TextAlign = ContentAlignment.MiddleLeft;

            sendToLynxButton.Text = "Send to FinishLynx";
            sendToLynxButton.Location = new Point(508, 256);
            sendToLynxButton.Size = new Size(140, 28);
            sendToLynxButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            sendToLynxButton.Enabled = false;
            sendToLynxButton.Click += sendToLynxButton_Click;

            calibrationGroupBox.Controls.Add(calibrationGridView);
            calibrationGroupBox.Controls.Add(selectedTimeLabel);
            calibrationGroupBox.Controls.Add(calibrationSummaryLabel);
            calibrationGroupBox.Controls.Add(sendToLynxButton);
            calibrationGroupBox.ResumeLayout(false);

            // ════════════════════════════════════════════════════════════════
            // settingsGroupBox
            // ════════════════════════════════════════════════════════════════
            settingsGroupBox.SuspendLayout();
            settingsGroupBox.Text = "Settings";
            settingsGroupBox.Location = new Point(12, 604);
            settingsGroupBox.Size = new Size(660, 55);
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
            ClientSize = new Size(684, 698);
            MinimumSize = new Size(600, 668);
            Text = "Detector to Lynx";
            Icon = new System.Drawing.Icon(Path.Combine(AppContext.BaseDirectory, "win_icon.ico"));
            Controls.Add(sessionGroupBox);
            Controls.Add(lynxResultsGroupBox);
            Controls.Add(calibrationGroupBox);
            Controls.Add(settingsGroupBox);
            Controls.Add(statusStrip);
        }

        #endregion

        private GroupBox sessionGroupBox;
        private Label sessionCodeLabel;
        private TextBox sessionCodeTextBox;
        private Button joinButton;
        private Button refreshButton;
        private Label sessionStatusLabel;

        private GroupBox lynxResultsGroupBox;
        private Label lynxResultsDirLabel;
        private TextBox lynxResultsDirTextBox;
        private Button browseResultsDirButton;
        private Label lynxResultsStatusLabel;
        private Label matchWindowLabel;
        private TextBox matchWindowTextBox;
        private Label matchWindowUnitsLabel;
        private Label manualTimeLabel;
        private TextBox manualTimeTextBox;
        private Button addManualTimeButton;
        private ListBox manualTimesListBox;
        private Button removeManualTimeButton;

        private GroupBox calibrationGroupBox;
        private DataGridView calibrationGridView;
        private DataGridViewTextBoxColumn colDetection;
        private DataGridViewTextBoxColumn colLynxStart;
        private DataGridViewTextBoxColumn colDiff;
        private DataGridViewTextBoxColumn colDevice;
        private Label selectedTimeLabel;
        private Label calibrationSummaryLabel;
        private Button sendToLynxButton;

        private GroupBox settingsGroupBox;
        private Label lynxPortLabel;
        private TextBox lynxPortTextBox;

        private StatusStrip statusStrip;
        private ToolStripStatusLabel toolStripStatusLabel;
    }
}
