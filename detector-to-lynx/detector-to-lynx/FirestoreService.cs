using System.Net.Http;
using System.Text.Json;

namespace detector_to_lynx
{
    public record DetectionEntry(string Timestamp, string DisplayName, long ClientTimestamp);

    public class FirestoreService
    {
        public const string ProjectId = "starting-gun-detector";
        public const string ApiKey = "AIzaSyDeitBok4LXW8KsksfPxfQHm8K4ObWBEQo";

        private static readonly string BaseUrl =
            $"https://firestore.googleapis.com/v1/projects/{ProjectId}/databases/(default)/documents";

        private readonly HttpClient _http;

        public FirestoreService(HttpClient? httpClient = null)
        {
            _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
        }

        /// <summary>
        /// Returns true if a session document exists for the given code.
        /// </summary>
        public async Task<bool> ValidateSessionAsync(
            string sessionCode,
            CancellationToken cancellationToken = default
        )
        {
            var url = $"{BaseUrl}/sessions/{Uri.EscapeDataString(sessionCode.ToUpperInvariant())}?key={ApiKey}";
            using var response = await _http.GetAsync(url, cancellationToken).ConfigureAwait(false);
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
            var url = $"{BaseUrl}/sessions/{Uri.EscapeDataString(sessionCode.ToUpperInvariant())}/detections?key={ApiKey}";
            using var response = await _http.GetAsync(url, cancellationToken).ConfigureAwait(false);
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
