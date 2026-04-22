using System.Text.Json;

namespace detector_to_lynx
{
    internal static class SavedSettingsManager
    {
        private static readonly string SettingsPath = Path.Combine(
            Application.UserAppDataPath,
            "settings.json"
        );

        private static Settings? _cached;

        private static Settings Current => _cached ??= Load();

        public static string SessionCode
        {
            get => Current.SessionCode;
            set { Current.SessionCode = value; Save(); }
        }

        public static int LynxRemotePort
        {
            get => Current.LynxRemotePort;
            set { Current.LynxRemotePort = value; Save(); }
        }

        public static string LynxRemotePortString
        {
            get => Current.LynxRemotePort.ToString();
            set
            {
                if (int.TryParse(value, out var port))
                {
                    Current.LynxRemotePort = port;
                    Save();
                }
            }
        }

        private static Settings Load()
        {
            try
            {
                if (File.Exists(SettingsPath))
                {
                    var json = File.ReadAllText(SettingsPath);
                    return JsonSerializer.Deserialize<Settings>(json) ?? new Settings();
                }
            }
            catch
            {
                // Ignore corrupt settings; fall back to defaults
            }
            return new Settings();
        }

        private static void Save()
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(SettingsPath)!);
                var json = JsonSerializer.Serialize(Current, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(SettingsPath, json);
            }
            catch
            {
                // Ignore save failures (read-only filesystem, etc.)
            }
        }

        private class Settings
        {
            public string SessionCode { get; set; } = string.Empty;
            public int LynxRemotePort { get; set; } = 7100;
        }
    }
}
