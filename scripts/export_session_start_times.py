from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_PROJECT_ID = os.environ.get("STARTING_GUN_FIRESTORE_PROJECT_ID", "starting-gun-detector")
API_KEY_ENV_VAR = "STARTING_GUN_FIRESTORE_API_KEY"


@dataclass
class StartTimeEntry:
    document_name: str
    display_name: str
    timestamp_text: str
    client_timestamp: int
    server_timestamp_millis: int | None
    created_at_iso: str | None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Export all start times for a Firestore session into a CSV file."
    )
    parser.add_argument("session_code", help="4-character session code, for example ABCD")
    parser.add_argument("output_file", help="Path to the CSV file to write")
    parser.add_argument(
        "--project-id",
        default=DEFAULT_PROJECT_ID,
        help=f"Firebase project id (default: {DEFAULT_PROJECT_ID})",
    )
    parser.add_argument(
        "--api-key",
        default=None,
        help=f"Firestore REST API key. Defaults to the {API_KEY_ENV_VAR} environment variable.",
    )
    return parser


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Firestore request failed with HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Firestore request failed: {exc.reason}") from exc


def validate_session(session_code: str, project_id: str, api_key: str) -> None:
    encoded_code = urllib.parse.quote(session_code)
    url = (
        "https://firestore.googleapis.com/v1/projects/"
        f"{urllib.parse.quote(project_id)}/databases/(default)/documents/sessions/{encoded_code}?key={urllib.parse.quote(api_key)}"
    )
    fetch_json(url)


def get_string_field(fields: dict, name: str, default: str = "") -> str:
    field = fields.get(name, {})
    return field.get("stringValue", default)


def get_integer_field(fields: dict, name: str) -> int | None:
    field = fields.get(name, {})
    value = field.get("integerValue")
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def get_timestamp_field(fields: dict, name: str) -> str | None:
    field = fields.get(name, {})
    value = field.get("timestampValue")
    return value if isinstance(value, str) else None


def fetch_detections(session_code: str, project_id: str, api_key: str) -> list[StartTimeEntry]:
    encoded_project = urllib.parse.quote(project_id)
    encoded_session = urllib.parse.quote(session_code)
    base_url = (
        "https://firestore.googleapis.com/v1/projects/"
        f"{encoded_project}/databases/(default)/documents/sessions/{encoded_session}/detections"
    )

    page_token: str | None = None
    entries: list[StartTimeEntry] = []

    while True:
        params = {"pageSize": "100", "key": api_key}
        if page_token:
            params["pageToken"] = page_token
        url = f"{base_url}?{urllib.parse.urlencode(params)}"
        payload = fetch_json(url)

        for document in payload.get("documents", []):
            fields = document.get("fields", {})
            entries.append(
                StartTimeEntry(
                    document_name=document.get("name", ""),
                    display_name=get_string_field(fields, "displayName") or get_string_field(fields, "deviceId", "Unknown"),
                    timestamp_text=get_string_field(fields, "timestamp", ""),
                    client_timestamp=get_integer_field(fields, "clientTimestamp") or 0,
                    server_timestamp_millis=get_integer_field(fields, "serverCorrectedMillis"),
                    created_at_iso=get_timestamp_field(fields, "createdAt"),
                )
            )

        page_token = payload.get("nextPageToken")
        if not page_token:
            break

    entries.sort(
        key=lambda entry: (
            entry.server_timestamp_millis if entry.server_timestamp_millis is not None else entry.client_timestamp,
            entry.client_timestamp,
        )
    )
    return entries


def format_millis(millis: int | None) -> str:
    if millis is None:
        return ""
    dt = datetime.fromtimestamp(millis / 1000, tz=timezone.utc).astimezone()
    return dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def write_csv(output_path: Path, session_code: str, entries: list[StartTimeEntry]) -> None:
    with output_path.open("w", encoding="utf-8", newline="") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(
            [
                "session_code",
                "index",
                "display_name",
                "timestamp_text",
                "server_corrected_local_time",
                "server_corrected_millis",
                "client_timestamp_millis",
                "firestore_created_at",
                "document_name",
            ]
        )

        for index, entry in enumerate(entries, start=1):
            writer.writerow(
                [
                    session_code,
                    index,
                    entry.display_name,
                    entry.timestamp_text,
                    format_millis(entry.server_timestamp_millis),
                    "" if entry.server_timestamp_millis is None else entry.server_timestamp_millis,
                    entry.client_timestamp,
                    entry.created_at_iso or "",
                    entry.document_name,
                ]
            )


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    session_code = args.session_code.upper()
    output_path = Path(args.output_file)

    api_key = args.api_key or os.environ.get(API_KEY_ENV_VAR)
    if not api_key:
        print(
            f"Firestore API key not provided. Pass --api-key or set the {API_KEY_ENV_VAR} environment variable.",
            file=sys.stderr,
        )
        return 1

    try:
        validate_session(session_code, args.project_id, api_key)
        entries = fetch_detections(session_code, args.project_id, api_key)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        write_csv(output_path, session_code, entries)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"Wrote {len(entries)} start times to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())