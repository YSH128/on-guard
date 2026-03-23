import os
import json
import glob

valid_dir = r"c:\Users\SSAFY\Desktop\juhi\S14P21A404\AI\ForensicsAdapter\valid_data"
out_dir = r"c:\Users\SSAFY\Desktop\juhi\S14P21A404\AI\ForensicsAdapter\dataset_json"

os.makedirs(out_dir, exist_ok=True)

valid_data = {
    "valid_data": {
        "DFDC_Fake": { "test": {} },
        "DFDC_Real": { "test": {} }
    }
}

fake_files = glob.glob(os.path.join(valid_dir, "fake_*"))
real_files = glob.glob(os.path.join(valid_dir, "real_*"))

valid_exts = {".jpg", ".jpeg", ".png", ".bmp"}
fake_files = [f for f in fake_files if os.path.splitext(f)[1].lower() in valid_exts]
real_files = [f for f in real_files if os.path.splitext(f)[1].lower() in valid_exts]

for i, f in enumerate(fake_files):
    name = f"fake_video_{i}"
    valid_data["valid_data"]["DFDC_Fake"]["test"][name] = {
        "label": "DFDC_Fake", 
        "frames": [f.replace("\\", "/")]
    }

for i, f in enumerate(real_files):
    name = f"real_video_{i}"
    valid_data["valid_data"]["DFDC_Real"]["test"][name] = {
        "label": "DFDC_Real",
        "frames": [f.replace("\\", "/")]
    }

out_path = os.path.join(out_dir, "valid_data.json")
with open(out_path, "w") as f:
    json.dump(valid_data, f, indent=4)

print(f"Generated {out_path} with {len(fake_files)} fake and {len(real_files)} real images.")
