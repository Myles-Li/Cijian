"""Download optional pinned model assets for tests only; never bundle into the app."""
from pathlib import Path
import hashlib, urllib.request, json

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out/TestModels"
OUT.mkdir(parents=True, exist_ok=True)
REVISION = "1939ad2a8e416c0acfeecc08a694d14ef25f2231"
ASSETS = [
    ("model.onnx", "onnx/model_quantized.onnx", 92361116, "fbae9257e1e05ffc727e951ef9b9c98418e6d79f1c9b6b13bd59f5c9028a1478"),
    ("jf_alpha.bin", "voices/jf_alpha.bin", 522240, "56b479360aad9f367aeb8cef908f9201cf48b4555e488c5f4590c9dfcd978bb6"),
    ("zf_xiaobei.bin", "voices/zf_xiaobei.bin", 522240, "5dde6e1c9c4f12c8b327bc29c0cee361a23b52b952c04636858ba637ec66e640"),
]
for name, remote, size, sha in ASSETS:
    target = OUT / name
    if not target.exists() or target.stat().st_size != size or hashlib.sha256(target.read_bytes()).hexdigest() != sha:
        url = f"https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/{REVISION}/{remote}"
        with urllib.request.urlopen(url, timeout=180) as source, target.open("wb") as file:
            while chunk := source.read(1024 * 1024): file.write(chunk)
    assert target.stat().st_size == size and hashlib.sha256(target.read_bytes()).hexdigest() == sha, name
    print(name, size, sha, flush=True)
(ROOT / "out/model-provenance.json").write_text(json.dumps({"revision": REVISION, "total_bytes": sum(x[2] for x in ASSETS),
    "assets": [{"file": x[0], "bytes": x[2], "sha256": x[3]} for x in ASSETS]}, indent=2) + "\n")
