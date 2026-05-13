using Microsoft.Extensions.Logging;
using Serilog;

namespace detector_to_lynx
{
    internal static class Program
    {
        public const string ApiKeyEnvVar = "STARTING_GUN_FIRESTORE_API_KEY";

        // Firebase Web API key for the starting-gun-detector project. Embedded by design:
        // Firebase Web API keys are intended to be public (https://firebase.google.com/docs/projects/api-keys)
        // and security is enforced by Firestore Security Rules + Firebase Auth, not by key
        // secrecy. The STARTING_GUN_FIRESTORE_API_KEY environment variable overrides this
        // default, which is useful when pointing at a different Firebase project for testing.
        private const string DefaultApiKey = "AIzaSyDeitBok4LXW8KsksfPxfQHm8K4ObWBEQo";

        public static ILoggerFactory LoggerFactory { get; private set; } = null!;
        public static string ApiKey { get; private set; } = null!;

        /// <summary>
        ///  The main entry point for the application.
        /// </summary>
        [STAThread]
        static void Main()
        {
            ApiKey = Environment.GetEnvironmentVariable(ApiKeyEnvVar) ?? DefaultApiKey;

            var logsDir = Path.Combine(AppContext.BaseDirectory, "logs");
            Directory.CreateDirectory(logsDir);

            var logFile = Path.Combine(
                logsDir,
                $"detector-{DateTime.Now:yyyyMMdd-HHmmss}.log"
            );

            Log.Logger = new LoggerConfiguration()
                .MinimumLevel.Debug()
                .WriteTo.File(
                    logFile,
                    outputTemplate: "{Timestamp:yyyy-MM-dd HH:mm:ss.fff} [{Level:u3}] {SourceContext}: {Message}{NewLine}{Exception}")
                .CreateLogger();

            LoggerFactory = new LoggerFactory().AddSerilog(Log.Logger);

            Log.Information("detector-to-lynx starting up. Log: {LogFile}", logFile);

            try
            {
                // To customize application configuration such as set high DPI settings or default font,
                // see https://aka.ms/applicationconfiguration.
                ApplicationConfiguration.Initialize();
                Application.Run(new MainForm());
            }
            finally
            {
                Log.Information("detector-to-lynx shutting down.");
                Log.CloseAndFlush();
                LoggerFactory.Dispose();
            }
        }
    }
}