"""维护端生成固定样本索引。 / Maintenance-only G2P, without on-device dictionaries."""
from pathlib import Path
import json, urllib.request, os, hashlib
import unidic_lite

# 使用原Kokoro 1.0的前端。 / Use the original 1.0 cutlet frontend with local UniDic.
os.environ["UNIDIC_DIR"] = unidic_lite.DICDIR
import unidic
unidic.DICDIR = unidic_lite.DICDIR
from misaki import ja, zh

ROOT = Path(__file__).resolve().parent
REVISION = "1939ad2a8e416c0acfeecc08a694d14ef25f2231"
url = f"https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/{REVISION}/tokenizer.json"
with urllib.request.urlopen(url, timeout=30) as response: tokenizer = json.load(response)
vocab = tokenizer["model"]["vocab"]
converters = {"ja-JP": ja.JAG2P(), "zh-CN": zh.ZHG2P()}
requests = []
for word in json.loads((ROOT / "Resources/words.json").read_text(encoding="utf-8")):
    requests += [(word["word"], "ja-JP"), (word["meaning"], "zh-CN")]
requests += [("日本語を毎日少しずつ勉強します。", "ja-JP"), ("每天学一点，按自己的节奏学习。", "zh-CN")]
phrases = []
for text, language in requests:
    phones = converters[language](text)[0]
    unknown = set(phones) - vocab.keys()
    assert not unknown, (text, phones, unknown)
    tokens = [vocab[phone] for phone in phones]
    assert 0 < len(tokens) <= 510
    phrases.append(dict(text=text, language=language, phonemes=phones, tokens=tokens))
    print(language, text, phones)
destination = ROOT / "Resources/offline-phrases.json"
destination.write_text(json.dumps(phrases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
print("Index bytes:", destination.stat().st_size, "SHA256:", hashlib.sha256(destination.read_bytes()).hexdigest())
