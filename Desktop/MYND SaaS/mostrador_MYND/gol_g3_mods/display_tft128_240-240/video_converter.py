
import cv2
import os

VIDEO = "Logo_boot_sequence_and_welcome_77ca5947.mp4"
OUTPUT = "data/boot"

WIDTH = 240
HEIGHT = 240
FPS_OUTPUT = 12
JPEG_QUALITY = 82

os.makedirs(OUTPUT, exist_ok=True)

video = cv2.VideoCapture(VIDEO)

if not video.isOpened():
    raise RuntimeError("Não foi possível abrir o vídeo.")

fps_original = video.get(cv2.CAP_PROP_FPS)
total_frames = video.get(cv2.CAP_PROP_FRAME_COUNT)

duration = total_frames / fps_original
number_of_frames = int(duration * FPS_OUTPUT)

print("FPS original:", fps_original)
print("Duração:", duration)
print("Quadros de saída:", number_of_frames)

for i in range(number_of_frames):

    source_position = round(i * fps_original / FPS_OUTPUT)

    video.set(cv2.CAP_PROP_POS_FRAMES, source_position)

    success, frame = video.read()

    if not success:
        break

    h, w = frame.shape[:2]

    # Recorte quadrado central
    side = min(w, h)
    x = (w - side) // 2
    y = (h - side) // 2

    frame = frame[y:y+side, x:x+side]

    # Redimensionamento
    frame = cv2.resize(
        frame,
        (WIDTH, HEIGHT),
        interpolation=cv2.INTER_AREA
    )

    filename = os.path.join(
        OUTPUT,
        f"frame{i:03d}.jpg"
    )

    cv2.imwrite(
        filename,
        frame,
        [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY]
    )

    print("Gerado:", filename)

video.release()

print("Conversão finalizada.")
