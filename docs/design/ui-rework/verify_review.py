"""Offline documentation/artifact verification, not application/security execution tests."""

import argparse
import hashlib
import json
import re
import struct
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BASELINE = "5a71df697fee8fba72eef65364ee87cb28efbc27"
SLICE_BASELINE = "ba39c6a8081b7ecfd180e36ca28591aabbc8bb65"
CONTROLLERS = (
    "Workspace", "Brand", "BrandEvidence", "SocialBrandEvidence", "BrandIdentity",
    "IdentityEvidence", "EvidenceAdmission", "EvidenceAnalysis", "PdfEvidenceReview",
    "UrlEvidenceReview", "SocialEvidenceReview", "AiGeneration", "Strategy",
    "ContentGeneration", "Post", "Calendar", "Media", "CsvExport", "MetricImport",
    "Performance", "Recommendation", "CreatorReport", "Job",
)
PLAN_PATHS = (
    "docs/ui-rework-plan.md", "DEVELOPMENT_PLAN.md", "DEVELOPMENT_PLAN_RU.md",
    "docs/mvp-prompt-progress.md",
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def inventory():
    controllers, routes = [], []
    for name in CONTROLLERS:
        rel = f"src/main/java/com/cadence/presentation/{name}Controller.java"
        raw = (ROOT / rel).read_bytes()
        source = raw.decode("utf-8")
        base = re.search(r'@RequestMapping\("([^"]+)"\)', source).group(1)
        controllers.append({"source": rel, "sha256": sha(raw)})
        pattern = r'@(Get|Post|Patch|Put|Delete)Mapping\b(?:\s*\(([^)]*)\))?'
        for match in re.finditer(pattern, source):
            args = match.group(2) or ""
            literal = re.search(r'"([^"\n]*)"', args)
            suffix = literal.group(1) if literal and literal.group(1).startswith("/") else ""
            routes.append({
                "method": match.group(1).upper(), "path": base.rstrip("/") + suffix,
                "source": rel, "line": source.count("\n", 0, match.start()) + 1,
            })
    return {"baseline": BASELINE, "controllers": controllers, "routes": routes}


def image_dimensions(raw, extension):
    if extension == ".png":
        assert raw[:8] == b"\x89PNG\r\n\x1a\n" and raw[12:16] == b"IHDR"
        return struct.unpack(">II", raw[16:24])
    assert extension == ".jpg" and raw[:2] == b"\xff\xd8"
    pos = 2
    while pos < len(raw):
        assert raw[pos] == 255
        while raw[pos] == 255:
            pos += 1
        marker = raw[pos]
        pos += 1
        if marker in (0x01, 0xD8) or 0xD0 <= marker <= 0xD7:
            continue
        assert marker not in (0xD9, 0xDA), "Missing JPEG frame dimensions"
        length = int.from_bytes(raw[pos:pos + 2], "big")
        assert length >= 2 and pos + length <= len(raw)
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            height, width = struct.unpack(">HH", raw[pos + 3:pos + 7])
            return width, height
        pos += length
    raise AssertionError("Missing JPEG frame")


def image_manifest():
    images = []
    roots = [HERE / rel for rel in (
        "references", "directions", "selected", "prototype/public/assets",
        "craft/screens", "craft/inspect-landing", "craft/inspect-app", "craft/motion",
    )]
    paths = sorted(p for root in roots for p in root.rglob("*")
                   if p.suffix in (".png", ".jpg"))
    for path in paths:
        raw = path.read_bytes()
        width, height = image_dimensions(raw, path.suffix)
        assert width >= 375 and height >= 375, path
        images.append({"path": path.relative_to(HERE).as_posix(), "width": width,
                       "height": height, "bytes": len(raw), "sha256": sha(raw)})
    return {"capture_date": "2026-10-19", "images": images}


def write_json(name, value):
    (HERE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def git_paths(*args):
    raw = subprocess.check_output(["git", *args, "-z"], cwd=ROOT)
    return [v.decode("utf-8") for v in raw.split(b"\0") if v]


def verify():
    expected_inventory = json.loads((HERE / "api-inventory.json").read_text(encoding="utf-8"))
    actual_inventory = inventory()
    assert actual_inventory == expected_inventory, "Controller routes/source drift: review mapping again"
    manifest = json.loads((HERE / "artifact-manifest.json").read_text(encoding="utf-8"))
    assert manifest == image_manifest(), "Image manifest drift"
    refs = [v for v in manifest["images"] if v["path"].startswith("references/")]
    directions = [v for v in manifest["images"] if v["path"].startswith("directions/")]
    assert len(refs) == 12 and len(directions) == 3, "Expected 12 captures and exactly 3 directions"
    prompts = json.loads((HERE / "generation-prompts.json").read_text(encoding="utf-8"))
    assert len(prompts["directions"]) == 3
    for item in prompts["directions"]:
        assert len(item["referenced_image_paths"]) == 3
        for ref in item["reference_artifacts"]:
            path = (HERE / ref).resolve()
            assert path.is_relative_to(HERE / "references") and path.is_file()
        assert "1440" in item["prompt"] and "DEMO" in item["prompt"]
    markdown = sorted(HERE.glob("*.md")) + [ROOT / p for p in PLAN_PATHS]
    local_links = 0
    for path in markdown:
        content = path.read_text(encoding="utf-8", errors="strict")
        assert content.count("```") % 2 == 0, f"Unbalanced code fence: {path}"
        assert not re.search(r"sk_(?:live|test)_[A-Za-z0-9]{16,}|-----BEGIN .*PRIVATE KEY", content)
        for link in re.findall(r'!?\[[^\]\n]*\]\(([^)\n]+)\)', content):
            value = link.strip().strip("<>")
            if urlsplit(value).scheme or value.startswith("#"):
                continue
            target = unquote(value.split("#", 1)[0])
            assert (path.parent / target).resolve().exists(), f"Missing link {value} in {path}"
            local_links += 1
    # The unrelated pinned GSAP commit is already in actual HEAD. This slice must
    # not count it as its own app change; API inventory still checks original sources.
    paths = set(git_paths("diff", "--name-only", SLICE_BASELINE))
    untracked = set(git_paths("ls-files", "--others", "--exclude-standard"))
    # These appeared concurrently after the initial clean status. They are unrelated,
    # remain untracked, and must never be staged by this review task.
    preserved_external = {p for p in untracked if p.startswith(".agents/skills/")}
    paths.update(untracked - preserved_external)
    assert paths, "Expected review changes"
    for rel in paths:
        assert rel.startswith("docs/design/ui-rework/") or rel in PLAN_PATHS, f"Unexpected product change: {rel}"
        assert not any(part.startswith(".env") for part in Path(rel).parts), rel
    assert "backend-backlog.md" in (ROOT / "docs/ui-rework-plan.md").read_text(encoding="utf-8")
    summary = {
        "result": "PASS", "markdown_files": len(markdown), "local_links": local_links,
        "image_files": len(manifest["images"]), "reference_captures": len(refs),
        "png_files": sum(v["path"].endswith(".png") for v in manifest["images"]),
        "jpeg_files": sum(v["path"].endswith(".jpg") for v in manifest["images"]),
        "directions": len(directions), "controllers": len(CONTROLLERS),
        "route_annotations": len(actual_inventory["routes"]), "changed_files": len(paths),
        "product_changes_in_this_slice": 0, "working_application_tests_executed": 0,
        "preserved_external_untracked_files": len(preserved_external),
    }
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh-manifests", action="store_true")
    options = parser.parse_args()
    if options.refresh_manifests:
        write_json("api-inventory.json", inventory())
        write_json("artifact-manifest.json", image_manifest())
        print("Refreshed API/image snapshots; run without --refresh-manifests to verify.")
    else:
        verify()
