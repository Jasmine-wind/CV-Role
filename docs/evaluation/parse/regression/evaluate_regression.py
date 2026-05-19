#!/usr/bin/env python3
"""Compare resume parse expected results with saved actual outputs.

Usage:
  python docs/evaluation/parse/regression/evaluate_regression.py
  python docs/evaluation/parse/regression/evaluate_regression.py --output reports/regression-report-latest.md

The script intentionally uses only Python standard library modules so it can run
without changing backend or frontend dependencies.
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
EXPECTED_DIR = ROOT / "expected-results"
OUTPUTS_DIR = ROOT / "outputs"
REPORTS_DIR = ROOT / "reports"
DEFAULT_REPORT = REPORTS_DIR / "regression-report-latest.md"
SAMPLE_NUMBER_PATTERN = re.compile(r"sample-(\d{3})")
MODE_PATTERN = re.compile(r"sample-\d{3}-(fast|balanced|accurate)-output", re.IGNORECASE)
PURE_INDEX_PATTERN = re.compile(r"^\s*(?:\d{1,3}|[一二三四五六七八九十百]+|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\s*[、.．)）:]?\s*$")


SECTION_FIELDS = [
    "education",
    "skills",
    "workExperiences",
    "internships",
    "projects",
    "campusExperiences",
    "summary",
    "others",
]


@dataclass
class CheckResult:
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    details: list[str] = field(default_factory=list)

    def add_pass(self) -> None:
        self.passed += 1

    def add_fail(self, detail: str) -> None:
        self.failed += 1
        self.details.append(detail)

    def add_skip(self) -> None:
        self.skipped += 1


@dataclass
class EvaluationRow:
    sample_id: str
    mode: str
    status: str
    basic_info: CheckResult
    sections: CheckResult
    pure_index_line_count: int | None
    others_count: int | None
    duplicate_warning_count: int | None
    total_parse_duration_ms: int | None
    ai_duration_ms: int | None
    cache_hit: bool | None
    fallback_occurred: bool | None
    details: list[str]


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def sample_number(path_or_id: str) -> str | None:
    match = SAMPLE_NUMBER_PATTERN.search(path_or_id)
    return match.group(1) if match else None


def actual_mode(path: Path, actual: dict[str, Any]) -> str:
    match = MODE_PATTERN.search(path.name)
    if match:
        return match.group(1).upper()
    content = actual_content(actual)
    mode = content.get("parseMode") if isinstance(content, dict) else None
    return str(mode or "UNKNOWN").upper()


def actual_content(actual: Any) -> dict[str, Any]:
    if not isinstance(actual, dict):
        return {}
    structured_json = actual.get("structuredJson")
    if isinstance(structured_json, str) and structured_json.strip():
        try:
            parsed = json.loads(structured_json)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            return {}
    structured_content = actual.get("structuredContent")
    if isinstance(structured_content, dict):
        return structured_content
    data = actual.get("data")
    if isinstance(data, dict):
        return actual_content(data)
    return actual


def text_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float, bool)):
        return str(value)
    if isinstance(value, list):
        return "\n".join(text_value(item) for item in value)
    if isinstance(value, dict):
        if "value" in value:
            return text_value(value.get("value"))
        return "\n".join(text_value(item) for item in value.values())
    return str(value)


def comparable_text(value: Any) -> str:
    return normalize_compare_text(text_value(value))


def normalize_compare_text(value: str) -> str:
    normalized = []
    for char in value:
        code_point = ord(char)
        if (0x2E80 <= code_point <= 0x2EFF
                or 0x2F00 <= code_point <= 0x2FDF
                or 0xF900 <= code_point <= 0xFAFF):
            normalized.append(unicodedata.normalize("NFKC", char))
        else:
            normalized.append(char)
    return "".join(normalized).replace("⻩", "黄").casefold()


def get_field(content: dict[str, Any], field_name: str) -> str:
    aliases = {
        "degree": ["degree", "highestEducation"],
        "school": ["school"],
        "workYears": ["workYears", "workExperienceYears"],
        "location": ["location", "currentLocation"],
    }
    for key in aliases.get(field_name, [field_name]):
        value = content.get(key)
        if text_value(value):
            return text_value(value)

    basic_info = content.get("basicInfo")
    if isinstance(basic_info, dict):
        for key in aliases.get(field_name, [field_name]):
            value = basic_info.get(key)
            if text_value(value):
                return text_value(value)

    debug_info = content.get("basicInfoDebug")
    if isinstance(debug_info, dict):
        for key in aliases.get(field_name, [field_name]):
            value = debug_info.get(key)
            if text_value(value):
                return text_value(value)
    return ""


def section_text(content: dict[str, Any], section_name: str) -> str:
    direct = text_value(content.get(section_name))
    section_lines: list[str] = []
    sections = content.get("sections")
    if isinstance(sections, list):
        for section in sections:
            if not isinstance(section, dict):
                continue
            section_type = str(section.get("sectionType") or "")
            if normalize_section_name(section_type) == normalize_section_name(section_name):
                section_lines.append(text_value(section.get("lines")))
                section_lines.append(blocks_text(section.get("blocks")))
    return "\n".join(item for item in [direct, *section_lines] if item)


def blocks_text(blocks: Any) -> str:
    if not isinstance(blocks, list):
        return ""
    lines: list[str] = []
    for block in blocks:
        if isinstance(block, str):
            lines.append(block)
        elif isinstance(block, dict):
            for key in ["text", "content", "line", "rawText", "cleanedText"]:
                value = block.get(key)
                if text_value(value).strip():
                    lines.append(text_value(value))
                    break
    return "\n".join(lines)


def normalize_section_name(value: str) -> str:
    mapping = {
        "EDUCATION": "education",
        "SKILLS": "skills",
        "WORK_EXPERIENCES": "workExperiences",
        "INTERNSHIPS": "internships",
        "PROJECTS": "projects",
        "CAMPUS_EXPERIENCES": "campusExperiences",
        "SUMMARY": "summary",
        "OTHERS": "others",
        "GENERAL": "others",
    }
    return mapping.get(value, value)


def check_rule(
        actual_text: str,
        rule: dict[str, Any],
        label: str,
        result: CheckResult) -> None:
    optional = bool(rule.get("optional"))
    allow_empty = bool(rule.get("allowEmpty"))
    comparable_actual = normalize_compare_text(actual_text)
    if not actual_text.strip() and (optional or allow_empty):
        result.add_skip()
        return

    expected_value = rule.get("expectedValue")
    if expected_value is not None:
        comparable_expected = normalize_compare_text(str(expected_value))
        if comparable_expected == comparable_actual.strip() or comparable_expected in comparable_actual:
            result.add_pass()
        elif optional:
            result.add_skip()
        else:
            result.add_fail(f"{label}: expected value `{expected_value}`, actual `{actual_text or '<empty>'}`")

    expected_contains = as_list(rule.get("expectedContains"))
    if expected_contains:
        if any(normalize_compare_text(item) in comparable_actual for item in expected_contains):
            result.add_pass()
        elif optional:
            result.add_skip()
        else:
            result.add_fail(f"{label}: expected one of {expected_contains}, actual `{actual_text or '<empty>'}`")

    for forbidden in as_list(rule.get("forbiddenValues")):
        if forbidden and normalize_compare_text(forbidden) in comparable_actual:
            result.add_fail(f"{label}: forbidden value `{forbidden}` appeared")
        else:
            result.add_pass()

    for must in as_list(rule.get("mustContain")):
        if normalize_compare_text(must) in comparable_actual:
            result.add_pass()
        elif optional:
            result.add_skip()
        else:
            result.add_fail(f"{label}: missing `{must}`")

    for forbidden in as_list(rule.get("mustNotContain")):
        if forbidden and normalize_compare_text(forbidden) in comparable_actual:
            result.add_fail(f"{label}: forbidden text `{forbidden}` appeared")
        else:
            result.add_pass()


def as_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item) for item in value]
    return [str(value)]


def count_pure_index_lines(content: dict[str, Any]) -> int:
    text_parts = [section_text(content, field_name) for field_name in SECTION_FIELDS]
    count = 0
    for line in "\n".join(text_parts).splitlines():
        if PURE_INDEX_PATTERN.match(line):
            count += 1
    return count


def count_others(content: dict[str, Any]) -> int:
    others = content.get("others")
    if isinstance(others, list):
        return len([item for item in others if text_value(item).strip()])
    text = section_text(content, "others")
    return len([line for line in text.splitlines() if line.strip()])


def quality_warnings(content: dict[str, Any]) -> list[str]:
    warnings = content.get("qualityWarnings")
    if isinstance(warnings, list):
        return [str(item) for item in warnings]
    if isinstance(warnings, str):
        return [warnings]
    return []


def int_value(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def bool_value(value: Any) -> bool | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.lower() == "true"
    return bool(value)


def evaluate(expected: dict[str, Any], actual: dict[str, Any], mode: str) -> EvaluationRow:
    content = actual_content(actual)
    basic_result = CheckResult()
    section_result = CheckResult()
    details: list[str] = []

    for field_name, rule in expected.get("basicInfo", {}).items():
        if isinstance(rule, dict):
            check_rule(get_field(content, field_name), rule, f"basicInfo.{field_name}", basic_result)

    for section_name, rule in expected.get("sections", {}).items():
        if isinstance(rule, dict):
            check_rule(section_text(content, section_name), rule, f"sections.{section_name}", section_result)
            max_count = int_value(rule.get("maxCount"))
            if max_count is not None and section_name == "others":
                actual_count = count_others(content)
                if actual_count > max_count:
                    section_result.add_fail(f"sections.others: count {actual_count} > maxCount {max_count}")
                else:
                    section_result.add_pass()

    pure_index_count = count_pure_index_lines(content)
    others_count = count_others(content)
    duplicate_count = sum(1 for warning in quality_warnings(content) if "DUPLICATE" in warning)
    total_duration = int_value(content.get("totalParseDurationMs"))
    ai_duration = sum(value or 0 for value in [
        int_value(content.get("aiSectionClassifyDurationMs")),
        int_value(content.get("aiStructuredParseDurationMs")),
    ])
    cache_hit = any(value is True for value in [
        bool_value(content.get("aiSectionClassifyCacheHit")),
        bool_value(content.get("aiStructuredParseCacheHit")),
    ])
    fallback_occurred = any([
        text_value(content.get("aiSectionClassifyFallbackReason")).strip(),
        text_value(content.get("aiStructuredParseFallbackReason")).strip(),
        content.get("aiSectionClassifyEnabled") is True and content.get("aiSectionClassifyApplied") is False,
        content.get("aiStructuredParseEnabled") is True and content.get("aiStructuredParseApplied") is False,
    ])

    quality = expected.get("quality", {})
    if isinstance(quality, dict):
        max_pure = int_value(quality.get("maxPureIndexLines"))
        if max_pure is not None and pure_index_count > max_pure:
            details.append(f"pureIndexLineCount {pure_index_count} > {max_pure}")
        max_others = int_value(quality.get("maxOthersCount"))
        if max_others is not None and others_count > max_others:
            details.append(f"othersCount {others_count} > {max_others}")
        max_duration = int_value(quality.get("maxTotalDurationMs"))
        if max_duration is not None and total_duration is not None and total_duration > max_duration:
            details.append(f"totalParseDurationMs {total_duration} > {max_duration}")
        max_duplicate = int_value(quality.get("maxDuplicateLines"))
        if max_duplicate is not None and duplicate_count > max_duplicate:
            details.append(f"duplicateWarningCount {duplicate_count} > {max_duplicate}")

    details.extend(basic_result.details)
    details.extend(section_result.details)
    status = "PASS" if not details and basic_result.failed == 0 and section_result.failed == 0 else "FAIL"

    return EvaluationRow(
        sample_id=str(expected.get("sampleId") or "UNKNOWN"),
        mode=mode,
        status=status,
        basic_info=basic_result,
        sections=section_result,
        pure_index_line_count=pure_index_count,
        others_count=others_count,
        duplicate_warning_count=duplicate_count,
        total_parse_duration_ms=total_duration,
        ai_duration_ms=ai_duration,
        cache_hit=cache_hit,
        fallback_occurred=bool(fallback_occurred),
        details=details,
    )


def missing_row(expected: dict[str, Any]) -> EvaluationRow:
    return EvaluationRow(
        sample_id=str(expected.get("sampleId") or "UNKNOWN"),
        mode="-",
        status="MISSING_ACTUAL",
        basic_info=CheckResult(),
        sections=CheckResult(),
        pure_index_line_count=None,
        others_count=None,
        duplicate_warning_count=None,
        total_parse_duration_ms=None,
        ai_duration_ms=None,
        cache_hit=None,
        fallback_occurred=None,
        details=["No matching output JSON was found in outputs/."],
    )


def collect_actual_outputs() -> dict[str, list[tuple[Path, dict[str, Any]]]]:
    result: dict[str, list[tuple[Path, dict[str, Any]]]] = {}
    for path in sorted(OUTPUTS_DIR.glob("*.json")):
        number = sample_number(path.name)
        if not number:
            continue
        try:
            actual = load_json(path)
        except json.JSONDecodeError:
            continue
        if not actual_content(actual):
            continue
        result.setdefault(number, []).append((path, actual))
    return result


def evaluate_all() -> list[EvaluationRow]:
    actual_by_sample = collect_actual_outputs()
    rows: list[EvaluationRow] = []
    for expected_path in sorted(EXPECTED_DIR.glob("sample-*-expected.json")):
        expected = load_json(expected_path)
        number = sample_number(expected_path.name) or sample_number(str(expected.get("sampleId")))
        actuals = actual_by_sample.get(number or "", [])
        if not actuals:
            rows.append(missing_row(expected))
            continue
        for actual_path, actual in actuals:
            rows.append(evaluate(expected, actual, actual_mode(actual_path, actual)))
    return rows


def ratio(passed: int, total: int) -> str:
    if total <= 0:
        return "-"
    return f"{passed}/{total} ({passed / total:.1%})"


def render_report(rows: list[EvaluationRow]) -> str:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    evaluated = [row for row in rows if row.status != "MISSING_ACTUAL"]
    passed_rows = sum(1 for row in evaluated if row.status == "PASS")
    total_basic_passed = sum(row.basic_info.passed for row in evaluated)
    total_basic = sum(row.basic_info.passed + row.basic_info.failed for row in evaluated)
    total_section_passed = sum(row.sections.passed for row in evaluated)
    total_section = sum(row.sections.passed + row.sections.failed for row in evaluated)

    lines = [
        "# Parse Regression Report",
        "",
        f"- Generated at: {generated_at}",
        f"- Expected samples: {len(rows)}",
        f"- Evaluated outputs: {len(evaluated)}",
        f"- Passed outputs: {passed_rows}/{len(evaluated) if evaluated else 0}",
        f"- basicInfoAccuracy: {ratio(total_basic_passed, total_basic)}",
        f"- sectionAccuracy: {ratio(total_section_passed, total_section)}",
        "",
        "## Summary",
        "",
        "| sampleId | mode | status | basicInfo | sections | pureIndexLineCount | othersCount | duplicateWarningCount | totalParseDurationMs | aiDurationMs | cacheHit | fallbackOccurred |",
        "| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |",
    ]
    for row in rows:
        lines.append(
            "| "
            + " | ".join([
                row.sample_id,
                row.mode,
                row.status,
                ratio(row.basic_info.passed, row.basic_info.passed + row.basic_info.failed),
                ratio(row.sections.passed, row.sections.passed + row.sections.failed),
                display(row.pure_index_line_count),
                display(row.others_count),
                display(row.duplicate_warning_count),
                display(row.total_parse_duration_ms),
                display(row.ai_duration_ms),
                display(row.cache_hit),
                display(row.fallback_occurred),
            ])
            + " |"
        )

    lines.extend(["", "## Details", ""])
    for row in rows:
        lines.append(f"### {row.sample_id} / {row.mode}")
        if row.details:
            for detail in row.details:
                lines.append(f"- {detail}")
        else:
            lines.append("- No failed checks.")
        lines.append("")
    return "\n".join(lines)


def display(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate resume parse regression outputs.")
    parser.add_argument("--output", type=Path, default=DEFAULT_REPORT, help="Markdown report path.")
    args = parser.parse_args()

    rows = evaluate_all()
    report_path = args.output
    if not report_path.is_absolute():
        report_path = ROOT / report_path
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(render_report(rows) + "\n", encoding="utf-8")
    print(f"Report written: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
