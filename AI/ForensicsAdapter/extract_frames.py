import cv2
import os
import glob

valid_dir = r"c:\Users\SSAFY\Desktop\juhi\S14P21A404\AI\ForensicsAdapter\valid_data"
video_files = glob.glob(os.path.join(valid_dir, "*.mp4"))

frame_interval = 5

for video_path in video_files:
    video_name = os.path.splitext(os.path.basename(video_path))[0]
    cap = cv2.VideoCapture(video_path)
    
    if not cap.isOpened():
        print(f"Error opening video file {video_path}")
        continue
        
    frame_count = 0
    saved_count = 0
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        if frame_count % frame_interval == 0:
            out_name = f"{video_name}_frame_{saved_count:04d}.png"
            out_path = os.path.join(valid_dir, out_name)
            cv2.imwrite(out_path, frame)
            saved_count += 1
            
        frame_count += 1
        
    cap.release()
    print(f"Extracted {saved_count} frames from {video_name}.mp4")

print("Frame extraction completed.")
