using System.Net;
using System.Net.Http;
using Moq;

namespace detector_to_lynx.Tests
{
    public class FirestoreServiceTests
    {
        // ─── Helpers ─────────────────────────────────────────────────────────

        private static HttpClient MakeClient(string responseBody, HttpStatusCode statusCode = HttpStatusCode.OK)
        {
            var handler = new MockHttpMessageHandler(responseBody, statusCode);
            return new HttpClient(handler);
        }

        // ─── ParseDetections ─────────────────────────────────────────────────

        [Fact]
        public void ParseDetections_ValidJson_ReturnsCorrectEntries()
        {
            var json = """
            {
              "documents": [
                {
                  "fields": {
                    "timestamp":             { "stringValue": "14:23:45.123" },
                    "displayName":           { "stringValue": "Mike's Phone" },
                    "clientTimestamp":       { "integerValue": "1000" }
                  }
                },
                {
                  "fields": {
                    "timestamp":             { "stringValue": "14:23:44.000" },
                    "displayName":           { "stringValue": "Coach iPad" },
                    "clientTimestamp":       { "integerValue": "900" }
                  }
                }
              ]
            }
            """;

            var results = FirestoreService.ParseDetections(json);

            Assert.Equal(2, results.Count);
            Assert.Equal("14:23:45.123", results[0].Timestamp);
            Assert.Equal("Mike's Phone", results[0].DisplayName);
            Assert.Equal(1000L, results[0].ClientTimestamp);
        }

        [Fact]
        public void ParseDetections_EmptyDocuments_ReturnsEmptyList()
        {
            var json = """{ "documents": [] }""";
            var results = FirestoreService.ParseDetections(json);
            Assert.Empty(results);
        }

        [Fact]
        public void ParseDetections_NoDocumentsProperty_ReturnsEmptyList()
        {
            // Firestore returns this when there are no docs in the collection
            var json = """{}""";
            var results = FirestoreService.ParseDetections(json);
            Assert.Empty(results);
        }

        [Fact]
        public void ParseDetections_SortsByClientTimestampDescending()
        {
            var json = """
            {
              "documents": [
                { "fields": { "timestamp": { "stringValue": "10:00:01.000" }, "displayName": { "stringValue": "A" }, "clientTimestamp": { "integerValue": "100" } } },
                { "fields": { "timestamp": { "stringValue": "10:00:03.000" }, "displayName": { "stringValue": "C" }, "clientTimestamp": { "integerValue": "300" } } },
                { "fields": { "timestamp": { "stringValue": "10:00:02.000" }, "displayName": { "stringValue": "B" }, "clientTimestamp": { "integerValue": "200" } } }
              ]
            }
            """;

            var results = FirestoreService.ParseDetections(json);

            Assert.Equal(3, results.Count);
            Assert.Equal(300L, results[0].ClientTimestamp); // newest first
            Assert.Equal(200L, results[1].ClientTimestamp);
            Assert.Equal(100L, results[2].ClientTimestamp);
        }

        [Fact]
        public void ParseDetections_DocumentMissingRequiredField_IsSkipped()
        {
            var json = """
            {
              "documents": [
                { "fields": { "displayName": { "stringValue": "A" }, "clientTimestamp": { "integerValue": "100" } } },
                { "fields": { "timestamp": { "stringValue": "10:00:01.000" }, "displayName": { "stringValue": "B" }, "clientTimestamp": { "integerValue": "200" } } }
              ]
            }
            """;

            var results = FirestoreService.ParseDetections(json);

            Assert.Single(results);
            Assert.Equal("B", results[0].DisplayName);
        }

        // ─── ValidateSessionAsync ─────────────────────────────────────────────

        [Fact]
        public async Task ValidateSessionAsync_Returns_True_On_200()
        {
            var svc = new FirestoreService(MakeClient("""{ "name": "projects/x/databases/(default)/documents/sessions/ABCD" }"""));
            var result = await svc.ValidateSessionAsync("ABCD");
            Assert.True(result);
        }

        [Fact]
        public async Task ValidateSessionAsync_Returns_False_On_404()
        {
            var svc = new FirestoreService(MakeClient("""{ "error": { "code": 404 } }""", HttpStatusCode.NotFound));
            var result = await svc.ValidateSessionAsync("ZZZZ");
            Assert.False(result);
        }

        // ─── GetDetectionsAsync ───────────────────────────────────────────────

        [Fact]
        public async Task GetDetectionsAsync_ParsesResponseCorrectly()
        {
            var json = """
            {
              "documents": [
                { "fields": { "timestamp": { "stringValue": "09:01:02.345" }, "displayName": { "stringValue": "Dev1" }, "clientTimestamp": { "integerValue": "999" } } }
              ]
            }
            """;
            var svc = new FirestoreService(MakeClient(json));
            var results = await svc.GetDetectionsAsync("ABCD");

            Assert.Single(results);
            Assert.Equal("09:01:02.345", results[0].Timestamp);
            Assert.Equal("Dev1", results[0].DisplayName);
        }

        [Fact]
        public async Task GetDetectionsAsync_ThrowsOnHttpError()
        {
            var svc = new FirestoreService(MakeClient("Forbidden", HttpStatusCode.Forbidden));
            await Assert.ThrowsAsync<HttpRequestException>(() => svc.GetDetectionsAsync("ABCD"));
        }
    }

    // ─── Infrastructure ───────────────────────────────────────────────────────

    internal class MockHttpMessageHandler(string responseBody, HttpStatusCode statusCode) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var response = new HttpResponseMessage(statusCode)
            {
                Content = new StringContent(responseBody)
            };
            return Task.FromResult(response);
        }
    }
}
