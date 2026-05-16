using System.Net;
using Microsoft.Extensions.Logging;

namespace detector_to_lynx
{
    public enum DetectionRefreshStatus
    {
        Updated,
        NoChange,
        BackoffActive,
        RateLimited,
        Busy,
        Failed,
    }

    public sealed record DetectionRefreshResult(
        DetectionRefreshStatus Status,
        List<DetectionEntry>? Detections = null,
        TimeSpan? Delay = null,
        string? Message = null
    );

    public interface IDetectionRefreshCoordinator
    {
        Task<DetectionRefreshResult> RefreshAsync(
            string sessionCode,
            bool manualRequest,
            CancellationToken cancellationToken = default
        );

        void Reset();
    }

    public sealed class DetectionRefreshCoordinator : IDetectionRefreshCoordinator
    {
        private readonly IFirestoreService _firestoreService;
        private readonly ILogger<DetectionRefreshCoordinator> _logger;

        private readonly object _lock = new();
        private bool _refreshInProgress;
        private int _consecutiveTooManyRequests;
        private TimeSpan _currentBackoff = TimeSpan.Zero;
        private DateTimeOffset _nextAllowedRefreshUtc = DateTimeOffset.MinValue;
        private long[] _lastClientTimestamps = [];

        private const int InitialBackoffMs = 60_000;
        private const int MaxBackoffMs = 15 * 60_000;

        public DetectionRefreshCoordinator(
            IFirestoreService firestoreService,
            ILoggerFactory? loggerFactory = null
        )
        {
            _firestoreService = firestoreService;
            _logger = (loggerFactory
                    ?? Program.LoggerFactory
                    ?? Microsoft.Extensions.Logging.Abstractions.NullLoggerFactory.Instance)
                .CreateLogger<DetectionRefreshCoordinator>();
        }

        public void Reset()
        {
            lock (_lock)
            {
                _refreshInProgress = false;
                _consecutiveTooManyRequests = 0;
                _currentBackoff = TimeSpan.Zero;
                _nextAllowedRefreshUtc = DateTimeOffset.MinValue;
                _lastClientTimestamps = [];
            }
        }

        public async Task<DetectionRefreshResult> RefreshAsync(
            string sessionCode,
            bool manualRequest,
            CancellationToken cancellationToken = default
        )
        {
            lock (_lock)
            {
                if (_refreshInProgress)
                {
                    return new DetectionRefreshResult(
                        DetectionRefreshStatus.Busy,
                        Message: manualRequest ? "Refresh already in progress." : null
                    );
                }

                var now = DateTimeOffset.UtcNow;
                if (now < _nextAllowedRefreshUtc)
                {
                    var remaining = _nextAllowedRefreshUtc - now;
                    return new DetectionRefreshResult(
                        DetectionRefreshStatus.BackoffActive,
                        Delay: remaining,
                        Message: $"Backoff active. Retry in {FormatDuration(remaining)}."
                    );
                }

                _refreshInProgress = true;
            }

            try
            {
                var detections = await _firestoreService
                    .GetDetectionsAsync(sessionCode, cancellationToken)
                    .ConfigureAwait(false);

                lock (_lock)
                {
                    _consecutiveTooManyRequests = 0;
                    _currentBackoff = TimeSpan.Zero;
                    _nextAllowedRefreshUtc = DateTimeOffset.MinValue;

                    var newTimestamps = detections.Select(d => d.ClientTimestamp).ToArray();
                    if (newTimestamps.SequenceEqual(_lastClientTimestamps))
                    {
                        return new DetectionRefreshResult(DetectionRefreshStatus.NoChange);
                    }

                    _lastClientTimestamps = newTimestamps;
                    return new DetectionRefreshResult(DetectionRefreshStatus.Updated, Detections: detections);
                }
            }
            catch (HttpRequestException ex) when (ex.StatusCode == HttpStatusCode.TooManyRequests)
            {
                TimeSpan delay;
                lock (_lock)
                {
                    _consecutiveTooManyRequests++;
                    var exponentialMs = Math.Min(
                        MaxBackoffMs,
                        InitialBackoffMs * (int)Math.Pow(2, _consecutiveTooManyRequests - 1)
                    );
                    var jitterMs = Random.Shared.Next(0, 5000);
                    _currentBackoff = TimeSpan.FromMilliseconds(exponentialMs + jitterMs);
                    _nextAllowedRefreshUtc = DateTimeOffset.UtcNow + _currentBackoff;
                    delay = _currentBackoff;
                }

                _logger.LogWarning(ex, "Refresh returned HTTP 429 for session {SessionCode}", sessionCode);

                return new DetectionRefreshResult(
                    DetectionRefreshStatus.RateLimited,
                    Delay: delay,
                    Message: $"Firestore rate-limited. Backing off for {FormatDuration(delay)}."
                );
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Refresh failed for session {SessionCode}", sessionCode);
                return new DetectionRefreshResult(
                    DetectionRefreshStatus.Failed,
                    Message: "Refresh failed. Will retry on next interval."
                );
            }
            finally
            {
                lock (_lock)
                {
                    _refreshInProgress = false;
                }
            }
        }

        private static string FormatDuration(TimeSpan span)
        {
            if (span < TimeSpan.Zero)
                span = TimeSpan.Zero;

            if (span.TotalHours >= 1)
                return $"{(int)span.TotalHours}h {span.Minutes}m";

            if (span.TotalMinutes >= 1)
                return $"{span.Minutes}m {span.Seconds}s";

            return $"{span.Seconds}s";
        }
    }
}
