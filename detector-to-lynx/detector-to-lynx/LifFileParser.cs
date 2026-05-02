using System.Globalization;

namespace detector_to_lynx
{
    /// <summary>
    /// Parses FinishLynx result files (.lif) to extract event start times.
    ///
    /// .lif format: first line is the event header row, comma-delimited.
    /// Field index 10 (0-based) contains the start time as HH:mm:ss.ffff
    /// (4 decimal places, i.e. 1/10000 second resolution).
    /// </summary>
    public static class LifFileParser
    {
        // Field index of the start time in the header row (0-based).
        private const int StartTimeFieldIndex = 10;

        /// <summary>
        /// Reads the first line of <paramref name="filePath"/> and returns its start time,
        /// or <c>null</c> if the file is empty, the field is missing, or the time cannot be parsed.
        /// </summary>
        public static TimeSpan? ParseStartTime(string filePath)
        {
            string firstLine;
            try
            {
                using var reader = new StreamReader(filePath);
                firstLine = reader.ReadLine() ?? string.Empty;
            }
            catch
            {
                return null;
            }

            return ParseStartTimeFromContent(firstLine);
        }

        /// <summary>
        /// Parses the start time from a single .lif header line.
        /// Accepts 3 or 4 fractional-second digits; result is truncated to milliseconds.
        /// Returns <c>null</c> on any parse failure.
        /// </summary>
        public static TimeSpan? ParseStartTimeFromContent(string line)
        {
            if (string.IsNullOrWhiteSpace(line))
                return null;

            var fields = line.Split(',');
            if (fields.Length <= StartTimeFieldIndex)
                return null;

            var raw = fields[StartTimeFieldIndex].Trim();
            if (string.IsNullOrEmpty(raw))
                return null;

            // Normalise to 3 decimal places (milliseconds).
            // .lif files use 4 decimal places (1/10000 s); trim the last digit.
            var dotIndex = raw.LastIndexOf('.');
            if (dotIndex >= 0 && raw.Length - dotIndex - 1 > 3)
                raw = raw[..(dotIndex + 4)]; // keep dot + 3 digits

            if (TimeSpan.TryParseExact(
                    raw,
                    [@"hh\:mm\:ss\.fff", @"h\:mm\:ss\.fff", @"hh\:mm\:ss", @"h\:mm\:ss"],
                    CultureInfo.InvariantCulture,
                    out var result))
                return result;

            // Fallback for any other valid TimeSpan format.
            if (TimeSpan.TryParse(raw, CultureInfo.InvariantCulture, out result))
                return result;

            return null;
        }
    }
}
