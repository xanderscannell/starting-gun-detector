namespace detector_to_lynx
{
    public interface IFirestoreService
    {
        Task<bool> ValidateSessionAsync(string sessionCode, CancellationToken cancellationToken = default);

        Task<List<DetectionEntry>> GetDetectionsAsync(string sessionCode, CancellationToken cancellationToken = default);
    }
}
