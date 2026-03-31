#!/bin/bash
# ============================================================
# EchoNav — TFLite Model + Labels Download Script
# ============================================================
# Run this once before building the app.
# It downloads EfficientDet-Lite0 and COCO labels into assets/.
#
# EfficientDet-Lite0:
#   - 4.4MB quantized model
#   - ~50ms on mid-range Android (with NNAPI)
#   - COCO dataset: 80 object classes
#   - Input: 320×320 RGB
# ============================================================

set -e

ASSETS_DIR="app/src/main/assets"
MODEL_FILE="efficientdet_lite0.tflite"
LABELS_FILE="coco_labels.txt"

echo "📦 Creating assets directory..."
mkdir -p "$ASSETS_DIR"

# ── Download EfficientDet-Lite0 (quantized) ──────────────────
MODEL_URL="https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
MODEL_ALT_URL="https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1?lite-format=tflite"

echo "⬇️  Downloading EfficientDet-Lite0..."
if command -v curl &> /dev/null; then
    curl -L "$MODEL_URL" -o "$ASSETS_DIR/$MODEL_FILE" || \
    curl -L "$MODEL_ALT_URL" -o "$ASSETS_DIR/$MODEL_FILE"
elif command -v wget &> /dev/null; then
    wget -q "$MODEL_URL" -O "$ASSETS_DIR/$MODEL_FILE" || \
    wget -q "$MODEL_ALT_URL" -O "$ASSETS_DIR/$MODEL_FILE"
else
    echo "❌ Neither curl nor wget found. Please install one and retry."
    exit 1
fi

# ── Download COCO labels ─────────────────────────────────────
LABELS_URL="https://raw.githubusercontent.com/tensorflow/models/master/research/object_detection/data/mscoco_label_map.txt"

echo "⬇️  Downloading COCO labels..."
# We use a curated flat-text version of the labels
cat > "$ASSETS_DIR/$LABELS_FILE" << 'LABELS'
person
bicycle
car
motorcycle
airplane
bus
train
truck
boat
traffic light
fire hydrant
stop sign
parking meter
bench
bird
cat
dog
horse
sheep
cow
elephant
bear
zebra
giraffe
backpack
umbrella
handbag
tie
suitcase
frisbee
skis
snowboard
sports ball
kite
baseball bat
baseball glove
skateboard
surfboard
tennis racket
bottle
wine glass
cup
fork
knife
spoon
bowl
banana
apple
sandwich
orange
broccoli
carrot
hot dog
pizza
donut
cake
chair
couch
potted plant
bed
dining table
toilet
tv
laptop
mouse
remote
keyboard
cell phone
microwave
oven
toaster
sink
refrigerator
book
clock
vase
scissors
teddy bear
hair drier
toothbrush
LABELS

echo ""
echo "✅ Assets ready:"
ls -lh "$ASSETS_DIR/"
echo ""
echo "🏗️  You can now build the project in Android Studio."
echo "    File → Sync Project with Gradle Files, then Run."
