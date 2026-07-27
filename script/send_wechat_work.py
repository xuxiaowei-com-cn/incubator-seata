#!/usr/bin/env python3
"""
Send a diff file to a WeChat Work webhook as a markdown message.

Usage:
    python3 send_wechat_work.py <webhook_url> <diff_file> \
        <module_name> <platform> [--repository repo] [--ref-name ref] \
        [--run-id run_id] [--server-url url]
"""

import argparse
import json
import os
import sys


def build_payload(
    diff_content: str,
    module_name: str,
    platform: str,
    repository: str = "",
    ref_name: str = "",
    run_id: str = "",
    server_url: str = "https://github.com",
) -> str:
    """Build the WeChat Work markdown message JSON payload."""

    # WeChat Work markdown message max length is 4096 chars.
    # Reserve room for headers, fences, and link markup (~500 chars).
    MAX_DIFF_LEN = 3500
    if len(diff_content) > MAX_DIFF_LEN:
        diff_content = (
            diff_content[:MAX_DIFF_LEN]
            + "\n\n... (truncated, see artifact for full diff)"
        )

    run_link = ""
    if repository and run_id:
        run_link = (
            f"[{run_id}]({server_url}/{repository}/actions/runs/{run_id})"
        )
    elif run_id:
        run_link = run_id

    content_lines = [
        f"## {module_name} native-image config diff",
        f"> Platform: **{platform}**",
    ]
    if repository:
        content_lines.append(f"> Repository: {repository}")
    if ref_name:
        content_lines.append(f"> Branch: {ref_name}")
    if run_link:
        content_lines.append(f"> Run: {run_link}")

    content_lines.append("")
    content_lines.append("```")
    content_lines.append(diff_content)
    content_lines.append("```")

    payload = {
        "msgtype": "markdown",
        "markdown": {
            "content": "\n".join(content_lines),
        },
    }

    return json.dumps(payload, ensure_ascii=False)


def main():
    parser = argparse.ArgumentParser(
        description="Send a diff file to a WeChat Work webhook."
    )
    parser.add_argument("webhook_url", help="WeChat Work webhook URL")
    parser.add_argument("diff_file", help="Path to the diff file")
    parser.add_argument("module_name", help="Module name for the message title")
    parser.add_argument("platform", help="OS platform label")
    parser.add_argument(
        "--repository",
        default=os.environ.get("GITHUB_REPOSITORY", ""),
        help="GitHub repository (owner/name)",
    )
    parser.add_argument(
        "--ref-name",
        default=os.environ.get("GITHUB_REF_NAME", ""),
        help="Branch or tag name",
    )
    parser.add_argument(
        "--run-id",
        default=os.environ.get("GITHUB_RUN_ID", ""),
        help="GitHub Actions run ID",
    )
    parser.add_argument(
        "--server-url",
        default=os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
        help="GitHub server URL",
    )

    args = parser.parse_args()

    # Read diff file
    try:
        with open(args.diff_file, "r", encoding="utf-8") as f:
            diff_content = f.read()
    except FileNotFoundError:
        print(f"ERROR: Diff file not found: {args.diff_file}", file=sys.stderr)
        sys.exit(1)

    if not diff_content.strip():
        print("Diff content is empty, skipping notification.")
        sys.exit(0)

    # Build payload
    payload = build_payload(
        diff_content=diff_content,
        module_name=args.module_name,
        platform=args.platform,
        repository=args.repository,
        ref_name=args.ref_name,
        run_id=args.run_id,
        server_url=args.server_url,
    )

    # Send via curl
    import subprocess

    result = subprocess.run(
        [
            "curl",
            "-sS",
            "-X", "POST",
            "-H", "Content-Type: application/json",
            "-d", payload,
            args.webhook_url,
        ],
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"ERROR: curl failed: {result.stderr}", file=sys.stderr)
        sys.exit(1)

    print(f"Notification sent to WeChat Work. Response: {result.stdout.strip()}")


if __name__ == "__main__":
    main()
