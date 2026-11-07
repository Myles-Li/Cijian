"""Run on a standard GitHub macOS runner; never sign or publish an installable app."""
from pathlib import Path
import subprocess, json, platform, zipfile, os

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
OUT.mkdir(exist_ok=True)

def run(command, log=None, timeout=600, check=True):
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if log: (OUT / log).write_text(result.stdout, encoding="utf-8")
    if check and result.returncode:
        print(result.stdout[-16000:], flush=True)
        raise RuntimeError(f"Command failed ({result.returncode}): {command[:3]}")
    return result

report = {"stage": "starting", "device_install": "NOT TESTED: no signing credentials or physical iPad connection",
          "app_review": "NOT SUBMITTED", "user_reported_iPadOS": "27.2", "real_iPad_PiP_and_audio": "NOT TESTED",
          "host": platform.platform(), "xcode": run(["xcodebuild", "-version"]).stdout, "simulators": []}

def save(): (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

save()
run(["python3", "make_project.py"], "project-generation.log")
run(["plutil", "-lint", "Info.plist", "CijianProof.xcodeproj/project.pbxproj"], "project-lint.log")
common = ["xcodebuild", "-project", "CijianProof.xcodeproj", "-scheme", "CijianProof", "-configuration", "Debug"]
run(common + ["-destination", "generic/platform=iOS", "-derivedDataPath", str(OUT / "device"),
              "CODE_SIGNING_ALLOWED=NO", "build"], "unsigned-device-build.log")
app = OUT / "device/Build/Products/Debug-iphoneos/CijianProof.app"
assert app.is_dir()
run(["ditto", "-c", "-k", "--keepParent", str(app), str(OUT / "UNSIGNED-device-build.zip")])
report["unsigned_device_build"] = "PASSED; not directly installable"
save()
devices = json.loads(run(["xcrun", "simctl", "list", "devices", "available", "--json"]).stdout)["devices"]
choices = [(runtime, item) for runtime, items in devices.items() for item in items if item.get("isAvailable")]
assert choices, "No installed iOS simulators"
failed = []
for family in ["iPad", "iPhone"]:
    candidates = [(runtime, item) for runtime, item in choices if family in item["name"]]
    assert candidates, family
    runtime, device = sorted(candidates, key=lambda item: item[0])[-1]
    udid = device["udid"]
    run(["xcrun", "simctl", "boot", udid], check=False)
    run(["xcrun", "simctl", "bootstatus", udid, "-b"], f"{family}-boot.log", timeout=240)
    result_path = OUT / (family + ".xcresult")
    result = run(common + ["-destination", "id=" + udid, "-derivedDataPath", str(OUT / "simulator"),
        "-resultBundlePath", str(result_path), "-parallel-testing-enabled", "NO", "CODE_SIGNING_ALLOWED=NO", "test"],
        family + "-tests.log", check=False, timeout=900)
    summary = run(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result_path)], check=False)
    (OUT / (family + "-summary.json")).write_text(summary.stdout, encoding="utf-8")
    container = run(["xcrun", "simctl", "get_app_container", udid, "com.myles.cijian.iosproof", "data"], check=False)
    if container.returncode == 0:
        state = Path(container.stdout.strip()) / "Documents/proof-status.json"
        if state.exists(): (OUT / (family + "-last-status.json")).write_bytes(state.read_bytes())
    (OUT / (family + "-screenshot-export")).mkdir(exist_ok=True)
    run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(result_path),
         "--output-path", str(OUT / (family + "-screenshot-export"))], check=False)
    report["simulators"].append({"family": family, "device": device["name"], "runtime": runtime,
                                  "exit_code": result.returncode, "summary_available": summary.returncode == 0})
    if result.returncode: failed.append(family)
    save()
    run(["xcrun", "simctl", "shutdown", udid], check=False)
report["stage"] = "FAILED" if failed else "BUILD AND SIMULATOR TESTS COMPLETE; inspect skipped tests and real-device gaps"
save()
if failed: raise SystemExit("Simulator tests failed: " + ", ".join(failed))
print(json.dumps(report, ensure_ascii=False, indent=2))
