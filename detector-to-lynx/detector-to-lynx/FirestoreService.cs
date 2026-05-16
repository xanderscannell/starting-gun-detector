using System.Net.Http;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace detector_to_lynx
{
    public record DetectionEntry(string Timestamp, string DisplayName, long ClientTimestamp);

    public class FirestoreService : IFirestoreService
    {
        public const string ProjectId = "starting-gun-detector";

        private static readonly string BaseUrl =
            $"https://firestore.googleapis.com/v1/projects/{ProjectId}/databases/(default)/documents";

        private readonly HttpClient _http;
        private readonly ILogger<FirestoreService> _logger;
        private readonly string _apiKey;

        public FirestoreService(string apiKey, HttpClient? httpClient = null, ILoggerFactory? loggerFactory = null)
        {
            if (string.IsNullOrWhiteSpace(apiKey))
                throw new ArgumentException("API key is required", nameof(apiKey));
            _apiKey = apiKey;
            _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
            _logger = (loggerFactory ?? Program.LoggerFactory ?? Microsoft.Extensions.Logging.Abstractions.NullLoggerFactory.Instance).CreateLogger<FirestoreService>();
        }

        /// <summary>
        /// Returns true if a session document exists for the given code.
        /// </summary>
        public async Task<bool> ValidateSessionAsync(
            string sessionCode,
            CancellationToken cancellationToken = default
        )
        {
            var url = $"{BaseUrl}/sessions/{Uri.EscapeDataString(sessionCode.ToUpperInvariant())}?key={_apiKey}";
            _logger.LogDebug("Validating session {SessionCode}", sessionCode);
            using var response = await _http.GetAsync(url, cancellationToken).ConfigureAwait(false);
            _logger.LogInformation("Session validation for {SessionCode}: {StatusCode}", sessionCode, response.StatusCode);
            return response.IsSuccessStatusCode;
        }

        /// <summary>
        /// Returns all detections for the given session, sorted by clientTimestamp descending (newest first).
        /// </summary>
        public async Task<List<DetectionEntry>> GetDetectionsAsync(
            string sessionCode,
            CancellationToken cancellationToken = default
        )
        {
            var url = $"{BaseUrl}/sessions/{Uri.EscapeDataString(sessionCode.ToUpperInvariant())}/detections?key={_apiKey}";
            using var response = await _http.GetAsync(url, cancellationToken).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
            {
                _logger.LogWarning("GetDetections HTTP {StatusCode} for session {SessionCode}", response.StatusCode, sessionCode);
            }
            response.EnsureSuccessStatusCode();

            var json = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            return ParseDetections(json);
        }

        public static List<DetectionEntry> ParseDetections(string json)
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            if (!root.TryGetProperty("documents", out var documents))
                return [];

            var results = new List<DetectionEntry>();

            foreach (var document in documents.EnumerateArray())
            {
                if (!document.TryGetProperty("fields", out var fields))
                    continue;

                var timestamp = GetStringField(fields, "timestamp");
                var displayName = GetStringField(fields, "displayName");
                var clientTimestamp = GetIntegerField(fields, "clientTimestamp");

                if (timestamp is null || displayName is null)
                    continue;

                results.Add(new DetectionEntry(timestamp, displayName, clientTimestamp));
            }

            results.Sort((a, b) => b.ClientTimestamp.CompareTo(a.ClientTimestamp));
            return results;
        }

        private static string? GetStringField(JsonElement fields, string name)
        {
            if (fields.TryGetProperty(name, out var field) &&
                field.TryGetProperty("stringValue", out var value))
            {
                return value.GetString();
            }
            return null;
        }

        private static long GetIntegerField(JsonElement fields, string name)
        {
            if (fields.TryGetProperty(name, out var field) &&
                field.TryGetProperty("integerValue", out var value))
            {
                if (value.ValueKind == JsonValueKind.String &&
                    long.TryParse(value.GetString(), out var parsed))
                    return parsed;

                if (value.ValueKind == JsonValueKind.Number)
                    return value.GetInt64();
            }
            return 0;
        }
    }
}
