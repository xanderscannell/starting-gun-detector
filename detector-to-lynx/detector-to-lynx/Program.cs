using Microsoft.Extensions.Logging;
using Serilog;

namespace detector_to_lynx
{
    internal static class Program
    {
        public static ILoggerFactory LoggerFactory { get; private set; } = null!;

        /// <summary>
        ///  The main entry point for the application.
        /// </summary>
        [STAThread]
        static void Main()
        {
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